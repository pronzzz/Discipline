package app.discipline.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.discipline.data.db.HealthLogEntity
import app.discipline.health.EntryKind
import app.discipline.health.HcType
import app.discipline.health.HealthCategory
import app.discipline.health.HealthRecords
import app.discipline.ui.LocalNavigator
import app.discipline.ui.LocalSnackbar
import app.discipline.ui.Route
import app.discipline.ui.Tab
import app.discipline.ui.components.AccentTextButton
import app.discipline.ui.components.BottomActionBar
import app.discipline.ui.components.EmptyHint
import app.discipline.ui.components.Group
import app.discipline.ui.components.GroupDivider
import app.discipline.ui.components.IconBadge
import app.discipline.ui.components.ListRow
import app.discipline.ui.components.OneUiField
import app.discipline.ui.components.OneUiScreen
import app.discipline.ui.components.PillButton
import app.discipline.ui.theme.OneUi
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

// ============================================================================ health data

/** Latest value of every Health Connect type Discipline can read, grouped like Health Connect. */
@Composable
fun HealthDataScreen() {
    val c = appContainer()
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val s = rememberSettings() ?: return
    val status by c.health.status.collectAsStateWithLifecycle()
    val readings by c.diary.latestReadings().collectAsStateWithLifecycle(initialValue = emptyList())
    val byType = remember(readings) { readings.associateBy { it.type } }
    val today = LocalDate.now()
    var refreshing by remember { mutableStateOf(false) }

    fun open(t: HcType) = when (t) {
        HcType.WEIGHT -> nav.selectTab(Tab.PROGRESS)
        HcType.EXERCISE -> nav.push(Route.Exercise(today.toEpochDay()))
        HcType.NUTRITION -> nav.push(Route.Nutrition(today.toEpochDay()))
        HcType.HYDRATION -> nav.selectTab(Tab.TODAY)
        else -> nav.push(Route.LogHealth(t.name))
    }

    OneUiScreen(
        title = "Health data",
        subtitle = "Latest from Health Connect",
        onBack = { nav.pop() },
        headerFraction = 0.24f,
        actions = {
            IconButton(enabled = !refreshing, onClick = {
                refreshing = true
                scope.launch {
                    val n = runCatching { c.health.readRecent(30) }.getOrDefault(0)
                    refreshing = false
                    snackbar.showSnackbar(if (n > 0) "Updated from Health Connect" else "Nothing new")
                }
            }) { Icon(Icons.Rounded.Refresh, "Refresh", tint = OneUi.colors.textPrimary) }
        },
    ) {
        HealthCategory.entries.forEach { cat ->
            val types = HcType.entries.filter { it.category == cat && status.supported(it) }
            if (types.isEmpty()) return@forEach
            item(key = cat.name) {
                Group(label = cat.label) {
                    types.forEachIndexed { i, t ->
                        if (i > 0) GroupDivider(76.dp)
                        val r = byType[t]
                        val allowed = status.canRead(t) && s.health.canRead(t)
                        ListRow(
                            title = t.label,
                            summary = when {
                                r != null -> HealthRecords.format(t, r.value, r.value2, r.choice) + " · " + friendlyDate(LocalDate.ofEpochDay(r.epochDay)) +
                                    (if (r.sourceApp.isNotBlank()) " · ${r.sourceApp}" else "")
                                !allowed -> "Reading not allowed"
                                else -> "No data in the last 30 days"
                            },
                            summaryColor = if (r == null) OneUi.colors.textTertiary else null,
                            leading = { IconBadge(categoryIcon(cat), categoryColor(cat)) },
                            trailing = { Chevron() },
                            onClick = { open(t) },
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Values come from every app that shares with Health Connect, including Discipline. Tap a type to log a new value.",
                style = MaterialTheme.typography.bodySmall, color = OneUi.colors.textTertiary,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
            )
        }
    }
}

// ============================================================================= log health

/** Generic entry form for any [HcType.loggable] type, or a type picker when [typeName] is null. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LogHealthScreen(typeName: String?) {
    val type = typeName?.let { n -> HcType.entries.firstOrNull { it.name == n } }
    if (type == null) { HealthTypePicker(); return }

    val c = appContainer()
    val nav = LocalNavigator.current
    val colors = OneUi.colors
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val logs by c.diary.healthLogs(type).collectAsStateWithLifecycle(initialValue = emptyList())

    val defaultStart = when (type.kind) {
        EntryKind.SESSION -> if (type == HcType.SLEEP) LocalTime.of(23, 0) else LocalTime.now().minusMinutes(10)
        EntryKind.AMOUNT -> LocalTime.now().minusMinutes(30)
        else -> LocalTime.now()
    }.withSecond(0).withNano(0)
    val defaultDay = if (type == HcType.SLEEP) LocalDate.now().minusDays(1) else LocalDate.now()

    var day by rememberSaveable { mutableStateOf(defaultDay.toEpochDay()) }
    var time by rememberSaveable { mutableStateOf(defaultStart.toSecondOfDay()) }
    var value by rememberSaveable { mutableStateOf("") }
    var value2 by rememberSaveable { mutableStateOf("") }
    var choice by rememberSaveable { mutableStateOf(type.options.firstOrNull()?.first) }
    var duration by rememberSaveable {
        mutableStateOf(
            when (type.kind) {
                EntryKind.SESSION -> if (type == HcType.SLEEP) "480" else "10"
                EntryKind.AMOUNT -> "30"
                EntryKind.PERIOD -> "5"
                else -> ""
            },
        )
    }
    var pickingDay by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }

    fun String.num() = replace(',', '.').trim().toDoubleOrNull()
    val v = value.num()
    val v2 = value2.num()
    val dur = duration.num()
    val needsValue = type.kind in setOf(EntryKind.AMOUNT, EntryKind.VALUE, EntryKind.SAMPLE, EntryKind.PAIR)
    val needsDuration = type.kind in setOf(EntryKind.AMOUNT, EntryKind.SESSION, EntryKind.PERIOD)
    val valid = (!needsValue || (v != null && v in type.range)) &&
        (type.kind != EntryKind.PAIR || (v2 != null && v2 in type.range && v2 < v!!)) &&
        (!needsDuration || (dur != null && dur >= 1 && dur <= if (type.kind == EntryKind.PERIOD) 31.0 else 1440.0)) &&
        (type.kind != EntryKind.CHOICE || choice != null)

    fun save() {
        val date = LocalDate.ofEpochDay(day)
        val start = if (type.kind == EntryKind.PERIOD) date.atStartOfDay(zone).toInstant()
        else date.atTime(LocalTime.ofSecondOfDay(time.toLong())).atZone(zone).toInstant()
        val end = when (type.kind) {
            EntryKind.PERIOD -> date.plusDays(dur!!.roundToLong()).atStartOfDay(zone).toInstant()
            EntryKind.AMOUNT, EntryKind.SESSION -> start.plusSeconds((dur!! * 60).roundToLong())
            else -> start
        }
        val e = HealthLogEntity(
            type = type,
            start = start.toEpochMilli(),
            end = end.toEpochMilli(),
            value = when (type.kind) {
                EntryKind.SESSION -> dur
                EntryKind.PERIOD -> dur
                EntryKind.EVENT, EntryKind.CHOICE -> null
                else -> v
            },
            value2 = if (type.kind == EntryKind.PAIR) v2 else null,
            choice = if (type.kind == EntryKind.CHOICE) choice else null,
        )
        c.scope.launch { c.diary.saveHealthLog(e) }
        nav.pop()
    }

    OneUiScreen(
        title = type.label,
        subtitle = type.category.label,
        onBack = { nav.pop() },
        headerFraction = 0.22f,
        bottomBar = {
            BottomActionBar { PillButton("Save", enabled = valid, modifier = Modifier.fillMaxWidth(), onClick = ::save) }
        },
    ) {
        item {
            Group(label = "New entry", contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (type.kind) {
                        EntryKind.PAIR -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OneUiField(value, { value = it.take(5) }, "Systolic", Modifier.weight(1f), suffix = type.unit, keyboardType = KeyboardType.Number)
                            OneUiField(value2, { value2 = it.take(5) }, "Diastolic", Modifier.weight(1f), suffix = type.unit, keyboardType = KeyboardType.Number)
                        }
                        EntryKind.AMOUNT, EntryKind.VALUE, EntryKind.SAMPLE -> OneUiField(
                            value, { value = it.take(9) }, type.label, suffix = type.unit, keyboardType = KeyboardType.Decimal,
                            isError = value.isNotBlank() && (v == null || v !in type.range),
                        )
                        EntryKind.CHOICE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            type.options.forEach { (id, label) ->
                                val sel = id == choice
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (sel) Color.White else colors.textPrimary,
                                    modifier = Modifier.clip(OneUi.PillShape)
                                        .background(if (sel) colors.accent else colors.containerHigh)
                                        .clickable { choice = id }
                                        .padding(horizontal = 14.dp, vertical = 9.dp),
                                )
                            }
                        }
                        else -> Unit
                    }
                    val dateLabel = when (type.kind) {
                        EntryKind.PERIOD -> "First day"
                        EntryKind.SESSION -> if (type == HcType.SLEEP) "Went to bed" else "Started"
                        EntryKind.AMOUNT -> "Started"
                        else -> "Date"
                    }
                    ListRow(
                        title = dateLabel,
                        summary = friendlyDate(LocalDate.ofEpochDay(day)),
                        onClick = { pickingDay = true },
                        modifier = Modifier.clip(OneUi.InnerShape).background(colors.containerHigh),
                    )
                    if (type.kind != EntryKind.PERIOD) {
                        ListRow(
                            title = "Time",
                            summary = LocalTime.ofSecondOfDay(time.toLong()).format(timeFmt),
                            onClick = { pickingTime = true },
                            modifier = Modifier.clip(OneUi.InnerShape).background(colors.containerHigh),
                        )
                    }
                    if (needsDuration) {
                        OneUiField(
                            duration, { duration = it.filter(Char::isDigit).take(4) },
                            if (type.kind == EntryKind.PERIOD) "Length" else "Duration",
                            suffix = if (type.kind == EntryKind.PERIOD) "days" else "min",
                            keyboardType = KeyboardType.Number,
                            isError = dur == null,
                        )
                        if (type.kind == EntryKind.SESSION && dur != null && dur >= 60) {
                            Text(HealthRecords.minutes(dur), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 6.dp))
                        }
                    }
                }
            }
        }
        item {
            Group(label = "Logged in Discipline") {
                if (logs.isEmpty()) EmptyHint("Entries you add here are saved to Health Connect.")
                logs.forEachIndexed { i, e ->
                    if (i > 0) GroupDivider()
                    val at = Instant.ofEpochMilli(e.start).atZone(zone)
                    val minutes = (e.end - e.start) / 60_000.0
                    val shown = when (type.kind) {
                        EntryKind.AMOUNT -> HealthRecords.format(type, e.value ?: 0.0) + " · " + HealthRecords.minutes(minutes)
                        EntryKind.SESSION -> HealthRecords.minutes(minutes)
                        EntryKind.PERIOD -> "${Math.round(minutes / 1440)} days"
                        else -> HealthRecords.format(type, e.value ?: 0.0, e.value2, e.choice)
                    }
                    ListRow(
                        title = shown,
                        summary = friendlyDate(at.toLocalDate()) + if (type.kind != EntryKind.PERIOD) " · " + at.toLocalTime().format(timeFmt) else "",
                        trailing = {
                            IconButton(onClick = { scope.launch { c.diary.deleteHealthLog(e) } }) {
                                Icon(Icons.Rounded.DeleteOutline, "Delete", tint = colors.danger)
                            }
                        },
                    )
                }
            }
        }
    }

    if (pickingDay) {
        DayPicker(LocalDate.ofEpochDay(day), onDismiss = { pickingDay = false }) {
            day = minOf(it, LocalDate.now()).toEpochDay(); pickingDay = false
        }
    }
    if (pickingTime) {
        val t = LocalTime.ofSecondOfDay(time.toLong())
        val state = rememberTimePickerState(t.hour, t.minute, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = { AccentTextButton("Done", onClick = { time = LocalTime.of(state.hour, state.minute).toSecondOfDay(); pickingTime = false }) },
            dismissButton = { AccentTextButton("Cancel", onClick = { pickingTime = false }, color = colors.textSecondary) },
            title = { Text("Time") },
            shape = OneUi.ContainerShape,
        ) { TimePicker(state) }
    }
}

@Composable
private fun HealthTypePicker() {
    val c = appContainer()
    val nav = LocalNavigator.current
    val status by c.health.status.collectAsStateWithLifecycle()
    OneUiScreen(title = "Log health data", onBack = { nav.pop() }, headerFraction = 0.24f) {
        HealthCategory.entries.forEach { cat ->
            val types = HcType.entries.filter { it.category == cat && it.loggable && status.supported(it) }
            if (types.isEmpty()) return@forEach
            item(key = cat.name) {
                Group(label = cat.label) {
                    types.forEachIndexed { i, t ->
                        if (i > 0) GroupDivider(76.dp)
                        ListRow(
                            title = t.label,
                            summary = if (status.canWrite(t)) null else "Saved here; allow writing in Health Connect to share it",
                            summaryColor = OneUi.colors.textTertiary,
                            leading = { IconBadge(categoryIcon(cat), categoryColor(cat)) },
                            trailing = { Chevron() },
                            onClick = { nav.replace(Route.LogHealth(t.name)) },
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Food, water, weight and workouts have their own screens in the diary.",
                style = MaterialTheme.typography.bodySmall, color = OneUi.colors.textTertiary,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
            )
        }
    }
}
