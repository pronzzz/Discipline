package app.discipline.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.discipline.data.Settings
import app.discipline.domain.ActivityLevel
import app.discipline.domain.GoalCalculator
import app.discipline.domain.MacroSplit
import app.discipline.domain.Profile
import app.discipline.domain.Sex
import app.discipline.domain.UnitSystem
import app.discipline.domain.Units
import app.discipline.ui.LocalNavigator
import app.discipline.ui.components.BottomActionBar
import app.discipline.ui.components.Group
import app.discipline.ui.components.GroupDivider
import app.discipline.ui.components.ListRow
import app.discipline.ui.components.OneUiField
import app.discipline.ui.components.OneUiScreen
import app.discipline.ui.components.PillButton
import app.discipline.ui.components.RadioRow
import app.discipline.ui.components.Segmented
import app.discipline.ui.components.Stat
import app.discipline.ui.components.SwitchRow
import app.discipline.ui.theme.OneUi
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Editable copy of the profile fields as text, so partially typed numbers don't jump around. */
class ProfileDraft(s: Settings) {
    val units = s.units
    var sex by mutableStateOf(s.profile.sex)
    var birth by mutableStateOf(s.profile.birthDate)
    var height by mutableStateOf(
        if (units == UnitSystem.METRIC) s.profile.heightCm.roundToInt().toString() else (s.profile.heightCm / Units.CM_PER_IN).roundToInt().toString()
    )
    var weight by mutableStateOf(Units.number(Units.weightValue(s.profile.currentWeightKg, units)))
    var goalWeight by mutableStateOf(Units.number(Units.weightValue(s.profile.goalWeightKg, units)))
    var activity by mutableStateOf(s.profile.activity)
    var weekly by mutableStateOf(s.profile.weeklyChangeKg)

    fun toProfile(): Profile? {
        val h = height.replace(',', '.').toDoubleOrNull()?.let { if (units == UnitSystem.METRIC) it else it * Units.CM_PER_IN }?.takeIf { it in 100.0..250.0 } ?: return null
        val w = weight.replace(',', '.').toDoubleOrNull()?.let { Units.weightToKg(it, units) }?.takeIf { it in 30.0..350.0 } ?: return null
        val g = goalWeight.replace(',', '.').toDoubleOrNull()?.let { Units.weightToKg(it, units) }?.takeIf { it in 30.0..350.0 } ?: return null
        return Profile(sex, birth, h, w, g, activity, weekly)
    }
}

val WeeklyOptions = listOf(-1.0, -0.75, -0.5, -0.25, 0.0, 0.25, 0.5)

fun weeklyLabel(kg: Double, units: UnitSystem): String = when {
    kg == 0.0 -> "Maintain my weight"
    units == UnitSystem.IMPERIAL -> "${if (kg < 0) "Lose" else "Gain"} ${Units.number(kotlin.math.abs(kg) * 2)} lb per week"
    else -> "${if (kg < 0) "Lose" else "Gain"} ${Units.number(kotlin.math.abs(kg))} kg per week"
}

