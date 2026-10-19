package app.discipline.domain

import java.time.LocalDate
import java.time.Period
import kotlin.math.roundToInt

enum class Sex { FEMALE, MALE }

enum class ActivityLevel(val factor: Double, val label: String, val description: String) {
    SEDENTARY(1.2, "Not very active", "Desk job, little walking"),
    LIGHT(1.375, "Lightly active", "On your feet for part of the day"),
    ACTIVE(1.55, "Active", "On your feet most of the day"),
    VERY_ACTIVE(1.725, "Very active", "Physically demanding work"),
}

enum class Meal(val label: String, val defaultHour: Int) {
    BREAKFAST("Breakfast", 8),
    LUNCH("Lunch", 13),
    DINNER("Dinner", 19),
    SNACKS("Snacks", 16);

    companion object {
        fun fromOrdinal(i: Int) = entries.getOrElse(i) { SNACKS }

        /** Best-guess meal for something logged right now. */
        fun forHour(hour: Int): Meal = when (hour) {
            in 4..10 -> BREAKFAST
            in 11..14 -> LUNCH
            in 17..21 -> DINNER
            else -> SNACKS
        }
    }
}

data class Profile(
    val sex: Sex = Sex.MALE,
    val birthDate: LocalDate = LocalDate.of(1995, 1, 1),
    val heightCm: Double = 175.0,
    val currentWeightKg: Double = 75.0,
    val goalWeightKg: Double = 70.0,
    val activity: ActivityLevel = ActivityLevel.LIGHT,
    /** Signed kg/week: negative to lose, positive to gain, 0 to maintain. */
    val weeklyChangeKg: Double = -0.5,
)

data class MacroSplit(val carbsPct: Int = 50, val proteinPct: Int = 20, val fatPct: Int = 30) {
    val isValid get() = carbsPct + proteinPct + fatPct == 100 && listOf(carbsPct, proteinPct, fatPct).all { it in 0..100 }
}

data class DailyGoals(
    val calories: Int,
    val carbsG: Int,
    val proteinG: Int,
    val fatG: Int,
    val waterMl: Int,
    val steps: Int,
    val bmr: Int,
    val tdee: Int,
)

object GoalCalculator {
    const val KCAL_PER_KG = 7700.0

    fun ageOn(birthDate: LocalDate, today: LocalDate = LocalDate.now()) =
        Period.between(birthDate, today).years.coerceAtLeast(13)

    /** Mifflin–St Jeor resting energy expenditure. */
    fun bmr(p: Profile, today: LocalDate = LocalDate.now()): Double {
        val base = 10 * p.currentWeightKg + 6.25 * p.heightCm - 5 * ageOn(p.birthDate, today)
        return if (p.sex == Sex.MALE) base + 5 else base - 161
    }

    fun tdee(p: Profile, today: LocalDate = LocalDate.now()) = bmr(p, today) * p.activity.factor

    fun minimumCalories(sex: Sex) = if (sex == Sex.FEMALE) 1200 else 1500

    fun calorieGoal(p: Profile, today: LocalDate = LocalDate.now()): Int {
        val delta = p.weeklyChangeKg * KCAL_PER_KG / 7.0
        val raw = tdee(p, today) + delta
        val floored = if (p.weeklyChangeKg < 0) raw.coerceAtLeast(minimumCalories(p.sex).toDouble()) else raw
        return roundTo10(floored)
    }

    fun goals(
        p: Profile,
        split: MacroSplit,
        customCalories: Int?,
        waterMl: Int,
        steps: Int,
        today: LocalDate = LocalDate.now(),
    ): DailyGoals {
        val kcal = customCalories ?: calorieGoal(p, today)
        return DailyGoals(
            calories = kcal,
            carbsG = (kcal * split.carbsPct / 100.0 / 4).roundToInt(),
            proteinG = (kcal * split.proteinPct / 100.0 / 4).roundToInt(),
            fatG = (kcal * split.fatPct / 100.0 / 9).roundToInt(),
            waterMl = waterMl,
            steps = steps,
            bmr = bmr(p, today).roundToInt(),
            tdee = tdee(p, today).roundToInt(),
        )
    }

    /** Projected date to reach goal weight at the current weekly rate, or null if moving away / maintaining. */
    fun projectedGoalDate(p: Profile, today: LocalDate = LocalDate.now()): LocalDate? {
        val diff = p.goalWeightKg - p.currentWeightKg
        if (p.weeklyChangeKg == 0.0 || diff == 0.0 || diff / p.weeklyChangeKg < 0) return null
        val weeks = diff / p.weeklyChangeKg
        return today.plusDays((weeks * 7).roundToInt().toLong())
    }

    /**
     * Calories credited from steps. Only steps beyond a 5,000/day baseline count (the baseline is
     * already covered by the activity factor). Roughly 0.04 kcal per step per 70 kg, scaled by weight.
     */
    fun stepCalories(steps: Long, weightKg: Double, baseline: Long = 5000): Int {
        val extra = (steps - baseline).coerceAtLeast(0)
        return (extra * 0.04 * (weightKg / 70.0)).roundToInt()
    }

    private fun roundTo10(v: Double) = ((v / 10.0).roundToInt() * 10)
}
