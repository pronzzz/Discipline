package app.discipline.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.discipline.domain.Meal
import app.discipline.domain.Units
import app.discipline.health.HealthAvailability
import app.discipline.ui.LocalNavigator
import app.discipline.ui.Route
import app.discipline.ui.Tab
import app.discipline.ui.components.Group
import app.discipline.ui.components.IconBadge
import app.discipline.ui.components.ListRow
import app.discipline.ui.components.Meter
import app.discipline.ui.components.OneUiScreen
import app.discipline.ui.components.PillButton
import app.discipline.ui.components.Ring
import app.discipline.ui.components.TonalPillButton
import app.discipline.ui.theme.OneUi
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun TodayScreen(onOpenDiary: () -> Unit) {
    val today = remember { LocalDate.now() }
    val ui = rememberDayUi(today) ?: return
    val c = appContainer()
    val nav = LocalNavigator.current
    val colors = OneUi.colors
    val haptics = LocalHapticFeedback.current
    val hc by c.health.status.collectAsStateWithLifecycle()
    val latestWeight by c.diary.latestWeight().collectAsStateWithLifecycle(initialValue = null)
    val units = ui.settings.units

    val greeting = when (LocalTime.now().hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val subtitle = buildString {
        append(longDate(today))
        if (ui.streak > 1) append("  ·  ${ui.streak}-day streak")
    }

    OneUiScreen(title = greeting, subtitle = subtitle, padNavigationBar = false) {
        // ---------------------------------------------------------------- calories
        item {
            Group(contentPadding = PaddingValues(22.dp)) {
                Text("Calories", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text("Remaining = Goal − Food + Exercise", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(ui.calorieProgress, colors.calories, Modifier.size(148.dp), stroke = 13.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                Units.kcal(kotlin.math.abs(ui.remaining)),
                                style = MaterialTheme.typography.displaySmall,
                                color = if (ui.remaining < 0) colors.danger else colors.textPrimary,
                            )
                            Text(if (ui.remaining < 0) "Over" else "Remaining", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                    Spacer(Modifier.width(22.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Legend(Icons.Rounded.Flag, colors.textSecondary, "Base goal", Units.kcal(ui.goals.calories))
                        Legend(Icons.Rounded.Restaurant, colors.calories, "Food", Units.kcal(ui.food.calories))
                        Legend(Icons.Rounded.LocalFireDepartment, colors.exercise, "Exercise", Units.kcal(ui.exerciseCredit))
                    }
                }
            }
        }

        // ---------------------------------------------------------------- macros
        item {
            Group(contentPadding = PaddingValues(22.dp)) {
                Text("Macros", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Spacer(Modifier.height(14.dp))
                MacroLine("Carbs", ui.food.carbs, ui.goals.carbsG, colors.carbs)
                Spacer(Modifier.height(14.dp))
                MacroLine("Protein", ui.food.protein, ui.goals.proteinG, colors.protein)
                Spacer(Modifier.height(14.dp))
                MacroLine("Fat", ui.food.fat, ui.goals.fatG, colors.fat)
            }
        }

        // ---------------------------------------------------------------- tiles
        item {
            TileRow {
                Tile(
                    title = "Steps",
                    value = if (hc.connected) Units.integer(ui.steps) else "—",
                    icon = Icons.AutoMirrored.Rounded.DirectionsWalk,
                    tint = colors.steps,
                    detail = if (hc.connected) "Goal ${Units.integer(ui.goals.steps.toLong())}" else "Connect Health",
                    onClick = { nav.push(Route.HealthConnect) },
                ) { Meter(ui.steps.toFloat() / ui.goals.steps.coerceAtLeast(1), colors.steps) }
                Tile(
                    title = "Exercise",
                    value = "${Units.kcal(ui.manualExerciseKcal + ui.importedExerciseKcal)} kcal",
                    icon = Icons.Rounded.LocalFireDepartment,
                    tint = colors.exercise,
                    detail = "${ui.exerciseMinutes} min" + if (ui.stepKcal > 0) " · +${ui.stepKcal} steps" else "",
                    onClick = { nav.push(Route.Exercise(today.toEpochDay())) },
                )
            }
        }
        item {
            TileRow {
                Tile(
                    title = "Water",
                    value = Units.water(ui.waterMl, units),
                    icon = Icons.Rounded.WaterDrop,
                    tint = colors.water,
                    detail = "of ${Units.water(ui.goals.waterMl, units)}",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        c.scope.launch { c.diary.addWater(today, 250) }
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Meter(ui.waterMl.toFloat() / ui.goals.waterMl.coerceAtLeast(1), colors.water, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.Add, contentDescription = "Add 250 ml", tint = colors.water, modifier = Modifier.size(18.dp))
                    }
                }
                val w = latestWeight
                val goalKg = ui.settings.profile.goalWeightKg
                Tile(
                    title = "Weight",
                    value = w?.let { Units.weight(it.kg, units) } ?: "—",
                    icon = Icons.Rounded.MonitorWeight,
                    tint = colors.weight,
                    detail = w?.let {
                        val diff = it.kg - goalKg
                        if (kotlin.math.abs(diff) < 0.05) "At goal" else "${Units.weight(kotlin.math.abs(diff), units)} to goal"
                    } ?: "Tap to log",
                    onClick = { nav.selectTab(Tab.PROGRESS) },
                )
            }
        }

        // ---------------------------------------------------------------- health connect nudge
        if (!hc.connected && hc.availability != HealthAvailability.UNAVAILABLE) {
            item {
                Group {
                    ListRow(
                        title = "Connect Health Connect",
                        summary = "Bring in steps, workouts and weight, and share what you eat.",
                        leading = { IconBadge(Icons.Rounded.Favorite, colors.danger) },
                        onClick = { nav.push(Route.HealthConnect) },
                    )
                }
            }
        }

        // ---------------------------------------------------------------- quick actions
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val meal = Meal.forHour(LocalTime.now().hour).ordinal
                PillButton(
                    "Log food",
                    onClick = { nav.push(Route.Search(today.toEpochDay(), meal)) },
                    leadingIcon = Icons.Rounded.Search,
                    modifier = Modifier.weight(1f),
                )
                TonalPillButton("Diary", onClick = onOpenDiary, modifier = Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun Legend(icon: ImageVector, tint: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodySmall, color = OneUi.colors.textSecondary)
            Text(value, style = MaterialTheme.typography.titleMedium, color = OneUi.colors.textPrimary)
        }
    }
}

@Composable
fun MacroLine(label: String, value: Double, goal: Int, color: Color) {
    val colors = OneUi.colors
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(
                "${value.toInt()} / $goal g",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.End,
            )
        }
        Spacer(Modifier.height(8.dp))
        Meter(if (goal > 0) (value / goal).toFloat() else 0f, color)
    }
}
