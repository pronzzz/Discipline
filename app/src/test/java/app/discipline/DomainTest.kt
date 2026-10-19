package app.discipline

import app.discipline.data.food.SearchKey
import app.discipline.domain.ActivityLevel
import app.discipline.domain.GoalCalculator
import app.discipline.domain.MacroSplit
import app.discipline.domain.Nutrients
import app.discipline.domain.Profile
import app.discipline.domain.Sex
import app.discipline.domain.sum
import app.discipline.ui.streak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DomainTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val male30 = Profile(
        sex = Sex.MALE, birthDate = LocalDate.of(1996, 1, 1), heightCm = 180.0,
        currentWeightKg = 80.0, goalWeightKg = 75.0, activity = ActivityLevel.SEDENTARY, weeklyChangeKg = -0.5,
    )

    @Test fun bmrMatchesMifflinStJeor() {
        // 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertEquals(1780.0, GoalCalculator.bmr(male30, today), 0.001)
        val female = male30.copy(sex = Sex.FEMALE)
        assertEquals(1614.0, GoalCalculator.bmr(female, today), 0.001)
    }

    @Test fun calorieGoalAppliesWeeklyDeficitAndRounds() {
        // TDEE 1780*1.2 = 2136; deficit 0.5*7700/7 = 550 -> 1586 -> 1590
        assertEquals(1590, GoalCalculator.calorieGoal(male30, today))
    }

    @Test fun calorieGoalNeverRecommendsBelowFloorWhenLosing() {
        val small = male30.copy(sex = Sex.FEMALE, currentWeightKg = 50.0, heightCm = 155.0, weeklyChangeKg = -1.0)
        assertEquals(1200, GoalCalculator.calorieGoal(small, today))
    }

    @Test fun macroGramsFollowSplit() {
        val g = GoalCalculator.goals(male30, MacroSplit(50, 20, 30), customCalories = 2000, waterMl = 2500, steps = 8000, today = today)
        assertEquals(250, g.carbsG)
        assertEquals(100, g.proteinG)
        assertEquals(67, g.fatG)
    }

    @Test fun projectedDateOnlyWhenMovingTowardsGoal() {
        val date = GoalCalculator.projectedGoalDate(male30, today)
        assertEquals(today.plusDays(70), date) // 5 kg at 0.5 kg/week = 10 weeks
        assertNull(GoalCalculator.projectedGoalDate(male30.copy(weeklyChangeKg = 0.5), today))
    }

    @Test fun stepCaloriesOnlyAboveBaseline() {
        assertEquals(0, GoalCalculator.stepCalories(4000, 70.0))
        assertEquals(200, GoalCalculator.stepCalories(10_000, 70.0))
    }

    @Test fun nutrientsScaleAndSumKeepingUnknowns() {
        val a = Nutrients(calories = 100.0, protein = 10.0, fiber = 2.0)
        val b = Nutrients(calories = 50.0, carbs = 5.0)
        val total = listOf(a * 2.0, b).sum()
        assertEquals(250.0, total.calories, 0.0)
        assertEquals(20.0, total.protein, 0.0)
        assertEquals(4.0, total.fiber!!, 0.0)
        assertNull(total.sugar)
    }

    @Test fun streakCountsBackFromTodayOrYesterday() {
        val d = today.toEpochDay()
        assertEquals(3, streak(listOf(d, d - 1, d - 2, d - 4), today))
        assertEquals(2, streak(listOf(d - 1, d - 2), today))
        assertEquals(0, streak(listOf(d - 3), today))
    }

    @Test fun searchKeyStripsAccentsAndPunctuation() {
        assertEquals("creme brulee la maison", SearchKey.of("Crème Brûlée!", "La-Maison"))
    }
}
