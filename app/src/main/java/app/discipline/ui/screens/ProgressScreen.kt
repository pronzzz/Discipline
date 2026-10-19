package app.discipline.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.discipline.data.db.WeightSource
import app.discipline.domain.Units
import app.discipline.ui.components.AccentTextButton
import app.discipline.ui.components.BarChart
import app.discipline.ui.components.ChartPoint
import app.discipline.ui.components.EmptyHint
import app.discipline.ui.components.Group
import app.discipline.ui.components.GroupDivider
import app.discipline.ui.components.LineChart
import app.discipline.ui.components.ListRow
import app.discipline.ui.components.OneUiField
import app.discipline.ui.components.OneUiScreen
import app.discipline.ui.components.PillButton
import app.discipline.ui.components.Segmented
import app.discipline.ui.components.Stat
import app.discipline.ui.theme.OneUi
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class Range(val label: String, val days: Long) { WEEK("1W", 7), MONTH("1M", 30), QUARTER("3M", 90), YEAR("1Y", 365) }

@Composable
fun ProgressScreen() {
    val c = appContainer()
    val settings = rememberSettings() ?: return
    val colors = OneUi.colors
    val units = settings.units
    var range by rememberSaveable { mutableStateOf(Range.MONTH) }
    var logging by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val from = today.minusDays(range.days - 1)
    val weights by remember(range) { c.diary.weights(from, today) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val calories by remember(range) { c.diary.caloriesByDay(from, today) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val health by remember(range) { c.diary.healthDays(from, today) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val goals = settings.goals()

    OneUiScreen(title = "Progress", subtitle = "Weight, calories and activity", padNavigationBar = false) {
        item {
            Segmented(Range.entries, range, { it.label }, { range = it }, Modifier.padding(horizontal = 16.dp))
        }

        // ------------------------------------------------------------ weight
        item {
            Group(label = "Weight", contentPadding = PaddingValues(20.dp)) {
                val first = weights.firstOrNull()
                val last = weights.lastOrNull()
                Row(Modifier.fillMaxWidth()) {
                    Stat(last?.let { Units.weight(it.kg, units) } ?: "—", "Current", Modifier.weight(1f))
                    Stat(
                        if (first != null && last != null) (if (last.kg - first.kg >= 0) "+" else "−") + Units.weight(kotlin.math.abs(last.kg - first.kg), units) else "—",
                        "Change", Modifier.weight(1f),
                    )
                    Stat(Units.weight(settings.profile.goalWeightKg, units), "Goal", Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                if (weights.size >= 2) {
                    LineChart(
                        weights.map { ChartPoint(LocalDate.ofEpochDay(it.epochDay), it.kg) },
                        colors.weight, target = settings.profile.goalWeightKg, from = from, to = today,
                    )
                } else {
                    Text("Log your weight on two or more days to see a trend.", style = MaterialTheme.typography.bodyMedium, color = colors.textTertiary)
                }
                Spacer(Modifier.height(16.dp))
                PillButton("Log weight", onClick = { logging = true }, leadingIcon = Icons.Rounded.Add, modifier = Modifier.fillMaxWidth())
            }
        }

        // ------------------------------------------------------------ calories
        item {
            Group(label = "Calories eaten", contentPadding = PaddingValues(20.dp)) {
                val logged = calories.filter { it.calories > 0 }
                val avg = if (logged.isEmpty()) 0 else (logged.sumOf { it.calories } / logged.size).roundToInt()
                Row(Modifier.fillMaxWidth()) {
                    Stat(Units.kcal(avg), "Daily average", Modifier.weight(1f))
                    Stat(Units.kcal(goals.calories), "Goal", Modifier.weight(1f))
                    Stat("${logged.size}", "Days logged", Modifier.weight(1f))
                }
                Spacer(Modifier.height(18.dp))
                BarChart(
                    calories.map { ChartPoint(LocalDate.ofEpochDay(it.epochDay), it.calories) },
                    colors.calories, goal = goals.calories.toDouble(), from = from, to = today,
                )
            }
        }

        // ------------------------------------------------------------ steps
        if (health.any { it.steps > 0 }) {
            item {
                Group(label = "Steps", contentPadding = PaddingValues(20.dp)) {
                    val withSteps = health.filter { it.steps > 0 }
                    Row(Modifier.fillMaxWidth()) {
                        Stat(Units.integer(withSteps.sumOf { it.steps } / withSteps.size.coerceAtLeast(1)), "Daily average", Modifier.weight(1f))
                        Stat(Units.integer(goals.steps.toLong()), "Goal", Modifier.weight(1f))
                        Stat("${withSteps.count { it.steps >= goals.steps }}", "Goal days", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(18.dp))
                    BarChart(
                        health.map { ChartPoint(LocalDate.ofEpochDay(it.epochDay), it.steps.toDouble()) },
                        colors.steps, goal = goals.steps.toDouble(), from = from, to = today, overColor = colors.steps,
                    )
                }
            }
        }

        // ------------------------------------------------------------ history
        item {
            Group(label = "Weight history") {
                val recent = weights.reversed().take(12)
                if (recent.isEmpty()) EmptyHint("No weigh-ins in this period")
                recent.forEachIndexed { i, w ->
                    if (i > 0) GroupDivider()
                    ListRow(
                        title = Units.weight(w.kg, units),
                        summary = LocalDate.ofEpochDay(w.epochDay).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")) +
                            if (w.source == WeightSource.HEALTH_CONNECT) " · Health Connect" else "",
                        trailing = {
                            IconButton(onClick = { c.scope.launch { c.diary.deleteWeight(LocalDate.ofEpochDay(w.epochDay)) } }) {
                                Icon(Icons.Rounded.DeleteOutline, "Delete", tint = colors.textTertiary)
                            }
                        },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    if (logging) {
        WeightDialog(
            initialKg = weights.lastOrNull()?.kg ?: settings.profile.currentWeightKg,
            units = units,
            onDismiss = { logging = false },
            onSave = { day, kg ->
                logging = false
                c.scope.launch {
                    c.diary.logWeight(day, kg)
                    val latest = c.db.diary().allWeights().lastOrNull()
                    if (latest != null) c.settings.setCurrentWeight(latest.kg)
                }
            },
        )
    }
}

@Composable
fun WeightDialog(initialKg: Double, units: app.discipline.domain.UnitSystem, onDismiss: () -> Unit, onSave: (LocalDate, Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf(Units.number(Units.weightValue(initialKg, units))) }
    var day by remember { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf(false) }
    val value = text.replace(',', '.').toDoubleOrNull()?.takeIf { it in 20.0..700.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = OneUi.ContainerShape,
        containerColor = OneUi.colors.container,
        title = { Text("Log weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OneUiField(text, { text = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(6) }, "Weight",
                    suffix = Units.weightUnit(units), keyboardType = KeyboardType.Decimal, isError = value == null)
                AccentTextButton(friendlyDate(day), onClick = { picking = true })
            }
        },
        confirmButton = { AccentTextButton("Save", onClick = { value?.let { onSave(day, Units.weightToKg(it, units)) } }) },
        dismissButton = { AccentTextButton("Cancel", onClick = onDismiss, color = OneUi.colors.textSecondary) },
    )
    if (picking) DayPicker(day, onDismiss = { picking = false }, onPicked = { day = minOf(it, LocalDate.now()); picking = false })
}