@Composable
fun GoalsScreen() {
    val c = appContainer()
    val nav = LocalNavigator.current
    val s = rememberSettings() ?: return
    val scope = rememberCoroutineScope()
    val colors = OneUi.colors
    val draft = remember { ProfileDraft(s) }
    var customOn by remember { mutableStateOf(s.customCalories != null) }
    var custom by remember { mutableStateOf((s.customCalories ?: s.goals().calories).toString()) }
    var carbs by remember { mutableStateOf(s.macros.carbsPct.toString()) }
    var protein by remember { mutableStateOf(s.macros.proteinPct.toString()) }
    var fat by remember { mutableStateOf(s.macros.fatPct.toString()) }
    var pickingBirth by remember { mutableStateOf(false) }

    val profile = draft.toProfile()
    val split = MacroSplit(carbs.toIntOrNull() ?: -1, protein.toIntOrNull() ?: -1, fat.toIntOrNull() ?: -1)
    val customKcal = custom.toIntOrNull()?.takeIf { it in 800..8000 }
    val valid = profile != null && split.isValid && (!customOn || customKcal != null)

    OneUiScreen(
        title = "Goals",
        subtitle = "Profile, calories and macros",
        onBack = { nav.pop() },
        headerFraction = 0.26f,
        bottomBar = {
            BottomActionBar {
                PillButton("Save", enabled = valid, modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch {
                        c.settings.saveProfile(profile!!)
                        c.settings.setMacros(split)
                        c.settings.setCustomCalories(if (customOn) customKcal else null)
                        nav.pop()
                    }
                })
            }
        },
    ) {
        // -------------------------------------------------------------- result
        item {
            Group(contentPadding = PaddingValues(20.dp)) {
                if (profile != null) {
                    val goal = if (customOn && customKcal != null) customKcal else GoalCalculator.calorieGoal(profile)
                    Row(Modifier.fillMaxWidth()) {
                        Stat(Units.kcal(goal), "Daily goal", Modifier.weight(1f), color = colors.accent)
                        Stat(Units.kcal(GoalCalculator.tdee(profile).roundToInt()), "Maintenance", Modifier.weight(1f))
                        Stat(Units.kcal(GoalCalculator.bmr(profile).roundToInt()), "BMR", Modifier.weight(1f))
                    }
                    GoalCalculator.projectedGoalDate(profile)?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "At this pace you'll reach ${Units.weight(profile.goalWeightKg, draft.units)} around ${it.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}.",
                            style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                        )
                    }
                } else {
                    Text("Check your height and weights.", style = MaterialTheme.typography.bodyMedium, color = colors.danger)
                }
            }
        }

        item { ProfileFields(draft, onPickBirth = { pickingBirth = true }) }

        item {
            Group(label = "Weekly goal") {
                WeeklyOptions.forEach { kg -> RadioRow(weeklyLabel(kg, draft.units), draft.weekly == kg, onClick = { draft.weekly = kg }) }
            }
        }
        item {
            Group(label = "Activity level") {
                ActivityLevel.entries.forEach { a -> RadioRow(a.label, draft.activity == a, onClick = { draft.activity = a }, summary = a.description) }
            }
        }

        // -------------------------------------------------------------- calories
        item {
            Group(label = "Calorie goal") {
                SwitchRow("Set my own calorie goal", customOn, { customOn = it }, summary = "Override the recommendation")
                if (customOn) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                        OneUiField(custom, { custom = it.filter(Char::isDigit).take(5) }, "Calories per day", suffix = "kcal", keyboardType = KeyboardType.Number, isError = customKcal == null)
                    }
                }
                profile?.let {
                    val min = GoalCalculator.minimumCalories(it.sex)
                    if (!customOn && GoalCalculator.tdee(it) + it.weeklyChangeKg * GoalCalculator.KCAL_PER_KG / 7 < min) {
                        GroupDivider()
                        ListRow("Goal held at $min kcal", summary = "Discipline won't recommend less than this. Choose a slower pace to lose safely.", summaryColor = colors.exercise)
                    }
                }
            }
        }

        // -------------------------------------------------------------- macros
        item {
            Group(label = "Macronutrients", contentPadding = PaddingValues(16.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Balanced" to Triple(50, 20, 30), "High protein" to Triple(40, 30, 30), "Low carb" to Triple(25, 35, 40), "Keto" to Triple(5, 25, 70))
                        .forEach { (name, t) ->
                            val sel = split == MacroSplit(t.first, t.second, t.third)
                            Text(
                                name,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (sel) androidx.compose.ui.graphics.Color.White else colors.textPrimary,
                                modifier = Modifier.clip(OneUi.PillShape).background(if (sel) colors.accent else colors.containerHigh)
                                    .clickable { carbs = t.first.toString(); protein = t.second.toString(); fat = t.third.toString() }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OneUiField(carbs, { carbs = it.filter(Char::isDigit).take(3) }, "Carbs", Modifier.weight(1f), suffix = "%", keyboardType = KeyboardType.Number)
                    OneUiField(protein, { protein = it.filter(Char::isDigit).take(3) }, "Protein", Modifier.weight(1f), suffix = "%", keyboardType = KeyboardType.Number)
                    OneUiField(fat, { fat = it.filter(Char::isDigit).take(3) }, "Fat", Modifier.weight(1f), suffix = "%", keyboardType = KeyboardType.Number)
                }
                val sum = (carbs.toIntOrNull() ?: 0) + (protein.toIntOrNull() ?: 0) + (fat.toIntOrNull() ?: 0)
                Text(
                    if (sum == 100) "Total 100%" else "Total $sum% — must add up to 100%",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sum == 100) colors.textSecondary else colors.danger,
                    modifier = Modifier.padding(top = 8.dp, start = 6.dp),
                )
            }
        }
    }

    if (pickingBirth) DayPicker(draft.birth, onDismiss = { pickingBirth = false }, onPicked = {
        pickingBirth = false
        if (it.isBefore(LocalDate.now().minusYears(12))) draft.birth = it
    })
}

@Composable
fun ProfileFields(draft: ProfileDraft, onPickBirth: () -> Unit, label: String? = "About you") {
    val colors = OneUi.colors
    Group(label = label, contentPadding = PaddingValues(16.dp)) {
        Segmented(listOf(Sex.FEMALE, Sex.MALE), draft.sex, { if (it == Sex.FEMALE) "Female" else "Male" }, { draft.sex = it })
        Spacer(Modifier.height(12.dp))
        ListRow(
            "Date of birth",
            summary = "${draft.birth.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))} · ${GoalCalculator.ageOn(draft.birth)} years",
            onClick = onPickBirth,
            modifier = Modifier.clip(OneUi.InnerShape).background(colors.containerHigh),
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OneUiField(draft.height, { draft.height = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) },
                if (draft.units == UnitSystem.METRIC) "Height" else "Height (inches)", suffix = if (draft.units == UnitSystem.METRIC) "cm" else "in", keyboardType = KeyboardType.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OneUiField(draft.weight, { draft.weight = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(6) }, "Current weight",
                    Modifier.weight(1f), suffix = Units.weightUnit(draft.units), keyboardType = KeyboardType.Decimal)
                OneUiField(draft.goalWeight, { draft.goalWeight = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(6) }, "Goal weight",
                    Modifier.weight(1f), suffix = Units.weightUnit(draft.units), keyboardType = KeyboardType.Decimal)
            }
        }
    }
}
