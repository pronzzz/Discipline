package app.discipline.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import app.discipline.BuildConfig
import app.discipline.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A published GitHub release with an installable APK. */
data class Release(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val sha256Url: String?,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    /** Android needs the user to allow Discipline to install apps first. */
    data class NeedsPermission(val release: Release) : UpdateState
    data class Installing(val release: Release) : UpdateState
    data class Failed(val message: String, val release: Release? = null) : UpdateState
}

/**
 * Checks this project's GitHub releases for a newer version and installs it. Downloads are
 * verified against the release's `.apk.sha256` asset, and Android itself refuses an update
 * that isn't signed with the same key as the installed app.
 */
class Updater(private val context: Context, private val settings: SettingsRepository) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val lock = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    /** Background check, at most every [AUTO_INTERVAL_MS], respecting the user's settings. */
    suspend fun autoCheck() {
        val s = settings.current()
        if (!s.autoCheckUpdates) return
        if (System.currentTimeMillis() - s.lastUpdateCheck < AUTO_INTERVAL_MS) return
        check(manual = false)
    }

    suspend fun check(manual: Boolean = true) = lock.withLock {
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return@withLock
        _state.value = UpdateState.Checking
        _state.value = try {
            val r = withContext(Dispatchers.IO) { latest() }
            settings.setLastUpdateCheck(System.currentTimeMillis())
            val skipped = settings.current().skippedUpdate
            when {
                r == null || !isNewer(r.version, BuildConfig.VERSION_NAME) -> UpdateState.UpToDate
                !manual && r.version == skipped -> UpdateState.Idle
                else -> UpdateState.Available(r)
            }
        } catch (e: Exception) {
            if (manual) UpdateState.Failed(e.message ?: "Couldn't reach GitHub") else UpdateState.Idle
        }
    }

    fun dismiss() { if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle }

    suspend fun skip(release: Release) {
        settings.setSkippedUpdate(release.version)
        _state.value = UpdateState.Idle
    }

    fun canInstall() = context.packageManager.canRequestPackageInstalls()

    fun permissionIntent() = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    suspend fun install(release: Release) = lock.withLock {
        if (!canInstall()) { _state.value = UpdateState.NeedsPermission(release); return@withLock }
        _state.value = UpdateState.Downloading(release, 0f)
        try {
            val apk = withContext(Dispatchers.IO) { download(release) }
            _state.value = UpdateState.Installing(release)
            withContext(Dispatchers.IO) { commit(apk) }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: "Update failed", release)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        val r = (_state.value as? UpdateState.Installing)?.release
        _state.value = when (status) {
            PackageInstaller.STATUS_SUCCESS -> UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> r?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                UpdateState.Failed("This update is signed differently from the installed app. Download it from GitHub instead.", r)
            else -> UpdateState.Failed(message ?: "Install failed", r)
        }
    }

    // ------------------------------------------------------------------------------ network

    private fun latest(): Release? {
        val root = json.parseToJsonElement(get(LATEST_URL)).jsonObject
        if ((root["draft"] as? JsonPrimitive)?.content == "true") return null
        val tag = (root["tag_name"] as? JsonPrimitive)?.content ?: return null
        val assets = root["assets"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        fun name(a: JsonObject) = (a["name"] as? JsonPrimitive)?.content.orEmpty()
        fun url(a: JsonObject) = (a["browser_download_url"] as? JsonPrimitive)?.content
        val apk = assets.firstOrNull { name(it).endsWith(".apk") } ?: return null
        val sha = assets.firstOrNull { name(it) == name(apk) + ".sha256" }
        return Release(
            version = tag.removePrefix("v"),
            notes = cleanNotes((root["body"] as? JsonPrimitive)?.content.orEmpty()),
            apkUrl = url(apk) ?: return null,
            apkSize = (apk["size"] as? JsonPrimitive)?.longOrNull ?: 0,
            sha256Url = sha?.let(::url),
            pageUrl = (root["html_url"] as? JsonPrimitive)?.content ?: "https://github.com/$REPO/releases",
        )
    }

    private fun download(r: Release): File {
        val expected = r.sha256Url?.let { get(it).trim().split(Regex("\\s+")).first().lowercase() }
            ?: throw IllegalStateException("The release has no checksum, so it can't be verified")
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "Discipline-${r.version}.apk")
        val conn = open(r.apkUrl)
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("Download failed (HTTP ${conn.responseCode})")
            val total = r.apkSize.takeIf { it > 0 } ?: conn.contentLengthLong
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n); digest.update(buf, 0, n); read += n
                        if (total > 0) _state.value = UpdateState.Downloading(r, (read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != expected) { file.delete(); throw IllegalStateException("Downloaded file didn't match its checksum") }
            return file
        } finally {
            conn.disconnect()
        }
    }

    private fun commit(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, UpdateReceiver::class.java).setAction(ACTION_RESULT)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Discipline/${BuildConfig.VERSION_NAME} (Android)")
        setRequestProperty("Accept", "application/vnd.github+json")
    }

    private fun get(url: String): String {
        val conn = open(url)
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("GitHub returned HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val REPO = "pronzzz/Discipline"
        const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
        const val AUTO_INTERVAL_MS = 12 * 60 * 60 * 1000L
        const val ACTION_RESULT = "app.discipline.UPDATE_RESULT"

        /** True when [candidate] is a later version than [current] ("1.10.0" > "1.9.2"). */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            // Same numbers: a release beats a pre-release of it.
            return current.contains('-') && !candidate.contains('-')
        }

        /** Release notes as plain text: Markdown markers removed, at most ~1,500 characters. */
        fun cleanNotes(md: String): String = md.lines()
            .map { it.trimEnd() }
            .filterNot { it.startsWith("<!--") || it.startsWith("**Full Changelog**") }
            .joinToString("\n") { line ->
                line.replace(Regex("^#{1,6}\\s*"), "")
                    .replace(Regex("^\\s*[-*]\\s+"), "• ")
                    .replace("**", "")
                    .replace(Regex("`([^`]*)`"), "$1")
                    .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
            }
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
            .let { if (it.length > 1500) it.take(1500).substringBeforeLast('\n') + "\n…" else it }
    }
}

/** Receives the PackageInstaller result and asks the user to confirm when Android requires it. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_INTENT)
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
            return
        }
        val app = context.applicationContext as? app.discipline.DisciplineApp ?: return
        app.container.updater.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
