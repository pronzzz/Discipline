package app.discipline.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.discipline.data.db.ExerciseEntity
import app.discipline.data.db.FoodEntity
import app.discipline.data.db.FoodSource
import app.discipline.data.food.SearchKey
import app.discipline.domain.ExerciseKind
import app.discipline.domain.Nutrients
import app.discipline.domain.Units
import app.discipline.domain.sum
import app.discipline.ui.LocalNavigator
import app.discipline.ui.LocalSnackbar
import app.discipline.ui.Route
import app.discipline.ui.components.AccentTextButton
import app.discipline.ui.components.BottomActionBar
import app.discipline.ui.components.Group
import app.discipline.ui.components.GroupDivider
import app.discipline.ui.components.ListRow
import app.discipline.ui.components.Meter
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
import kotlin.math.roundToInt

private fun String.num(): Double? = replace(',', '.').trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
private fun numFilter(s: String) = s.filter { it.isDigit() || it == '.' || it == ',' }.take(9)

// ============================================================================== create food

@Composable
fun CreateFoodScreen(route: Route.CreateFood) {
    val c = appContainer()
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    var loadedId by remember { mutableStateOf<Long?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var brand by rememberSaveable { mutableStateOf("") }
    var barcode by rememberSaveable { mutableStateOf(route.barcode ?: "") }
    var serving by rememberSaveable { mutableStateOf("1 serving") }
    var grams by rememberSaveable { mutableStateOf("") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var fiber by rememberSaveable { mutableStateOf("") }
    var sugar by rememberSaveable { mutableStateOf("") }
    var satFat by rememberSaveable { mutableStateOf("") }
    var sodium by rememberSaveable { mutableStateOf("") }
    var cholesterol by rememberSaveable { mutableStateOf("") }
    var potassium by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(route.editFoodId) {
        val id = route.editFoodId ?: return@LaunchedEffect
        if (loadedId == id) return@LaunchedEffect
        val f = c.foods.byId(id) ?: return@LaunchedEffect
        loadedId = id
        name = f.name; brand = f.brand.orEmpty(); barcode = f.barcode.orEmpty(); serving = f.servingLabel
        grams = f.servingGrams?.fmt().orEmpty()
        val n = f.nutrients
        kcal = n.calories.fmt(); protein = n.protein.fmt(); carbs = n.carbs.fmt(); fat = n.fat.fmt()
        fiber = n.fiber?.fmt().orEmpty(); sugar = n.sugar?.fmt().orEmpty(); satFat = n.saturatedFat?.fmt().orEmpty()
        sodium = n.sodiumMg?.fmt().orEmpty(); cholesterol = n.cholesterolMg?.fmt().orEmpty(); potassium = n.potassiumMg?.fmt().orEmpty()
    }

    val valid = name.isNotBlank() && serving.isNotBlank() && kcal.num() != null
    val editing = route.editFoodId != null

    OneUiScreen(
        title = if (editing) "Edit food" else "Create food",
        subtitle = "Values per serving",
        onBack = { nav.pop() },
        headerFraction = 0.22f,
        actions = {
            if (editing) IconButton(onClick = {
                scope.launch { c.foods.deleteUserFood(route.editFoodId); nav.pop(); if (nav.current is Route.FoodDetail) nav.pop(); snackbar.showSnackbar("Food deleted") }
            }) { Icon(Icons.Rounded.DeleteOutline, "Delete food", tint = OneUi.colors.danger) }
        },
        bottomBar = {
            BottomActionBar {
                PillButton(if (editing) "Save" else "Save food", enabled = valid, modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch {
                        val food = FoodEntity(
                            id = route.editFoodId ?: 0,
                            name = name.trim(),
                            brand = brand.trim().ifEmpty { null },
                            barcode = barcode.filter(Char::isDigit).ifEmpty { null },
                            servingLabel = serving.trim(),
                            servingGrams = grams.num()?.takeIf { it > 0 },
                            nutrients = Nutrients(
                                calories = kcal.num() ?: 0.0, protein = protein.num() ?: 0.0, carbs = carbs.num() ?: 0.0, fat = fat.num() ?: 0.0,
                                fiber = fiber.num(), sugar = sugar.num(), saturatedFat = satFat.num(),
                                sodiumMg = sodium.num(), cholesterolMg = cholesterol.num(), potassiumMg = potassium.num(),
                            ),
                            source = FoodSource.USER,
                            remoteId = null,
                            searchKey = SearchKey.of(name, brand),
                        )
                        val id = c.foods.saveUserFood(food)
                        val realId = if (editing) route.editFoodId else id
                        if (editing) nav.pop() else nav.replace(Route.FoodDetail(realId, route.day, route.meal))
                    }
                })
            }
        },
    ) {
        item {
            Group(label = "Food", contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OneUiField(name, { name = it.take(80) }, "Name", isError = name.isBlank())
                    OneUiField(brand, { brand = it.take(60) }, "Brand (optional)")
                    OneUiField(barcode, { barcode = it.filter(Char::isDigit).take(14) }, "Barcode (optional)", keyboardType = KeyboardType.Number)
                }
            }
        }
        item {
            Group(label = "Serving", contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OneUiField(serving, { serving = it.take(40) }, "Serving size", placeholder = "e.g. 1 cup, 1 bar")
                    OneUiField(grams, { grams = numFilter(it) }, "Weight of one serving (optional)", suffix = "g", keyboardType = KeyboardType.Decimal)
                }
            }
        }
        item {
            Group(label = "Nutrition per serving", contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OneUiField(kcal, { kcal = numFilter(it) }, "Calories", suffix = "kcal", keyboardType = KeyboardType.Decimal, isError = kcal.num() == null)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OneUiField(carbs, { carbs = numFilter(it) }, "Carbs", Modifier.weight(1f), suffix = "g", keyboardType = KeyboardType.Decimal)
                        OneUiField(protein, { protein = numFilter(it) }, "Protein", Modifier.weight(1f), suffix = "g", keyboardType = KeyboardType.Decimal)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OneUiField(fat, { fat = numFilter(it) }, "Fat", Modifier.weight(1f), suffix = "g", keyboardType = KeyboardType.Decimal)
                        OneUiField(satFat, { satFat = numFilter(it) }, "Sat. fat", Modifier.weight(1f), suffix = "g", keyboardType = KeyboardType.Decimal)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OneUiField(fiber, { fiber = numFilter(it) }, "Fibre", Modifier.weight(1f), suffix = "g", keyboardType = KeyboardType.Decimal)
                        OneUiField(sugar, { sugar = numFilter(it) }, "Sugar", Modifier.weight(1f), suffix = "g", keyboardType = KeyboardType.Decimal)
                    }
                    OneUiField(sodium, { sodium = numFilter(it) }, "Sodium", suffix = "mg", keyboardType = KeyboardType.Decimal)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OneUiField(cholesterol, { cholesterol = numFilter(it) }, "Cholesterol", Modifier.weight(1f), suffix = "mg", keyboardType = KeyboardType.Decimal)
                        OneUiField(potassium, { potassium = numFilter(it) }, "Potassium", Modifier.weight(1f), suffix = "mg", keyboardType = KeyboardType.Decimal)
                    }
                    val fromMacros = ((carbs.num() ?: 0.0) * 4 + (protein.num() ?: 0.0) * 4 + (fat.num() ?: 0.0) * 9)
                    if (fromMacros > 0 && kcal.num() == null) {
                        AccentTextButton("Use ${fromMacros.roundToInt()} kcal from macros", onClick = { kcal = fromMacros.roundToInt().toString() })
                    }
                }
            }
        }
    }
}

