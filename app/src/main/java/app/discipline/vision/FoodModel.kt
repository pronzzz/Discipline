package app.discipline.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** One thing the model thinks is in the photo. */
data class FoodGuess(val label: String, val confidence: Float)

/**
 * On-device food recognition with Google's AIY "food V1" classifier (2,023 dishes, Apache 2.0),
 * run through ML Kit custom image labeling. The 21 MB model isn't bundled in the APK; it's
 * downloaded once from this project's GitHub release and checked against a pinned SHA-256.
 * Photos never leave the phone.
 */
class FoodModel(private val context: Context) {

    sealed interface State {
        data object Missing : State
        data class Downloading(val progress: Float) : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val file get() = File(context.filesDir, "models/$FILE_NAME")
    private val _state = MutableStateFlow<State>(if (file.length() == SIZE) State.Ready else State.Missing)
    val state: StateFlow<State> = _state.asStateFlow()

    private val lock = Mutex()
    private var labeler: ImageLabeler? = null

    suspend fun download() = lock.withLock {
        if (_state.value == State.Ready) return@withLock
        _state.value = State.Downloading(0f)
        _state.value = try {
            withContext(Dispatchers.IO) { fetch() }
            State.Ready
        } catch (e: Exception) {
            State.Failed(e.message ?: "Download failed")
        }
    }

    private fun fetch() {
        val dir = file.parentFile!!.apply { mkdirs() }
        val tmp = File(dir, "$FILE_NAME.part")
        val conn = (URL(URL_MODEL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            val digest = MessageDigest.getInstance("SHA-256")
            var read = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        read += n
                        _state.value = State.Downloading((read.toFloat() / SIZE).coerceIn(0f, 1f))
                    }
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (hex != SHA256) { tmp.delete(); throw IllegalStateException("Model checksum didn't match") }
            if (!tmp.renameTo(file)) throw IllegalStateException("Couldn't save the model")
        } finally {
            conn.disconnect()
            tmp.delete()
        }
    }

    fun delete() {
        labeler?.close(); labeler = null
        file.delete()
        _state.value = State.Missing
    }

    /** Best guesses for [bitmap], most likely first. */
    suspend fun classify(bitmap: Bitmap, max: Int = 8): List<FoodGuess> {
        if (_state.value != State.Ready) return emptyList()
        val l = lock.withLock {
            labeler ?: ImageLabeling.getClient(
                CustomImageLabelerOptions.Builder(LocalModel.Builder().setAbsoluteFilePath(file.absolutePath).build())
                    .setConfidenceThreshold(0.01f)
                    .setMaxResultCount(max + 4)
                    .build(),
            ).also { labeler = it }
        }
        val labels = l.process(InputImage.fromBitmap(bitmap, 0)).await()
        return labels
            .filter { it.text.isNotBlank() && !it.text.startsWith("__") && !it.text.startsWith("/") }
            .map { FoodGuess(it.text.trim(), it.confidence) }
            .distinctBy { it.label.lowercase() }
            .take(max)
    }

    companion object {
        const val FILE_NAME = "food_v1.tflite"
        const val SIZE = 21_151_551L
        const val SHA256 = "03dd6d9129501f97be00775d9e17b5d9ad13be730149352adb8f4ca953b7a650"
        const val URL_MODEL = "https://github.com/pronzzz/Discipline/releases/download/v1.1.0/$FILE_NAME"
    }
}
