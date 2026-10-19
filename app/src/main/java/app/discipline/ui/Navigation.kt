package app.discipline.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class Tab { TODAY, DIARY, PROGRESS, SETTINGS }

@Serializable
sealed interface Route {
    @Serializable data class Home(val tab: Tab = Tab.TODAY) : Route
    @Serializable data object Onboarding : Route
    @Serializable data class Search(val day: Long, val meal: Int) : Route
    @Serializable data class FoodDetail(val foodId: Long, val day: Long, val meal: Int) : Route
    @Serializable data class EditEntry(val entryId: Long) : Route
    @Serializable data class CreateFood(val day: Long, val meal: Int, val barcode: String? = null, val editFoodId: Long? = null) : Route
    @Serializable data class QuickAdd(val day: Long, val meal: Int) : Route
    @Serializable data class Exercise(val day: Long, val exerciseId: Long? = null) : Route
    @Serializable data class Nutrition(val day: Long) : Route
    @Serializable data object Goals : Route
    @Serializable data object HealthConnect : Route
    @Serializable data object MealNames : Route
    @Serializable data object MyFoods : Route
    @Serializable data object About : Route
}

/** A tiny back stack. The bottom entry is always [Route.Home] (or onboarding on first run). */
class Navigator(private val stack: SnapshotStateList<Route>) {
    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** True when the most recent change moved backwards; drives the transition direction. */
    var lastWasPop by mutableStateOf(false)
        private set

    fun push(route: Route) { lastWasPop = false; stack.add(route) }
    fun replace(route: Route) { lastWasPop = false; stack[stack.lastIndex] = route }
    fun pop() { if (stack.size > 1) { lastWasPop = true; stack.removeAt(stack.lastIndex) } }
    fun resetTo(route: Route) { lastWasPop = false; stack.clear(); stack.add(route) }
    fun selectTab(tab: Tab) {
        lastWasPop = stack.size > 1
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        stack[0] = Route.Home(tab)
    }
}

private val json = Json { ignoreUnknownKeys = true }
private val listSerializer = ListSerializer(Route.serializer())

@Composable
fun rememberNavigator(start: Route): Navigator {
    val stack = rememberSaveable(
        saver = Saver<SnapshotStateList<Route>, String>(
            save = { json.encodeToString(listSerializer, it.toList()) },
            restore = { mutableStateListOf<Route>().apply { addAll(json.decodeFromString(listSerializer, it)) } },
        ),
    ) { mutableStateListOf(start) }
    return androidx.compose.runtime.remember(stack) { Navigator(stack) }
}