// ============================================================================== quick add

@Composable
fun QuickAddScreen(route: Route.QuickAdd) {
    val c = appContainer()
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val settings = rememberSettings()
    var kcal by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var meal by rememberSaveable { mutableStateOf(route.meal) }

    val fromMacros = (carbs.num() ?: 0.0) * 4 + (protein.num() ?: 0.0) * 4 + (fat.num() ?: 0.0) * 9
    val calories = kcal.num() ?: fromMacros.takeIf { it > 0 }

    OneUiScreen(
        title = "Quick add",
        subtitle = friendlyDate(LocalDate.ofEpochDay(route.day)),
        onBack = { nav.pop() },
        headerFraction = 0.24f,
        bottomBar = {
            BottomActionBar {
                PillButton("Add", enabled = calories != null && calories > 0, modifier = Modifier.fillMaxWidth(), onClick = {
                    val n = Nutrients(calories = calories ?: 0.0, carbs = carbs.num() ?: 0.0, protein = protein.num() ?: 0.0, fat = fat.num() ?: 0.0)
                    c.scope.launch { c.diary.quickAdd(LocalDate.ofEpochDay(route.day), meal, n, label.trim()) }
                    nav.pop()
                    if (nav.current is Route.Search) nav.pop()
                    scope.launch { snackbar.showSnackbar("Added ${Units.kcal(n.calories)} kcal") }
                })
            }
        },
    ) {
        item {
            Group(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OneUiField(kcal, { kcal = numFilter(it) }, "Calories", suffix = "kcal", keyboardType = KeyboardType.Decimal,
                        placeholder = if (fromMacros > 0) "${fromMacros.roundToInt()} from macros" else null)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OneUiField(carbs, { carbs = numFilter(it) }, "Carbs", Modifier.weight(1f), suffix = "g", keyboardType = KeyboardType.Decimal)
                        OneUiField(protein, { protein = numFilter(it) }, "Protein", Modifier.weight(1f), suffix = "g", keyboardType = KeyboardType.Decimal)
                        OneUiField(fat, { fat = numFilter(it) }, "Fat", Modifier.weight(1f), suffix = "g", keyboardType = KeyboardType.Decimal)
                    }
                    OneUiField(label, { label = it.take(60) }, "Description (optional)", placeholder = "e.g. Office cake")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Meal", style = MaterialTheme.typography.bodyLarge, color = OneUi.colors.textPrimary, modifier = Modifier.weight(1f))
                        MealPicker(meal, settings?.mealNames.orEmpty(), onPick = { meal = it })
                    }
                }
            }
        }
    }
}

