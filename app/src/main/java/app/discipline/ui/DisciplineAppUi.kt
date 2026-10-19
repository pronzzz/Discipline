package app.discipline.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.discipline.data.Settings
import app.discipline.ui.screens.AboutScreen
import app.discipline.ui.screens.CreateFoodScreen
import app.discipline.ui.screens.DiaryScreen
import app.discipline.ui.screens.ExerciseScreen
import app.discipline.ui.screens.FoodDetailScreen
import app.discipline.ui.screens.GoalsScreen
import app.discipline.ui.screens.HealthConnectScreen
import app.discipline.ui.screens.MealNamesScreen
import app.discipline.ui.screens.MyFoodsScreen
import app.discipline.ui.screens.NutritionScreen
import app.discipline.ui.screens.OnboardingScreen
import app.discipline.ui.screens.ProgressScreen
import app.discipline.ui.screens.QuickAddScreen
import app.discipline.ui.screens.SearchScreen
import app.discipline.ui.screens.SettingsScreen
import app.discipline.ui.screens.TodayScreen
import app.discipline.ui.theme.OneUi
import java.time.LocalDate

val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("no navigator") }

@Composable
fun DisciplineAppUi(settings: Settings) {
    val nav = rememberNavigator(if (settings.onboarded) Route.Home() else Route.Onboarding)
    val snackbar = remember { SnackbarHostState() }
    // The diary's selected day survives tab switches.
    var diaryDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }

    BackHandler(enabled = nav.canGoBack) { nav.pop() }

    CompositionLocalProvider(LocalSnackbar provides snackbar, LocalNavigator provides nav) {
        Box(Modifier.fillMaxSize().background(OneUi.colors.background)) {
            AnimatedContent(
                targetState = nav.current,
                transitionSpec = {
                    if (targetState is Route.Home && initialState is Route.Home) {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                    } else if (!nav.lastWasPop) {
                        (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(220))) togetherWith fadeOut(tween(160))
                    } else {
                        fadeIn(tween(220)) togetherWith (slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(200)))
                    }
                },
                label = "route",
            ) { route ->
                when (route) {
                    is Route.Home -> HomeShell(route.tab, diaryDay, onDiaryDay = { diaryDay = it })
                    Route.Onboarding -> OnboardingScreen(onDone = { nav.resetTo(Route.Home()) })
                    is Route.Search -> SearchScreen(route)
                    is Route.FoodDetail -> FoodDetailScreen(foodId = route.foodId, entryId = null, day = route.day, meal = route.meal)
                    is Route.EditEntry -> FoodDetailScreen(foodId = null, entryId = route.entryId, day = null, meal = null)
                    is Route.CreateFood -> CreateFoodScreen(route)
                    is Route.QuickAdd -> QuickAddScreen(route)
                    is Route.Exercise -> ExerciseScreen(route)
                    is Route.Nutrition -> NutritionScreen(LocalDate.ofEpochDay(route.day))
                    Route.Goals -> GoalsScreen()
                    Route.HealthConnect -> HealthConnectScreen()
                    Route.MealNames -> MealNamesScreen()
                    Route.MyFoods -> MyFoodsScreen()
                    Route.About -> AboutScreen()
                }
            }
            SnackbarHost(
                snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = if (nav.current is Route.Home) 84.dp else 12.dp, start = 12.dp, end = 12.dp),
            ) { data ->
                Snackbar(
                    data,
                    shape = OneUi.PillShape,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = OneUi.colors.accent,
                )
            }
        }
    }
}

private data class TabSpec(val tab: Tab, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    TabSpec(Tab.TODAY, "Today", Icons.Outlined.Home, Icons.Rounded.Home),
    TabSpec(Tab.DIARY, "Diary", Icons.Outlined.Book, Icons.Rounded.Book),
    TabSpec(Tab.PROGRESS, "Progress", Icons.Outlined.BarChart, Icons.Rounded.BarChart),
    TabSpec(Tab.SETTINGS, "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

@Composable
private fun HomeShell(tab: Tab, diaryDay: Long, onDiaryDay: (Long) -> Unit) {
    val nav = LocalNavigator.current
    val haptics = LocalHapticFeedback.current
    val c = OneUi.colors
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                Tab.TODAY -> TodayScreen(onOpenDiary = { onDiaryDay(LocalDate.now().toEpochDay()); nav.selectTab(Tab.DIARY) })
                Tab.DIARY -> DiaryScreen(LocalDate.ofEpochDay(diaryDay), onDayChange = { onDiaryDay(it.toEpochDay()) })
                Tab.PROGRESS -> ProgressScreen()
                Tab.SETTINGS -> SettingsScreen()
            }
        }
        NavigationBar(
            containerColor = c.container,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            tabs.forEach { spec ->
                val selected = spec.tab == tab
                NavigationBarItem(
                    selected = selected,
                    onClick = { if (!selected) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); nav.selectTab(spec.tab) } },
                    icon = { Icon(if (selected) spec.selectedIcon else spec.icon, contentDescription = spec.label) },
                    label = { Text(spec.label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = c.accent,
                        selectedTextColor = c.accent,
                        indicatorColor = c.accentContainer,
                        unselectedIconColor = c.textSecondary,
                        unselectedTextColor = c.textSecondary,
                    ),
                )
            }
        }
    }
}
