package app.discipline.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.discipline.data.db.FoodEntryEntity
import app.discipline.domain.Units
import app.discipline.domain.sum
import app.discipline.ui.DayUi
import app.discipline.ui.LocalNavigator
import app.discipline.ui.LocalSnackbar
import app.discipline.ui.Route
import app.discipline.ui.components.AccentTextButton
import app.discipline.ui.components.Group
import app.discipline.ui.components.GroupDivider
import app.discipline.ui.components.ListRow
import app.discipline.ui.components.OneUiScreen
import app.discipline.ui.theme.OneUi
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun DiaryScreen(day: LocalDate, onDayChange: (LocalDate) -> Unit) {
    val ui = rememberDayUi(day) ?: return
    val nav = LocalNavigator.current
    val colors = OneUi.colors
    var picking by remember { mutableStateOf(false) }

    OneUiScreen(
        title = "Diary",
        subtitle = "${Units.kcal(ui.food.calories)} of ${Units.kcal(ui.goals.calories + ui.exerciseCredit)} kcal",
        padNavigationBar = false,
        actions = {
            if (day != LocalDate.now()) {
                IconButton(onClick = { onDayChange(LocalDate.now()) }) {
                    Icon(Icons.Rounded.Today, contentDescription = "Go to today", tint = colors.textPrimary)
                }
            }
        },
    ) {
        item { DateBar(day, onDayChange, onPick = { picking = true }) }
        item { EquationCard(ui) }

        for (meal in 0..3) {
            item(key = "meal$meal") { MealCard(ui, meal) }
        }

        item(key = "exercise") { ExerciseCard(ui) }
        item(key = "water") { WaterCard(ui) }
        item(key = "nutrition") {
            Group {
                ListRow(
                    title = "Nutrition",
                    summary = "Macros and nutrients for ${friendlyDate(day).lowercase()}",
                    onClick = { nav.push(Route.Nutrition(day.toEpochDay())) },
                    trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = colors.textTertiary) },
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (picking) DayPicker(day, onDismiss = { picking = false }, onPicked = { picking = false; onDayChange(it) })
}

@Composable
private fun DateBar(day: LocalDate, onDayChange: (LocalDate) -> Unit, onPick: () -> Unit) {
    val colors = OneUi.colors
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .padding(horizontal = OneUi.ScreenPadding)
            .fillMaxWidth()
            .clip(OneUi.PillShape)
            .background(colors.container)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onDayChange(day.minusDays(1)) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous day", tint = colors.textPrimary)
        }
        Text(
            friendlyDate(day),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).clip(OneUi.PillShape).clickable(onClick = onPick).padding(vertical = 10.dp),
        )
        IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onDayChange(day.plusDays(1)) }) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next day", tint = colors.textPrimary)
        }
    }
}

@Composable
private fun EquationCard(ui: DayUi) {
    val colors = OneUi.colors
    Group(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            EqCell(Units.kcal(ui.goals.calories), "Goal", Modifier.weight(1f))
            EqOp("−")
            EqCell(Units.kcal(ui.food.calories), "Food", Modifier.weight(1f))
            EqOp("+")
            EqCell(Units.kcal(ui.exerciseCredit), "Exercise", Modifier.weight(1f))
            EqOp("=")
            EqCell(
                Units.kcal(ui.remaining), "Remaining", Modifier.weight(1.15f),
                color = if (ui.remaining < 0) colors.danger else colors.accent,
            )
        }
    }
}

@Composable
private fun EqCell(value: String, label: String, modifier: Modifier, color: Color = OneUi.colors.textPrimary) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OneUi.colors.textSecondary, maxLines = 1)
    }
}

@Composable
private fun EqOp(op: String) {
    Text(op, style = MaterialTheme.typography.titleMedium, color = OneUi.colors.textTertiary, modifier = Modifier.padding(horizontal = 2.dp))
}