// ============================================================================== exercise

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExerciseScreen(route: Route.Exercise) {
    val c = appContainer()
    val nav = LocalNavigator.current
    val settings = rememberSettings()
    val colors = OneUi.colors
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val day = LocalDate.ofEpochDay(route.day)

    var kind by rememberSaveable { mutableStateOf(ExerciseKind.WALKING) }
    var name by rememberSaveable { mutableStateOf(ExerciseKind.WALKING.label) }
    var minutes by rememberSaveable { mutableStateOf("30") }
    var kcal by rememberSaveable { mutableStateOf("") }
    var kcalEdited by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf(LocalTime.now().minusMinutes(30).withSecond(0).withNano(0).toSecondOfDay()) }
    var existing by remember { mutableStateOf<ExerciseEntity?>(null) }
    var pickingTime by remember { mutableStateOf(false) }

    LaunchedEffect(route.exerciseId) {
        val id = route.exerciseId ?: return@LaunchedEffect
        val e = c.db.diary().exercise(id) ?: return@LaunchedEffect
        existing = e
        kind = ExerciseKind.forHcType(e.hcExerciseType) ?: ExerciseKind.OTHER
        name = e.name; minutes = e.minutes.toString(); kcal = e.calories.toString(); kcalEdited = true
        start = Instant.ofEpochMilli(e.startedAt).atZone(zone).toLocalTime().toSecondOfDay()
    }

    val weight = settings?.profile?.currentWeightKg ?: 70.0
    val mins = minutes.num()?.toInt()?.takeIf { it in 1..1440 }
    LaunchedEffect(kind, mins, weight, kcalEdited) {
        if (!kcalEdited && mins != null) kcal = kind.calories(mins, weight).toString()
    }
    val calories = kcal.num()?.toInt()

    OneUiScreen(
        title = if (existing != null) "Edit exercise" else "Add exercise",
        subtitle = friendlyDate(day),
        onBack = { nav.pop() },
        headerFraction = 0.22f,
        actions = {
            existing?.let { e ->
                IconButton(onClick = { scope.launch { c.diary.deleteExercise(e.id); nav.pop() } }) {
                    Icon(Icons.Rounded.DeleteOutline, "Delete", tint = colors.danger)
                }
            }
        },
        bottomBar = {
            BottomActionBar {
                PillButton("Save", enabled = mins != null && calories != null && name.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                    val startedAt = day.atTime(LocalTime.ofSecondOfDay(start.toLong())).atZone(zone).toInstant().toEpochMilli()
                    val e = ExerciseEntity(
                        id = existing?.id ?: 0,
                        epochDay = route.day,
                        name = name.trim(),
                        minutes = mins!!,
                        calories = calories!!,
                        startedAt = startedAt,
                        hcExerciseType = kind.hcType,
                    )
                    c.scope.launch { c.diary.saveExercise(e) }
                    nav.pop()
                })
            }
        },
    ) {
        item {
            Group(label = "Activity", contentPadding = PaddingValues(14.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExerciseKind.entries.forEach { k ->
                        val sel = k == kind
                        Text(
                            k.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sel) androidx.compose.ui.graphics.Color.White else colors.textPrimary,
                            modifier = Modifier
                                .clip(OneUi.PillShape)
                                .background(if (sel) colors.accent else colors.containerHigh)
                                .clickable {
                                    if (name == kind.label || name.isBlank()) name = k.label
                                    kind = k
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }
        item {
            Group(label = "Details", contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OneUiField(name, { name = it.take(60) }, "Name")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OneUiField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, "Duration", Modifier.weight(1f), suffix = "min", keyboardType = KeyboardType.Number, isError = mins == null)
                        OneUiField(kcal, { kcal = it.filter(Char::isDigit).take(5); kcalEdited = true }, "Calories", Modifier.weight(1f), suffix = "kcal", keyboardType = KeyboardType.Number)
                    }
                    ListRow(
                        title = "Start time",
                        summary = LocalTime.ofSecondOfDay(start.toLong()).format(DateTimeFormatter.ofPattern("HH:mm")),
                        onClick = { pickingTime = true },
                        modifier = Modifier.clip(OneUi.InnerShape).background(colors.containerHigh),
                    )
                    if (kcalEdited) {
                        AccentTextButton("Estimate calories again", onClick = { kcalEdited = false })
                    } else {
                        Text(
                            "Estimated from ${kind.met} MET at ${Units.weight(weight, settings?.units ?: app.discipline.domain.UnitSystem.METRIC)}.",
                            style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                }
            }
        }
    }

    if (pickingTime) {
        val t = LocalTime.ofSecondOfDay(start.toLong())
        val state = rememberTimePickerState(t.hour, t.minute, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { pickingTime = false },
            confirmButton = { AccentTextButton("Done", onClick = { start = LocalTime.of(state.hour, state.minute).toSecondOfDay(); pickingTime = false }) },
            dismissButton = { AccentTextButton("Cancel", onClick = { pickingTime = false }, color = colors.textSecondary) },
            title = { Text("Start time") },
            shape = OneUi.ContainerShape,
        ) { TimePicker(state) }
    }
}

// ============================================================================== nutrition

@Composable
fun NutritionScreen(day: LocalDate) {
    val nav = LocalNavigator.current
    val ui = rememberDayUi(day) ?: return
    val colors = OneUi.colors
    val n = ui.food
    val g = ui.goals

    OneUiScreen(title = "Nutrition", subtitle = friendlyDate(day), onBack = { nav.pop() }, headerFraction = 0.24f) {
        item {
            Group(contentPadding = PaddingValues(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MacroDonut(n, Modifier.width(132.dp).height(132.dp))
                    Spacer(Modifier.width(22.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val (cp, pp, fp) = n.macroSplit()
                        SplitLine("Carbs", cp, ui.settings.macros.carbsPct, colors.carbs)
                        SplitLine("Protein", pp, ui.settings.macros.proteinPct, colors.protein)
                        SplitLine("Fat", fp, ui.settings.macros.fatPct, colors.fat)
                    }
                }
            }
        }
        item {
            Group(label = "Totals vs. goals", contentPadding = PaddingValues(vertical = 8.dp)) {
                GoalRow("Calories", n.calories, (g.calories + ui.exerciseCredit).toDouble(), "kcal", colors.calories)
                GoalRow("Carbohydrates", n.carbs, g.carbsG.toDouble(), "g", colors.carbs)
                GoalRow("Protein", n.protein, g.proteinG.toDouble(), "g", colors.protein)
                GoalRow("Fat", n.fat, g.fatG.toDouble(), "g", colors.fat)
                GoalRow("Saturated fat", n.saturatedFat ?: 0.0, (g.calories * 0.10 / 9).roundToInt().toDouble(), "g", colors.fat, limit = true)
                GoalRow("Fibre", n.fiber ?: 0.0, 30.0, "g", colors.success)
                GoalRow("Sugar", n.sugar ?: 0.0, (g.calories * 0.10 / 4).roundToInt().toDouble(), "g", colors.exercise, limit = true)
                GoalRow("Sodium", n.sodiumMg ?: 0.0, 2300.0, "mg", colors.water, limit = true)
                GoalRow("Cholesterol", n.cholesterolMg ?: 0.0, 300.0, "mg", colors.weight, limit = true)
                GoalRow("Potassium", n.potassiumMg ?: 0.0, 3500.0, "mg", colors.steps)
            }
        }
        item {
            Group(label = "By meal") {
                (0..3).forEachIndexed { i, meal ->
                    val m = ui.byMeal[meal].orEmpty().map { it.nutrients }.sum()
                    if (i > 0) GroupDivider()
                    ListRow(
                        title = ui.settings.mealName(meal),
                        summary = "C ${m.carbs.roundToInt()} g · P ${m.protein.roundToInt()} g · F ${m.fat.roundToInt()} g",
                        trailing = { Text("${Units.kcal(m.calories)} kcal", style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary) },
                    )
                }
            }
        }
        item {
            Text(
                "Sub-nutrient totals only include foods that list them.",
                style = MaterialTheme.typography.bodySmall, color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun SplitLine(label: String, actual: Float, goalPct: Int, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.width(10.dp).height(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(10.dp))
        Text("$label ${(actual * 100).roundToInt()}%", style = MaterialTheme.typography.bodyLarge, color = OneUi.colors.textPrimary)
        Text("  goal $goalPct%", style = MaterialTheme.typography.bodySmall, color = OneUi.colors.textSecondary)
    }
}

@Composable
private fun GoalRow(label: String, value: Double, goal: Double, unit: String, color: androidx.compose.ui.graphics.Color, limit: Boolean = false) {
    val colors = OneUi.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(
                "${Units.number(kotlin.math.round(value))} / ${Units.number(goal)} $unit",
                style = MaterialTheme.typography.bodyMedium,
                color = if (limit && value > goal) colors.danger else colors.textSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Meter(if (goal > 0) (value / goal).toFloat().let { if (!limit) it.coerceAtMost(1f) else it } else 0f, color, height = 6.dp)
    }
}
