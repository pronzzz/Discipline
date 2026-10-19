package app.discipline.ui

import app.discipline.AppContainer
import app.discipline.data.Settings
import app.discipline.data.db.ExerciseEntity
import app.discipline.data.db.FoodEntryEntity
import app.discipline.data.db.HealthDayEntity
import app.discipline.data.db.ImportedExerciseEntity
import app.discipline.data.db.WaterEntity
import app.discipline.domain.DailyGoals
import app.discipline.domain.GoalCalculator
import app.discipline.domain.Nutrients
import app.discipline.domain.sum
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate

data class DayUi(
    val day: LocalDate,
    val settings: Settings,
    val goals: DailyGoals,
    val entries: List<FoodEntryEntity>,
    val exercise: List<ExerciseEntity>,
    val imported: List<ImportedExerciseEntity>,
    val water: List<WaterEntity>,
    val health: HealthDayEntity?,
    val streak: Int,
) {
    val food: Nutrients = entries.map { it.nutrients }.sum()
    val byMeal: Map<Int, List<FoodEntryEntity>> = entries.groupBy { it.meal }
    fun mealCalories(meal: Int) = byMeal[meal].orEmpty().sumOf { it.nutrients.calories }

    val waterMl = water.sumOf { it.ml }
    val steps = health?.steps ?: 0L
    val manualExerciseKcal = exercise.sumOf { it.calories }
    val importedExerciseKcal = imported.sumOf { it.calories ?: 0 }
    val exerciseMinutes = exercise.sumOf { it.minutes } + imported.sumOf { it.minutes }
    val stepKcal = if (settings.addStepCalories) GoalCalculator.stepCalories(steps, settings.profile.currentWeightKg) else 0
    val exerciseCredit: Int = if (settings.addExerciseCalories) manualExerciseKcal + importedExerciseKcal + stepKcal else 0
    val remaining: Int = (goals.calories - food.calories + exerciseCredit).toInt()
    val calorieProgress: Float = if (goals.calories + exerciseCredit <= 0) 0f else (food.calories / (goals.calories + exerciseCredit)).toFloat()
}

@OptIn(ExperimentalCoroutinesApi::class)
fun dayUiFlow(c: AppContainer, dayFlow: Flow<LocalDate>): Flow<DayUi> = dayFlow.flatMapLatest { day ->
    val base = combine(
        c.settings.settings,
        c.diary.entries(day),
        c.diary.exercise(day),
        c.diary.importedExercise(day),
        c.diary.water(day),
    ) { s, entries, ex, imp, water -> Base(s, entries, ex, imp, water) }
    combine(base, c.diary.healthDay(day), c.diary.loggedDays(LocalDate.now())) { b, h, logged ->
        DayUi(
            day = day,
            settings = b.settings,
            goals = b.settings.goals(day),
            entries = b.entries,
            exercise = b.exercise,
            imported = b.imported,
            water = b.water,
            health = h,
            streak = streak(logged, LocalDate.now()),
        )
    }
}

private data class Base(
    val settings: Settings,
    val entries: List<FoodEntryEntity>,
    val exercise: List<ExerciseEntity>,
    val imported: List<ImportedExerciseEntity>,
    val water: List<WaterEntity>,
)

/** Consecutive logged days ending today, or ending yesterday if today has no entries yet. */
fun streak(loggedDaysDesc: List<Long>, today: LocalDate): Int {
    val set = loggedDaysDesc.toHashSet()
    var d = today.toEpochDay()
    if (d !in set) d -= 1
    var n = 0
    while (d in set) { n++; d-- }
    return n
}