@Composable
private fun MealCard(ui: DayUi, meal: Int) {
    val c = appContainer()
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val colors = OneUi.colors
    val entries = ui.byMeal[meal].orEmpty()
    var menu by remember { mutableStateOf(false) }
    val day = ui.day

    Group(label = null) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(ui.settings.mealName(meal), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                if (entries.isNotEmpty()) {
                    val n = entries.map { it.nutrients }.sum()
                    Text(
                        "C ${n.carbs.toInt()}g · P ${n.protein.toInt()}g · F ${n.fat.toInt()}g",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            Text(Units.kcal(ui.mealCalories(meal)), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Meal options", tint = colors.textSecondary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = OneUi.InnerShape, containerColor = colors.container) {
                    DropdownMenuItem(text = { Text("Quick add calories") }, onClick = { menu = false; nav.push(Route.QuickAdd(day.toEpochDay(), meal)) })
                    DropdownMenuItem(text = { Text("Copy from yesterday") }, onClick = {
                        menu = false
                        scope.launch {
                            val n = c.diary.copyMeal(day.minusDays(1), meal, day, meal)
                            snackbar.showSnackbar(if (n == 0) "Nothing logged yesterday" else "Copied $n item${if (n == 1) "" else "s"}")
                        }
                    })
                    if (day != LocalDate.now()) {
                        DropdownMenuItem(text = { Text("Copy to today") }, onClick = {
                            menu = false
                            scope.launch {
                                val n = c.diary.copyMeal(day, meal, LocalDate.now(), meal)
                                snackbar.showSnackbar("Copied $n item${if (n == 1) "" else "s"} to today")
                            }
                        })
                    }
                }
            }
        }
        entries.forEach { e ->
            key(e.id) {
                SwipeableEntry(
                    e,
                    onClick = { nav.push(Route.EditEntry(e.id)) },
                    onDelete = {
                        scope.launch {
                            val removed = c.diary.deleteEntry(e.id) ?: return@launch
                            val res = snackbar.showSnackbar("Removed ${removed.name}", actionLabel = "Undo", duration = SnackbarDuration.Long)
                            if (res == SnackbarResult.ActionPerformed) c.diary.restoreEntry(removed)
                        }
                    },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 6.dp)) {
            AccentTextButton("Add food", onClick = { nav.push(Route.Search(day.toEpochDay(), meal)) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableEntry(e: FoodEntryEntity, onClick: () -> Unit, onDelete: () -> Unit) {
    val colors = OneUi.colors
    val haptics = LocalHapticFeedback.current
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = {
            if (it == SwipeToDismissBoxValue.EndToStart) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onDelete()
            }
        },
        backgroundContent = {
            val bg by animateColorAsState(
                if (state.targetValue == SwipeToDismissBoxValue.EndToStart) colors.danger else colors.danger.copy(alpha = 0.6f),
                label = "swipe",
            )
            Box(Modifier.fillMaxSize().background(bg).padding(end = 24.dp), contentAlignment = Alignment.CenterEnd) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color.White)
            }
        },
    ) {
        Row(
            Modifier.fillMaxWidth().background(colors.container).clickable(onClick = onClick)
                .heightIn(min = 60.dp).padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(e.name, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = listOfNotNull(
                    e.brand,
                    if (e.foodId == null) null else "${e.servings.fmt()} × ${e.servingLabel}",
                ).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(Units.kcal(e.nutrients.calories), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
        }
    }
}

@Composable
private fun ExerciseCard(ui: DayUi) {
    val nav = LocalNavigator.current
    val colors = OneUi.colors
    Group {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Exercise", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(Units.kcal(ui.manualExerciseKcal + ui.importedExerciseKcal), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        }
        ui.exercise.forEach { e ->
            ListRow(
                title = e.name,
                summary = "${e.minutes} min",
                trailing = { Text(Units.kcal(e.calories), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary) },
                onClick = { nav.push(Route.Exercise(ui.day.toEpochDay(), e.id)) },
            )
        }
        ui.imported.forEach { e ->
            ListRow(
                title = e.title,
                summary = "${e.minutes} min · from ${e.sourceApp}",
                trailing = { Text(e.calories?.let { Units.kcal(it) } ?: "—", style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary) },
            )
        }
        if (ui.stepKcal > 0) {
            ListRow(
                title = "Step adjustment",
                summary = "${Units.integer(ui.steps)} steps from Health Connect",
                trailing = { Text(Units.kcal(ui.stepKcal), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary) },
            )
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 6.dp)) {
            AccentTextButton("Add exercise", onClick = { nav.push(Route.Exercise(ui.day.toEpochDay())) })
        }
    }
}

@Composable
private fun WaterCard(ui: DayUi) {
    val c = appContainer()
    val colors = OneUi.colors
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val snackbar = LocalSnackbar.current
    val units = ui.settings.units
    Group {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Water", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text("${Units.water(ui.waterMl, units)} / ${Units.water(ui.goals.waterMl, units)}", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(250, 330, 500, 750).forEach { ml ->
                AssistChip(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        scope.launch { c.diary.addWater(ui.day, ml) }
                    },
                    label = { Text("+ ${Units.water(ml, units)}") },
                    leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) },
                    shape = OneUi.PillShape,
                    colors = AssistChipDefaults.assistChipColors(containerColor = colors.containerHigh, labelColor = colors.water, leadingIconContentColor = colors.water),
                    border = null,
                )
            }
        }
        if (ui.water.isNotEmpty()) {
            GroupDivider()
            val last = ui.water.last()
            ListRow(
                title = "Undo last (${Units.water(last.ml, units)})",
                summary = "${ui.water.size} drink${if (ui.water.size == 1) "" else "s"} logged",
                onClick = {
                    scope.launch {
                        c.diary.deleteWater(last.id)
                        snackbar.showSnackbar("Removed ${Units.water(last.ml, units)}")
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPicker(day: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            AccentTextButton("Done", onClick = {
                state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            })
        },
        dismissButton = { AccentTextButton("Cancel", onClick = onDismiss, color = OneUi.colors.textSecondary) },
        shape = OneUi.ContainerShape,
    ) {
        DatePicker(state = state)
    }
}
