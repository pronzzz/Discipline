package app.discipline.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.discipline.BuildConfig
import app.discipline.data.Settings
import app.discipline.ui.components.AccentTextButton
import app.discipline.ui.components.Group
import app.discipline.ui.components.GroupDivider
import app.discipline.ui.components.IconBadge
import app.discipline.ui.components.ListRow
import app.discipline.ui.components.SwitchRow
import app.discipline.ui.theme.OneUi
import app.discipline.update.Release
import app.discipline.update.UpdateState
import kotlinx.coroutines.launch

/** App-wide dialog for an available update, its download and any install problem. */
@Composable
fun UpdatePrompt() {
    val c = appContainer()
    val context = LocalContext.current
    val colors = OneUi.colors
    val state by c.updater.state.collectAsStateWithLifecycle()

    fun openPage(r: Release) = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    val s = state
    val release = when (s) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.NeedsPermission -> s.release
        is UpdateState.Installing -> s.release
        is UpdateState.Failed -> s.release
        else -> null
    } ?: return

    AlertDialog(
        onDismissRequest = { if (s !is UpdateState.Downloading && s !is UpdateState.Installing) c.updater.dismiss() },
        shape = OneUi.ContainerShape,
        containerColor = colors.container,
        icon = { IconBadge(Icons.Rounded.SystemUpdate, colors.accent) },
        title = { Text("Discipline ${release.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (s) {
                    is UpdateState.Downloading -> {
                        Text("Downloading… ${(s.progress * 100).toInt()}%", color = colors.textSecondary)
                        LinearProgressIndicator(progress = { s.progress }, color = colors.accent, trackColor = colors.containerHigh, modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateState.Installing -> Text("Confirm the update when Android asks. Your diary is kept.", color = colors.textSecondary)
                    is UpdateState.NeedsPermission -> Text(
                        "To install updates, allow Discipline to install apps. Turn on “Allow from this source”, then come back and tap Update.",
                        color = colors.textSecondary,
                    )
                    is UpdateState.Failed -> Text(s.message, color = colors.danger)
                    else -> {
                        Text("You have ${BuildConfig.VERSION_NAME}. Your diary and settings are kept.", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                        if (release.notes.isNotBlank()) {
                            Text(
                                release.notes,
                                style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary,
                                modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (s) {
                is UpdateState.Downloading, is UpdateState.Installing -> Unit
                is UpdateState.NeedsPermission -> Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    AccentTextButton(
                        if (c.updater.canInstall()) "Update" else "Allow installs",
                        onClick = {
                            if (c.updater.canInstall()) c.scope.launch { c.updater.install(release) }
                            else runCatching { context.startActivity(c.updater.permissionIntent()) }
                        },
                    )
                }
                is UpdateState.Failed -> AccentTextButton("Open GitHub", onClick = { openPage(release); c.updater.dismiss() })
                else -> AccentTextButton("Update", onClick = { c.scope.launch { c.updater.install(release) } })
            }
        },
        dismissButton = {
            when (s) {
                is UpdateState.Downloading, is UpdateState.Installing -> Unit
                is UpdateState.Available -> Column {
                    AccentTextButton("Later", onClick = { c.updater.dismiss() }, color = colors.textSecondary)
                    AccentTextButton("Skip this version", onClick = { c.scope.launch { c.updater.skip(release) } }, color = colors.textSecondary)
                }
                else -> AccentTextButton("Close", onClick = { c.updater.dismiss() }, color = colors.textSecondary)
            }
        },
    )
}

/** "App updates" section of Settings. */
@Composable
fun UpdateSettingsGroup(s: Settings) {
    val c = appContainer()
    val colors = OneUi.colors
    val state by c.updater.state.collectAsStateWithLifecycle()
    Group(label = "App updates") {
        SwitchRow(
            "Check for updates", s.autoCheckUpdates, { on -> c.scope.launch { c.settings.setAutoCheckUpdates(on) } },
            summary = "Look for new versions on GitHub when you open the app",
            leading = { IconBadge(Icons.Rounded.SystemUpdate, colors.accent) },
        )
        GroupDivider(76.dp)
        ListRow(
            "Check now",
            summary = when (val st = state) {
                UpdateState.Checking -> "Checking…"
                UpdateState.UpToDate -> "Discipline ${BuildConfig.VERSION_NAME} is the latest version"
                is UpdateState.Available -> "Version ${st.release.version} is available"
                is UpdateState.Failed -> if (st.release == null) st.message else "Update failed"
                else -> "You have version ${BuildConfig.VERSION_NAME}"
            },
            leading = { IconBadge(Icons.Rounded.SystemUpdate, Color(0xFF64748B)) },
            enabled = state !is UpdateState.Checking,
            onClick = { c.scope.launch { c.updater.check(manual = true) } },
        )
    }
}
