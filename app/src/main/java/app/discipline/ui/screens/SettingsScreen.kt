package app.discipline.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.discipline.AppContainer
import app.discipline.BuildConfig
import app.discipline.data.ThemeMode
import app.discipline.domain.UnitSystem
import app.discipline.domain.Units
import app.discipline.health.HealthAvailability
import app.discipline.ui.LocalNavigator
import app.discipline.ui.LocalSnackbar
import app.discipline.ui.Route
import app.discipline.ui.components.AccentTextButton
import app.discipline.ui.components.Group
import app.discipline.ui.components.GroupDivider
import app.discipline.ui.components.IconBadge
import app.discipline.ui.components.ListRow
import app.discipline.ui.components.OneUiField
import app.discipline.ui.components.OneUiScreen
import app.discipline.ui.components.RadioRow
import app.discipline.ui.components.SwitchRow
import app.discipline.ui.theme.OneUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SettingsScreen() {
    val c = appContainer()
    val s = rememberSettings() ?: return
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = OneUi.colors
    val hc by c.health.status.collectAsStateWithLifecycle()
    val goals = s.goals()

    var dialog by remember { mutableStateOf<String?>(null) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching { exportCsv(context, c, uri) }.isSuccess
            snackbar.showSnackbar(if (ok) "Diary exported" else "Export failed")
        }
    }

    OneUiScreen(title = "Settings", padNavigationBar = false) {
        item {
            // Plan card
            Group {
                Row(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.TrackChanges, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        val rate = s.profile.weeklyChangeKg
                        Text(
                            when {
                                rate < 0 -> "Lose ${Units.weight(abs(rate), s.units)} per week"
                                rate > 0 -> "Gain ${Units.weight(rate, s.units)} per week"
                                else -> "Maintain weight"
                            },
                            style = MaterialTheme.typography.titleMedium, color = colors.textPrimary,
                        )
                        Text("${Units.kcal(goals.calories)} kcal · ${goals.carbsG}C / ${goals.proteinG}P / ${goals.fatG}F", style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    }
                }
                GroupDivider(0.dp)
                ListRow("Goals and profile", onClick = { nav.push(Route.Goals) }, trailing = { Chevron() })
            }
        }

        item {
            Group(label = "Daily targets") {
                ListRow("Water", summary = Units.water(s.waterGoalMl, s.units), leading = { IconBadge(Icons.Rounded.WaterDrop, colors.water) }, onClick = { dialog = "water" })
                GroupDivider(76.dp)
                ListRow("Steps", summary = Units.integer(s.stepGoal.toLong()), leading = { IconBadge(Icons.AutoMirrored.Rounded.DirectionsWalk, colors.steps) }, onClick = { dialog = "steps" })
            }
        }

        item {
            Group(label = "Connected apps") {
                ListRow(
                    "Health Connect",
                    summary = when {
                        hc.availability == HealthAvailability.UNAVAILABLE -> "Not available on this device"
                        hc.availability == HealthAvailability.UPDATE_REQUIRED -> "Update required"
                        hc.connected -> "Connected"
                        else -> "Not connected"
                    },
                    summaryColor = if (hc.connected) colors.success else null,
                    leading = { IconBadge(Icons.Rounded.Favorite, colors.danger) },
                    onClick = { nav.push(Route.HealthConnect) },
                    trailing = { Chevron() },
                )
            }
        }

        item {
            Group(label = "Diary") {
                ListRow("Meal names", summary = s.mealNames.joinToString(", "), leading = { IconBadge(Icons.Rounded.Restaurant, colors.calories) }, onClick = { nav.push(Route.MealNames) })
                GroupDivider(76.dp)
                ListRow("My foods", summary = "Foods you created", leading = { IconBadge(Icons.Rounded.Fastfood, colors.protein) }, onClick = { nav.push(Route.MyFoods) })
                GroupDivider(76.dp)
                SwitchRow(
                    "Add exercise calories", s.addExerciseCalories, { scope.launch { c.settings.setAddExerciseCalories(it) } },
                    summary = "Earn back calories you burn", leading = { IconBadge(Icons.Rounded.LocalFireDepartment, colors.exercise) },
                )
                GroupDivider(76.dp)
                SwitchRow(
                    "Step adjustment", s.addStepCalories, { scope.launch { c.settings.setAddStepCalories(it) } },
                    summary = "Credit steps above 5,000 a day", leading = { IconBadge(Icons.AutoMirrored.Rounded.DirectionsWalk, colors.steps) },
                )
            }
        }

        item {
            Group(label = "Display") {
                ListRow(
                    "Theme",
                    summary = when (s.theme) { ThemeMode.SYSTEM -> "Follow system"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" },
                    leading = { IconBadge(Icons.Rounded.DarkMode, Color(0xFF5B5BD6)) }, onClick = { dialog = "theme" },
                )
                GroupDivider(76.dp)
                ListRow(
                    "Units",
                    summary = if (s.units == UnitSystem.METRIC) "Metric (kg, ml, cm)" else "Imperial (lb, fl oz, ft)",
                    leading = { IconBadge(Icons.Rounded.Straighten, Color(0xFF0EA5E9)) }, onClick = { dialog = "units" },
                )
                GroupDivider(76.dp)
                SwitchRow("Vibration", s.haptics, { scope.launch { c.settings.setHaptics(it) } }, leading = { IconBadge(Icons.Rounded.Vibration, Color(0xFF64748B)) })
            }
        }

        item {
            Group(label = "Your data") {
                ListRow("Export diary", summary = "Save food, exercise and weight as CSV", leading = { IconBadge(Icons.Rounded.FileDownload, colors.success) },
                    onClick = { exporter.launch("discipline-export-${LocalDate.now()}.csv") })
                GroupDivider(76.dp)
                ListRow("Delete all data", summary = "Erase everything on this device", leading = { IconBadge(Icons.Rounded.DeleteForever, colors.danger) },
                    onClick = { dialog = "delete" })
            }
        }

        item {
            Group {
                ListRow("About Discipline", summary = "Version ${BuildConfig.VERSION_NAME}", leading = { IconBadge(Icons.Rounded.Info, colors.textSecondary) },
                    onClick = { nav.push(Route.About) }, trailing = { Chevron() })
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }

    when (dialog) {
        "water" -> NumberDialog("Daily water goal", waterGoalValue(s.waterGoalMl, s.units), if (s.units == UnitSystem.METRIC) "ml" else "fl oz",
            onDismiss = { dialog = null }) { v ->
            dialog = null
            val ml = if (s.units == UnitSystem.METRIC) v.roundToInt() else (v * Units.ML_PER_FL_OZ).roundToInt()
            scope.launch { c.settings.setWaterGoal(ml.coerceIn(250, 10_000)) }
        }
        "steps" -> NumberDialog("Daily step goal", s.stepGoal.toDouble(), "steps", onDismiss = { dialog = null }) { v ->
            dialog = null; scope.launch { c.settings.setStepGoal(v.roundToInt().coerceIn(500, 100_000)) }
        }
        "theme" -> ChoiceDialog("Theme", listOf(ThemeMode.SYSTEM to "Follow system", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"), s.theme,
            onDismiss = { dialog = null }) { dialog = null; scope.launch { c.settings.setTheme(it) } }
        "units" -> ChoiceDialog("Units", listOf(UnitSystem.METRIC to "Metric", UnitSystem.IMPERIAL to "Imperial"), s.units,
            onDismiss = { dialog = null }) { dialog = null; scope.launch { c.settings.setUnits(it) } }
        "delete" -> AlertDialog(
            onDismissRequest = { dialog = null },
            shape = OneUi.ContainerShape,
            containerColor = colors.container,
            title = { Text("Delete all data?") },
            text = { Text("Your diary, foods, weights and settings will be erased from this device. Records Discipline shared with Health Connect will also be removed.") },
            confirmButton = {
                AccentTextButton("Delete", color = colors.danger, onClick = {
                    dialog = null
                    c.scope.launch {
                        runCatching { c.health.deleteAllOwnData() }
                        withContext(Dispatchers.IO) { c.db.clearAllTables() }
                        c.settings.clearAll()
                        c.foods.seedIfNeeded()
                    }
                    nav.resetTo(Route.Onboarding)
                })
            },
            dismissButton = { AccentTextButton("Cancel", onClick = { dialog = null }, color = colors.textSecondary) },
        )
    }
}

@Composable
fun Chevron() = Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = OneUi.colors.textTertiary)

private fun waterGoalValue(ml: Int, u: UnitSystem): Double = if (u == UnitSystem.METRIC) ml.toDouble() else (ml / Units.ML_PER_FL_OZ).roundToInt().toDouble()

@Composable
fun NumberDialog(title: String, initial: Double, suffix: String, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial.fmt()) }
    val v = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = OneUi.ContainerShape,
        containerColor = OneUi.colors.container,
        title = { Text(title) },
        text = { OneUiField(text, { text = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(7) }, title, suffix = suffix, keyboardType = KeyboardType.Decimal, isError = v == null) },
        confirmButton = { AccentTextButton("Save", onClick = { v?.let(onSave) }) },
        dismissButton = { AccentTextButton("Cancel", onClick = onDismiss, color = OneUi.colors.textSecondary) },
    )
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = OneUi.ContainerShape,
        containerColor = OneUi.colors.container,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                options.forEach { (value, label) -> RadioRow(label, value == selected, onClick = { onPick(value) }) }
            }
        },
        confirmButton = {},
        dismissButton = { AccentTextButton("Cancel", onClick = onDismiss, color = OneUi.colors.textSecondary) },
    )
}

