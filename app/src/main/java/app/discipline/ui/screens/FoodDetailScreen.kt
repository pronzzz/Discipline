package app.discipline.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.discipline.data.db.FoodEntity
import app.discipline.data.db.FoodSource
import app.discipline.domain.Nutrients
import app.discipline.domain.Units
import app.discipline.ui.LocalNavigator
import app.discipline.ui.LocalSnackbar
import app.discipline.ui.Route
import app.discipline.ui.components.BottomActionBar
import app.discipline.ui.components.Group
import app.discipline.ui.components.GroupDivider
import app.discipline.ui.components.OneUiField
import app.discipline.ui.components.OneUiScreen
import app.discipline.ui.components.PillButton
import app.discipline.ui.components.Segmented
import app.discipline.ui.theme.OneUi
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class Loaded(
    val title: String,
    val brand: String?,
    val perServing: Nutrients,
    val servingLabel: String,
    val servingGrams: Double?,
    val initialServings: Double,
    val meal: Int,
    val day: LocalDate,
    val food: FoodEntity?,
    val entryId: Long?,
    val isQuickAdd: Boolean,
)

private enum class AmountUnit { SERVING, GRAM }

@Composable
fun FoodDetailScreen(foodId: Long?, entryId: Long?, day: Long?, meal: Int?) {
    val c = appContainer()
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val settings = rememberSettings()

    val loaded by produceState<Loaded?>(null, foodId, entryId) {
        value = if (entryId != null) {
            val e = c.diary.entry(entryId) ?: return@produceState
            Loaded(
                title = e.name, brand = e.brand,
                perServing = if (e.servings > 0) e.nutrients * (1.0 / e.servings) else e.nutrients,
                servingLabel = e.servingLabel, servingGrams = e.servingGrams, initialServings = e.servings,
                meal = e.meal, day = LocalDate.ofEpochDay(e.epochDay),
                food = e.foodId?.let { c.foods.byId(it) }, entryId = e.id, isQuickAdd = e.foodId == null,
            )
        } else {
            val f = c.foods.byId(foodId ?: return@produceState) ?: return@produceState
            Loaded(
                title = f.name, brand = f.brand, perServing = f.nutrients, servingLabel = f.servingLabel,
                servingGrams = f.servingGrams, initialServings = c.foods.lastServings(f.id) ?: 1.0,
                meal = meal ?: 0, day = LocalDate.ofEpochDay(day ?: LocalDate.now().toEpochDay()),
                food = f, entryId = null, isQuickAdd = false,
            )
        }
    }
    val l = loaded ?: run { OneUiScreen(title = "", onBack = { nav.pop() }) {}; return }

    var unit by remember(l) { mutableStateOf(AmountUnit.SERVING) }
    var amount by remember(l) { mutableStateOf(l.initialServings.fmt()) }
    var selectedMeal by remember(l) { mutableStateOf(l.meal) }
    var favorite by remember(l) { mutableStateOf(l.food?.favorite == true) }

    val parsed = amount.replace(',', '.').toDoubleOrNull()
    val servings: Double? = parsed?.takeIf { it > 0 && it < 10_000 }?.let {
        if (unit == AmountUnit.GRAM && l.servingGrams != null && l.servingGrams > 0) it / l.servingGrams else it
    }
    val total = l.perServing * (servings ?: 0.0)
    val editing = l.entryId != null

    OneUiScreen(
        title = l.title,
        subtitle = l.brand ?: if (l.isQuickAdd) "Quick add" else null,
        onBack = { nav.pop() },
        headerFraction = 0.24f,
        actions = {
            val food = l.food
            if (food != null) {
                IconButton(onClick = {
                    favorite = !favorite
                    scope.launch { c.foods.setFavorite(food.id, favorite) }
                }) {
                    Icon(if (favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline, "Favourite", tint = if (favorite) OneUi.colors.protein else OneUi.colors.textPrimary)
                }
                if (food.source == FoodSource.USER) {
                    IconButton(onClick = { nav.push(Route.CreateFood(l.day.toEpochDay(), selectedMeal, editFoodId = food.id)) }) {
                        Icon(Icons.Rounded.Edit, "Edit food", tint = OneUi.colors.textPrimary)
                    }
                }
            }
            if (editing) {
                IconButton(onClick = {
                    scope.launch {
                        c.diary.deleteEntry(l.entryId)
                        nav.pop()
                        snackbar.showSnackbar("Removed ${l.title}")
                    }
                }) { Icon(Icons.Rounded.DeleteOutline, "Delete entry", tint = OneUi.colors.danger) }
            }
        },
        bottomBar = {
            BottomActionBar {
                PillButton(
                    text = if (editing) "Save" else "Add to ${settings?.mealName(selectedMeal) ?: "meal"}",
                    enabled = servings != null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val s = servings ?: return@PillButton
                        c.scope.launch {
                            if (editing) c.diary.updateEntry(l.entryId, s, selectedMeal)
                            else c.diary.logFood(l.day, selectedMeal, l.food!!, s)
                        }
                        nav.pop()
                        if (!editing) {
                            // Leave search too, back to where the user started logging.
                            if (nav.current is Route.Search) nav.pop()
                            scope.launch { snackbar.showSnackbar("Added ${l.title}") }
                        }
                    },
                )
            }
        },
    ) {
        item {
            Group(contentPadding = PaddingValues(22.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MacroDonut(total, Modifier.size(132.dp))
                    Spacer(Modifier.width(22.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        val (cp, pp, fp) = total.macroSplit()
                        MacroLegend("Carbs", total.carbs, cp, OneUi.colors.carbs)
                        MacroLegend("Protein", total.protein, pp, OneUi.colors.protein)
                        MacroLegend("Fat", total.fat, fp, OneUi.colors.fat)
                    }
                }
            }
        }
        item {
            Group(label = "Amount", contentPadding = PaddingValues(18.dp)) {
                if (l.servingGrams != null && !l.isQuickAdd) {
                    Segmented(
                        AmountUnit.entries, unit,
                        label = { if (it == AmountUnit.SERVING) l.servingLabel else "Grams" },
                        onSelect = { new ->
                            // Keep the same quantity when switching units.
                            val g = l.servingGrams
                            val v = amount.replace(',', '.').toDoubleOrNull()
                            if (v != null && g > 0) amount = (if (new == AmountUnit.GRAM) v * g else v / g).let { kotlin.math.round(it * 100) / 100 }.fmt()
                            unit = new
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                }
                OneUiField(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(8) },
                    label = if (unit == AmountUnit.GRAM) "Grams" else "Number of servings",
                    suffix = if (unit == AmountUnit.GRAM) "g" else "× ${if (l.isQuickAdd) "entry" else l.servingLabel}",
                    keyboardType = KeyboardType.Decimal,
                    isError = servings == null,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Meal", style = MaterialTheme.typography.bodyLarge, color = OneUi.colors.textPrimary, modifier = Modifier.weight(1f))
                    MealPicker(selectedMeal, settings?.mealNames.orEmpty(), onPick = { selectedMeal = it })
                }
            }
        }
        item {
            Group(label = "Nutrition facts") {
                NutrientRow("Calories", "${Units.kcal(total.calories)} kcal", bold = true)
                GroupDivider()
                NutrientRow("Total fat", Units.grams(total.fat))
                total.saturatedFat?.let { NutrientRow("   Saturated fat", Units.grams(it), secondary = true) }
                total.cholesterolMg?.let { GroupDivider(); NutrientRow("Cholesterol", Units.mg(it)) }
                total.sodiumMg?.let { GroupDivider(); NutrientRow("Sodium", Units.mg(it)) }
                total.potassiumMg?.let { GroupDivider(); NutrientRow("Potassium", Units.mg(it)) }
                GroupDivider()
                NutrientRow("Total carbohydrates", Units.grams(total.carbs))
                total.fiber?.let { NutrientRow("   Dietary fibre", Units.grams(it), secondary = true) }
                total.sugar?.let { NutrientRow("   Sugars", Units.grams(it), secondary = true) }
                GroupDivider()
                NutrientRow("Protein", Units.grams(total.protein))
            }
        }
        if (l.food?.source == FoodSource.OPEN_FOOD_FACTS) {
            item {
                Text(
                    "Data from Open Food Facts (ODbL). Values may be incomplete.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OneUi.colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun MacroLegend(label: String, grams: Double, share: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("$label  ${(share * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = OneUi.colors.textSecondary)
            Text(Units.grams(grams), style = MaterialTheme.typography.titleMedium, color = OneUi.colors.textPrimary)
        }
    }
}

@Composable
fun MacroDonut(n: Nutrients, modifier: Modifier = Modifier) {
    val colors = OneUi.colors
    val (cp, pp, fp) = n.macroSplit()
    val anim by animateFloatAsState(1f, tween(600), label = "donut")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(132.dp)) {
            val sw = 14.dp.toPx()
            val d = size.minDimension - sw
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(colors.track, 0f, 360f, false, tl, Size(d, d), style = Stroke(sw))
            var start = -90f
            val gap = if (listOf(cp, pp, fp).count { it > 0 } > 1) 4f else 0f
            listOf(cp to colors.carbs, pp to colors.protein, fp to colors.fat).forEach { (share, color) ->
                if (share > 0f) {
                    val sweep = (360f * share * anim - gap).coerceAtLeast(0.5f)
                    drawArc(color, start + gap / 2, sweep, false, tl, Size(d, d), style = Stroke(sw))
                    start += 360f * share * anim
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Units.kcal(n.calories), style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
            Text("kcal", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

@Composable
fun NutrientRow(label: String, value: String, bold: Boolean = false, secondary: Boolean = false) {
    val colors = OneUi.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = if (secondary) 6.dp else 13.dp)) {
        Text(
            label,
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = if (secondary) colors.textSecondary else colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge, color = if (secondary) colors.textSecondary else colors.textPrimary)
    }
}
