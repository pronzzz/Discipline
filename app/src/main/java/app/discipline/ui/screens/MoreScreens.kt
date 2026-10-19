package app.discipline.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.discipline.BuildConfig
import app.discipline.domain.Meal
import app.discipline.domain.Units
import app.discipline.ui.LocalNavigator
import app.discipline.ui.Route
import app.discipline.ui.components.AccentTextButton
import app.discipline.ui.components.BottomActionBar
import app.discipline.ui.components.EmptyHint
import app.discipline.ui.components.Group
import app.discipline.ui.components.GroupDivider
import app.discipline.ui.components.ListRow
import app.discipline.ui.components.OneUiField
import app.discipline.ui.components.OneUiScreen
import app.discipline.ui.components.PillButton
import app.discipline.ui.theme.OneUi
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun MealNamesScreen() {
    val c = appContainer()
    val nav = LocalNavigator.current
    val s = rememberSettings() ?: return
    val scope = rememberCoroutineScope()
    val names = remember { mutableStateListOf(*s.mealNames.toTypedArray()) }
    val valid = names.all { it.isNotBlank() }

    OneUiScreen(
        title = "Meal names",
        onBack = { nav.pop() },
        headerFraction = 0.24f,
        bottomBar = {
            BottomActionBar {
                PillButton("Save", enabled = valid, modifier = Modifier.fillMaxWidth(), onClick = {
                    scope.launch { c.settings.setMealNames(names.map { it.trim() }); nav.pop() }
                })
            }
        },
    ) {
        item {
            Group(contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    names.forEachIndexed { i, n ->
                        OneUiField(n, { names[i] = it.take(24) }, "Meal ${i + 1}", isError = n.isBlank())
                    }
                    AccentTextButton("Reset to defaults", onClick = {
                        Meal.entries.forEachIndexed { i, m -> names[i] = m.label }
                    })
                }
            }
        }
        item {
            Text(
                "Health Connect still receives the standard meal type (breakfast, lunch, dinner, snack) for each slot.",
                style = MaterialTheme.typography.bodySmall, color = OneUi.colors.textTertiary,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
fun MyFoodsScreen() {
    val c = appContainer()
    val nav = LocalNavigator.current
    val foods by c.foods.userFoods.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = LocalDate.now().toEpochDay()

    OneUiScreen(
        title = "My foods",
        subtitle = "${foods.size} saved",
        onBack = { nav.pop() },
        headerFraction = 0.26f,
        bottomBar = {
            BottomActionBar {
                PillButton("Create food", modifier = Modifier.fillMaxWidth(), onClick = { nav.push(Route.CreateFood(today, 0)) })
            }
        },
    ) {
        item {
            Group {
                if (foods.isEmpty()) EmptyHint("Foods you create appear here.")
                foods.forEachIndexed { i, f ->
                    if (i > 0) GroupDivider()
                    ListRow(
                        title = f.name,
                        summary = listOfNotNull(f.brand, f.servingLabel, "${Units.kcal(f.nutrients.calories)} kcal").joinToString(" · "),
                        onClick = { nav.push(Route.CreateFood(today, 0, editFoodId = f.id)) },
                    )
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
    val nav = LocalNavigator.current
    OneUiScreen(title = "Discipline", subtitle = "Version ${BuildConfig.VERSION_NAME}", onBack = { nav.pop() }, headerFraction = 0.30f) {
        item {
            Group {
                ListRow("Private by design", summary = "No account, ads or analytics. Your diary lives in a local database on this phone.")
                GroupDivider()
                ListRow("Food data", summary = "Generic foods are bundled reference values. Packaged foods come from Open Food Facts, available under the Open Database License (ODbL).")
                GroupDivider()
                ListRow("Health Connect", summary = "Reads and writes only the data types you allow. Revoke access at any time in Health Connect.")
                GroupDivider()
                ListRow("Independent project", summary = "Built from public Android and Health Connect documentation. Not affiliated with any other calorie-tracking service.")
            }
        }
        item {
            Text(
                "Calorie and nutrient estimates are for general wellness and are not medical advice.",
                style = MaterialTheme.typography.bodySmall, color = OneUi.colors.textTertiary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp),
            )
        }
    }
}