private suspend fun exportCsv(context: Context, c: AppContainer, uri: Uri) = withContext(Dispatchers.IO) {
    val entries = c.db.diary().allEntries()
    val weights = c.db.diary().allWeights()
    val s = c.settings.current()
    fun q(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
    fun d(v: Double?) = v?.let { "%.2f".format(java.util.Locale.US, it) } ?: ""
    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { w ->
        w.appendLine("type,date,meal,name,brand,servings,serving,calories,protein_g,carbs_g,fat_g,fiber_g,sugar_g,sat_fat_g,sodium_mg,cholesterol_mg,potassium_mg,weight_kg")
        entries.forEach { e ->
            val n = e.nutrients
            w.appendLine(listOf(
                "food", LocalDate.ofEpochDay(e.epochDay).toString(), q(s.mealName(e.meal)), q(e.name), q(e.brand.orEmpty()),
                d(e.servings), q(e.servingLabel), d(n.calories), d(n.protein), d(n.carbs), d(n.fat), d(n.fiber), d(n.sugar),
                d(n.saturatedFat), d(n.sodiumMg), d(n.cholesterolMg), d(n.potassiumMg), "",
            ).joinToString(","))
        }
        weights.forEach { wt ->
            w.appendLine("weight,${LocalDate.ofEpochDay(wt.epochDay)}" + ",".repeat(16) + d(wt.kg))
        }
    } ?: error("Could not open file")
}
