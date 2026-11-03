package app.discipline.ui.screens

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.SelfImprovement
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.discipline.health.HealthAvailability
import app.discipline.health.HealthCategory
import app.discipline.health.HcType
import app.discipline.health.HealthStatus
import app.discipline.health.HealthToggles
import app.discipline.health.HealthSyncWorker
import app.discipline.ui.LocalNavigator
import app.discipline.ui.LocalSnackbar
import app.discipline.ui.Route
import app.discipline.ui.components.Group
import app.discipline.ui.components.GroupDivider
import app.discipline.ui.components.IconBadge
import app.discipline.ui.components.OneUiScreen
import app.discipline.ui.components.ListRow
import app.discipline.ui.components.OneUiSwitch
import app.discipline.ui.components.PillButton
import app.discipline.ui.components.SectionLabel
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
        } catch (e: Exception) {
            scope.launch { snackbar.showSnackbar("Couldn't open Health Connect") }
        }
    }

    fun open(intent: android.content.Intent) {
        try { context.startActivity(intent) } catch (e: Exception) {
            scope.launch { snackbar.showSnackbar("Couldn't open Health Connect") }
        }
    }

    fun openSettings() {
        if (!c.health.openSettings(context)) scope.launch { snackbar.showSnackbar("Couldn't open Health Connect settings") }
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
                    PillButton("Open Health Connect", onClick = ::openSettings, modifier = Modifier.fillMaxWidth())
                }
                !status.connected -> PillButton("Connect", onClick = { request(wanted) }, modifier = Modifier.fillMaxWidth())
                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    PillButton("Sync now", modifier = Modifier.weight(1f), onClick = {
                        HealthSyncWorker.enqueueFull(context, readDays = 30)
                        scope.launch { snackbar.showSnackbar("Syncing with Health Connect") }
                    })
                    TonalPillButton("Permissions", modifier = Modifier.weight(1f), onClick = ::openSettings)
                }
            }
            if (status.connected && missing.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                TonalPillButton("Allow ${missing.size} more permission${if (missing.size == 1) "" else "s"}", onClick = { request(wanted) }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (!showToggles || status.availability != HealthAvailability.AVAILABLE) return

    val nav = LocalNavigator.current
    if (status.connected) {
        Group {
            ListRow(
                "Health data",
                summary = "Everything Discipline reads from Health Connect",
                leading = { IconBadge(Icons.Rounded.MonitorHeart, colors.danger) },
                trailing = { Chevron() },
                onClick = { nav.push(Route.HealthData) },
            )
            GroupDivider(76.dp)
            ListRow(
                "Log health data",
                summary = "Blood pressure, sleep, cycle, vitals and more",
                leading = { IconBadge(Icons.Rounded.Add, colors.accent) },
                trailing = { Chevron() },
                onClick = { nav.push(Route.LogHealth(null)) },
            )
        }
    }

    fun update(t: HealthToggles, newPermissions: Set<String>) {
        scope.launch {
            c.settings.setHealthToggles(t)
            if (newPermissions.isNotEmpty() && status.connected) request(newPermissions)
        }
    }

    Group(label = "Data types") {
        HealthCategory.entries.forEachIndexed { i, cat ->
            if (i > 0) GroupDivider(24.dp)
            CategoryRow(cat, s.health, status, onRead = { on ->
                update(s.health.withRead(cat, on), if (on) typesIn(cat, status).mapTo(mutableSetOf()) { it.readPermission } else emptySet())
            }, onWrite = { on ->
                update(s.health.withWrite(cat, on), if (on) typesIn(cat, status).mapTo(mutableSetOf()) { it.writePermission } else emptySet())
            })
        }
    }
    Group(label = "Counting other apps' data") {
        SwitchRow(
            "Food from other apps", s.health.countOtherFood, { update(s.health.copy(countOtherFood = it), emptySet()) },
            summary = "Count meals logged in other apps towards your calories. Leave off if those apps already read Discipline's meals.",
            leading = { IconBadge(Icons.Rounded.Restaurant, colors.calories) },
        )
        GroupDivider(76.dp)
        SwitchRow(
            "Water from other apps", s.health.countOtherWater, { update(s.health.copy(countOtherWater = it), emptySet()) },
            summary = "Add drinks logged elsewhere to your daily water",
            leading = { IconBadge(Icons.Rounded.WaterDrop, colors.water) },
        )
    }
    Text(
        "Discipline can read and write every Health Connect data type. Use Permissions to choose individual types in Health Connect. " +
            "Each food you log becomes its own nutrition record, tagged with the meal, and edits and deletions are mirrored automatically. " +
            "Data Discipline reads stays on this device and refreshes when you open the app and about once an hour.",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textTertiary,
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
    )
}

private fun typesIn(cat: HealthCategory, status: HealthStatus) = HcType.entries.filter { it.category == cat && status.supported(it) }

@Composable
private fun CategoryRow(
    cat: HealthCategory,
    toggles: HealthToggles,
    status: HealthStatus,
    onRead: (Boolean) -> Unit,
    onWrite: (Boolean) -> Unit,
) {
    val colors = OneUi.colors
    var expanded by rememberSaveable(cat) { mutableStateOf(false) }
    val types = typesIn(cat, status)
    val readOk = types.count(status::canRead)
    val writeOk = types.count(status::canWrite)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 24.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(categoryIcon(cat), categoryColor(cat))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(cat.label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
                Text(
                    "Read $readOk of ${types.size} · Write $writeOk of ${types.size}",
                    style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                )
            }
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, if (expanded) "Hide types" else "Show types", tint = colors.textSecondary)
        }
        Row(Modifier.fillMaxWidth().padding(start = 76.dp, end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LabeledSwitch("Read", cat in toggles.read, onRead)
            LabeledSwitch("Write", cat in toggles.write, onWrite)
        }
        if (expanded) {
            types.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(start = 76.dp, end = 24.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
                    Text(
                        listOf(if (status.canRead(t)) "Read" else null, if (status.canWrite(t)) "Write" else null).filterNotNull().joinToString(" · ").ifEmpty { "Not allowed" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status.canRead(t) || status.canWrite(t)) colors.success else colors.textTertiary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(OneUi.PillShape).clickable { onChange(!checked) }.padding(end = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = OneUi.colors.textSecondary)
        Spacer(Modifier.width(8.dp))
        OneUiSwitch(checked, onChange)
    }
}

fun categoryIcon(cat: HealthCategory): ImageVector = when (cat) {
    HealthCategory.ACTIVITY -> Icons.AutoMirrored.Rounded.DirectionsWalk
    HealthCategory.BODY -> Icons.Rounded.MonitorWeight
    HealthCategory.CYCLE -> Icons.Rounded.WaterDrop
    HealthCategory.NUTRITION -> Icons.Rounded.Restaurant
    HealthCategory.SLEEP -> Icons.Rounded.Bedtime
    HealthCategory.VITALS -> Icons.Rounded.Favorite
    HealthCategory.WELLNESS -> Icons.Rounded.SelfImprovement
}

@Composable
fun categoryColor(cat: HealthCategory): Color {
    val c = OneUi.colors
    return when (cat) {
        HealthCategory.ACTIVITY -> c.steps
        HealthCategory.BODY -> c.weight
        HealthCategory.CYCLE -> Color(0xFFEC4899)
        HealthCategory.NUTRITION -> c.calories
        HealthCategory.SLEEP -> Color(0xFF6366F1)
        HealthCategory.VITALS -> c.danger
        HealthCategory.WELLNESS -> Color(0xFF14B8A6)
    }
}
