package app.discipline.ui.screens

import android.content.ActivityNotFoundException
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.discipline.data.HealthToggles
import app.discipline.health.HealthAvailability
import app.discipline.health.HealthConnectManager.Companion.read
import app.discipline.health.HealthConnectManager.Companion.write
import app.discipline.health.HealthSyncWorker
import app.discipline.ui.LocalNavigator
import app.discipline.ui.LocalSnackbar
import app.discipline.ui.components.Group
import app.discipline.ui.components.GroupDivider
import app.discipline.ui.components.IconBadge
import app.discipline.ui.components.OneUiScreen
import app.discipline.ui.components.PillButton
import app.discipline.ui.components.SwitchRow
import app.discipline.ui.components.TonalPillButton
import app.discipline.ui.theme.OneUi
import kotlinx.coroutines.launch

@Composable
fun HealthConnectScreen() {
    val nav = LocalNavigator.current
    OneUiScreen(title = "Health Connect", subtitle = "Sync with your other health apps", onBack = { nav.pop() }, headerFraction = 0.28f) {
        item { HealthConnectPanel(showToggles = true) }
    }
}

/** Status, connect button and per-type toggles. Also embedded in onboarding (without toggles). */
@Composable
fun HealthConnectPanel(showToggles: Boolean, onConnected: () -> Unit = {}) {
    val c = appContainer()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val colors = OneUi.colors
    val s = rememberSettings() ?: return
    val status by c.health.status.collectAsStateWithLifecycle()
    var requested by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) { c.health.refreshStatus() }

    val launcher = rememberLauncherForActivityResult(c.health.permissionContract()) { granted ->
        scope.launch {
            c.health.onPermissionResult(requested, granted)
            if (granted.isNotEmpty()) {
                HealthSyncWorker.enqueueFull(context, readDays = 30)
                onConnected()
            }
        }
    }

    fun request(perms: Set<String>) {
        val missing = perms - status.granted
        if (missing.isEmpty()) return
        requested = missing
        try {
            launcher.launch(missing)
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Couldn't open Health Connect") }
        }
    }

    fun open(intent: android.content.Intent) {
        try { context.startActivity(intent) } catch (e: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("Couldn't open Health Connect") }
        }
    }

    val wanted = c.health.permissionsFor(s.health)
    val missing = wanted - status.granted

    Group(contentPadding = PaddingValues(24.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(colors.danger.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Favorite, null, tint = colors.danger, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(14.dp))
            val (headline, body) = when {
                status.availability == HealthAvailability.UNAVAILABLE ->
                    "Not available" to "Health Connect isn't supported on this device."
                status.availability == HealthAvailability.UPDATE_REQUIRED ->
                    "Update needed" to "Install or update Health Connect from the Play Store to continue."
                status.connected -> "Connected" to (
                    if (s.lastHealthSync > 0) "Last synced " + DateUtils.getRelativeTimeSpanString(s.lastHealthSync, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
                    else "Waiting for first sync"
                )
                else -> "Not connected" to "Share meals, water, weight and workouts, and bring in steps and activity from your watch or phone."
            }
            Text(headline, style = MaterialTheme.typography.headlineSmall, color = if (status.connected) colors.success else colors.textPrimary)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            when {
                status.availability == HealthAvailability.UPDATE_REQUIRED ->
                    PillButton("Get Health Connect", onClick = { open(c.health.installIntent()) }, modifier = Modifier.fillMaxWidth())
                status.availability == HealthAvailability.UNAVAILABLE -> Unit
                !status.connected && s.healthDenials >= 2 -> {
                    Text(
                        "Android won't ask again after two refusals. Allow Discipline in Health Connect's settings.",
                        style = MaterialTheme.typography.bodySmall, color = colors.exercise, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    PillButton("Open Health Connect", onClick = { open(c.health.settingsIntent()) }, modifier = Modifier.fillMaxWidth())
                }
                !status.connected -> PillButton("Connect", onClick = { request(wanted) }, modifier = Modifier.fillMaxWidth())
                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PillButton("Sync now", modifier = Modifier.weight(1f), onClick = {
                        HealthSyncWorker.enqueueFull(context, readDays = 30)
                        scope.launch { snackbar.showSnackbar("Syncing with Health Connect") }
                    })
                    TonalPillButton("Permissions", modifier = Modifier.weight(1f), onClick = { open(c.health.settingsIntent()) })
                }
            }
            if (status.connected && missing.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                TonalPillButton("Allow ${missing.size} more permission${if (missing.size == 1) "" else "s"}", onClick = { request(wanted) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (!showToggles || status.availability != HealthAvailability.AVAILABLE) return

    fun update(t: HealthToggles, perms: Set<String>, on: Boolean) {
        scope.launch {
            c.settings.setHealthToggles(t)
            if (on && status.connected) request(perms)
        }
    }

    val g = status.granted
    fun allowed(p: String) = if (p in g) "Allowed" else "Not allowed in Health Connect"

    Group(label = "Share with Health Connect") {
        SwitchRow("Nutrition", s.health.writeNutrition, { update(s.health.copy(writeNutrition = it), setOf(write(NutritionRecord::class)), it) },
            summary = allowed(write(NutritionRecord::class)), leading = { IconBadge(Icons.Rounded.Restaurant, colors.calories) })
        GroupDivider(76.dp)
        SwitchRow("Water", s.health.writeHydration, { update(s.health.copy(writeHydration = it), setOf(write(HydrationRecord::class)), it) },
            summary = allowed(write(HydrationRecord::class)), leading = { IconBadge(Icons.Rounded.WaterDrop, colors.water) })
        GroupDivider(76.dp)
        SwitchRow("Weight", s.health.writeWeight, { update(s.health.copy(writeWeight = it), setOf(write(WeightRecord::class)), it) },
            summary = allowed(write(WeightRecord::class)), leading = { IconBadge(Icons.Rounded.MonitorWeight, colors.weight) })
        GroupDivider(76.dp)
        SwitchRow("Exercise", s.health.writeExercise, {
            update(s.health.copy(writeExercise = it), setOf(write(ExerciseSessionRecord::class), write(ActiveCaloriesBurnedRecord::class)), it)
        }, summary = allowed(write(ExerciseSessionRecord::class)), leading = { IconBadge(Icons.Rounded.FitnessCenter, colors.exercise) })
    }
    Group(label = "Read from Health Connect") {
        SwitchRow("Steps", s.health.readSteps, { update(s.health.copy(readSteps = it), setOf(read(StepsRecord::class)), it) },
            summary = allowed(read(StepsRecord::class)), leading = { IconBadge(Icons.AutoMirrored.Rounded.DirectionsWalk, colors.steps) })
        GroupDivider(76.dp)
        SwitchRow("Active calories", s.health.readActiveCalories, { update(s.health.copy(readActiveCalories = it), setOf(read(ActiveCaloriesBurnedRecord::class)), it) },
            summary = allowed(read(ActiveCaloriesBurnedRecord::class)), leading = { IconBadge(Icons.Rounded.LocalFireDepartment, colors.exercise) })
        GroupDivider(76.dp)
        SwitchRow("Workouts from other apps", s.health.readExercise, { update(s.health.copy(readExercise = it), setOf(read(ExerciseSessionRecord::class)), it) },
            summary = allowed(read(ExerciseSessionRecord::class)), leading = { IconBadge(Icons.Rounded.FitnessCenter, Color(0xFF6366F1)) })
        GroupDivider(76.dp)
        SwitchRow("Weight from smart scales", s.health.readWeight, { update(s.health.copy(readWeight = it), setOf(read(WeightRecord::class)), it) },
            summary = allowed(read(WeightRecord::class)), leading = { IconBadge(Icons.Rounded.MonitorWeight, colors.weight) })
    }
    Text(
        "Discipline writes each food you log as its own nutrition record, tagged with the meal, so other apps see exactly what you ate. " +
            "Edits and deletions are mirrored automatically. Data you read is only stored on this device.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textTertiary,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
    )
}
