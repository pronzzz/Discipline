package app.discipline.ui

import app.discipline.AppContainer
import app.discipline.data.Settings
import app.discipline.data.db.ExerciseEntity
import app.discipline.data.db.FoodEntryEntity
import app.discipline.data.db.HealthDayEntity
import app.discipline.data.db.HealthReadingEntity
import app.discipline.data.db.ImportedFoodEntity
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
    /** Food other apps logged; only stored when the user chose to count it. */
    val importedFood: List<ImportedFoodEntity> = emptyList(),
    /** Health Connect values for this day, one per type. */
    val readings: List<HealthReadingEntity> = emptyList(),
) {
    val food: Nutrients = (entries.map { it.nutrients } + importedFood.map { it.nutrients }).sum()
    val byMeal: Map<Int, List<FoodEntryEntity>> = entries.groupBy { it.meal }
    val importedByMeal: Map<Int, List<ImportedFoodEntity>> = importedFood.groupBy { it.meal }
    fun mealNutrients(meal: Int) = (byMeal[meal].orEmpty().map { it.nutrients } + importedByMeal[meal].orEmpty().map { it.nutrients }).sum()
    fun mealCalories(meal: Int) = mealNutrients(meal).calories

    val ownWaterMl = water.sumOf { it.ml }
    val otherWaterMl = if (settings.health.countOtherWater) health?.otherWaterMl ?: 0 else 0
    val waterMl = ownWaterMl + otherWaterMl
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
    val hc = combine(c.diary.healthDay(day), c.diary.importedFood(day), c.diary.readingsForDay(day)) { h, f, r -> Triple(h, f, r) }
    combine(base, hc, c.diary.loggedDays(LocalDate.now())) { b, (h, food, readings), logged ->
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
            importedFood = food,
            readings = readings,
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
