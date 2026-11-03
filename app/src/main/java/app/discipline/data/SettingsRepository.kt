package app.discipline.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.discipline.domain.ActivityLevel
import app.discipline.health.HealthToggles
import app.discipline.health.HealthCategory
import app.discipline.domain.DailyGoals
import app.discipline.domain.GoalCalculator
import app.discipline.domain.MacroSplit
import app.discipline.domain.Meal
import app.discipline.domain.Profile
import app.discipline.domain.Sex
import app.discipline.domain.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val onboarded: Boolean = false,
    val profile: Profile = Profile(),
    val macros: MacroSplit = MacroSplit(),
    val customCalories: Int? = null,
    val waterGoalMl: Int = 2500,
    val stepGoal: Int = 10_000,
    val units: UnitSystem = UnitSystem.METRIC,
    val addExerciseCalories: Boolean = true,
    val addStepCalories: Boolean = true,
    val mealNames: List<String> = Meal.entries.map { it.label },
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val haptics: Boolean = true,
    val health: HealthToggles = HealthToggles(),
    val healthDenials: Int = 0,
    val lastHealthSync: Long = 0,
    val autoCheckUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0,
    val skippedUpdate: String? = null,
) {
    fun goals(today: LocalDate = LocalDate.now()): DailyGoals =
        GoalCalculator.goals(profile, macros, customCalories, waterGoalMl, stepGoal, today)

    fun mealName(meal: Int) = mealNames.getOrElse(meal) { Meal.fromOrdinal(meal).label }
}

class SettingsRepository(private val context: Context) {

    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val sex = stringPreferencesKey("sex")
        val birth = longPreferencesKey("birth_epoch_day")
        val height = doublePreferencesKey("height_cm")
        val weight = doublePreferencesKey("weight_kg")
        val goalWeight = doublePreferencesKey("goal_weight_kg")
        val activity = stringPreferencesKey("activity")
        val weekly = doublePreferencesKey("weekly_change_kg")
        val carbs = intPreferencesKey("macro_carbs")
        val protein = intPreferencesKey("macro_protein")
        val fat = intPreferencesKey("macro_fat")
        val customCalories = intPreferencesKey("custom_calories")
        val water = intPreferencesKey("water_goal_ml")
        val steps = intPreferencesKey("step_goal")
        val units = stringPreferencesKey("units")
        val addExercise = booleanPreferencesKey("add_exercise_calories")
        val addSteps = booleanPreferencesKey("add_step_calories")
        val meals = stringPreferencesKey("meal_names")
        val theme = stringPreferencesKey("theme")
        val haptics = booleanPreferencesKey("haptics")
        val hcDenials = intPreferencesKey("hc_denials")
        val hcLastSync = longPreferencesKey("hc_last_sync")
        val hcOtherFood = booleanPreferencesKey("hc_count_other_food")
        val hcOtherWater = booleanPreferencesKey("hc_count_other_water")
        val autoUpdates = booleanPreferencesKey("auto_check_updates")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val skippedUpdate = stringPreferencesKey("skipped_update")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            onboarded = p[K.onboarded] ?: false,
            profile = Profile(
                sex = p[K.sex]?.let { runCatching { Sex.valueOf(it) }.getOrNull() } ?: d.profile.sex,
                birthDate = p[K.birth]?.let(LocalDate::ofEpochDay) ?: d.profile.birthDate,
                heightCm = p[K.height] ?: d.profile.heightCm,
                currentWeightKg = p[K.weight] ?: d.profile.currentWeightKg,
                goalWeightKg = p[K.goalWeight] ?: d.profile.goalWeightKg,
                activity = p[K.activity]?.let { runCatching { ActivityLevel.valueOf(it) }.getOrNull() } ?: d.profile.activity,
                weeklyChangeKg = p[K.weekly] ?: d.profile.weeklyChangeKg,
            ),
            macros = MacroSplit(p[K.carbs] ?: 50, p[K.protein] ?: 20, p[K.fat] ?: 30),
            customCalories = p[K.customCalories]?.takeIf { it > 0 },
            waterGoalMl = p[K.water] ?: d.waterGoalMl,
            stepGoal = p[K.steps] ?: d.stepGoal,
            units = p[K.units]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: d.units,
            addExerciseCalories = p[K.addExercise] ?: true,
            addStepCalories = p[K.addSteps] ?: true,
            mealNames = p[K.meals]?.split('\u001F')?.takeIf { it.size == 4 } ?: d.mealNames,
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            haptics = p[K.haptics] ?: true,
            health = HealthToggles(
                read = HealthCategory.entries.filterTo(mutableSetOf()) { p[readKey(it)] ?: true },
                write = HealthCategory.entries.filterTo(mutableSetOf()) { p[writeKey(it)] ?: true },
                countOtherFood = p[K.hcOtherFood] ?: false,
                countOtherWater = p[K.hcOtherWater] ?: true,
            ),
            healthDenials = p[K.hcDenials] ?: 0,
            lastHealthSync = p[K.hcLastSync] ?: 0,
            autoCheckUpdates = p[K.autoUpdates] ?: true,
            lastUpdateCheck = p[K.lastUpdateCheck] ?: 0,
            skippedUpdate = p[K.skippedUpdate],
        )
    }.distinctUntilChanged()

    suspend fun current(): Settings = settings.first()

    suspend fun saveProfile(p: Profile) = context.dataStore.edit {
        it[K.sex] = p.sex.name
        it[K.birth] = p.birthDate.toEpochDay()
        it[K.height] = p.heightCm
        it[K.weight] = p.currentWeightKg
        it[K.goalWeight] = p.goalWeightKg
        it[K.activity] = p.activity.name
        it[K.weekly] = p.weeklyChangeKg
    }

    suspend fun setCurrentWeight(kg: Double) = context.dataStore.edit { it[K.weight] = kg }
    suspend fun setOnboarded() = context.dataStore.edit { it[K.onboarded] = true }

    suspend fun setMacros(m: MacroSplit) = context.dataStore.edit {
        it[K.carbs] = m.carbsPct; it[K.protein] = m.proteinPct; it[K.fat] = m.fatPct
    }

    suspend fun setCustomCalories(kcal: Int?) = context.dataStore.edit {
        if (kcal == null) it.remove(K.customCalories) else it[K.customCalories] = kcal
    }

    suspend fun setWaterGoal(ml: Int) = context.dataStore.edit { it[K.water] = ml }
    suspend fun setStepGoal(steps: Int) = context.dataStore.edit { it[K.steps] = steps }
    suspend fun setUnits(u: UnitSystem) = context.dataStore.edit { it[K.units] = u.name }
    suspend fun setAddExerciseCalories(v: Boolean) = context.dataStore.edit { it[K.addExercise] = v }
    suspend fun setAddStepCalories(v: Boolean) = context.dataStore.edit { it[K.addSteps] = v }
    suspend fun setTheme(t: ThemeMode) = context.dataStore.edit { it[K.theme] = t.name }
    suspend fun setHaptics(v: Boolean) = context.dataStore.edit { it[K.haptics] = v }
    suspend fun setMealNames(names: List<String>) = context.dataStore.edit {
        it[K.meals] = names.joinToString("\u001F")
    }

    suspend fun setHealthToggles(t: HealthToggles) = context.dataStore.edit { p ->
        HealthCategory.entries.forEach {
            p[readKey(it)] = it in t.read
            p[writeKey(it)] = it in t.write
        }
        p[K.hcOtherFood] = t.countOtherFood
        p[K.hcOtherWater] = t.countOtherWater
    }

    private fun readKey(c: HealthCategory) = booleanPreferencesKey("hc_read_${c.name.lowercase()}")
    private fun writeKey(c: HealthCategory) = booleanPreferencesKey("hc_write_${c.name.lowercase()}")

    suspend fun setHeight(cm: Double) = context.dataStore.edit { it[K.height] = cm }

    suspend fun setAutoCheckUpdates(v: Boolean) = context.dataStore.edit { it[K.autoUpdates] = v }
    suspend fun setLastUpdateCheck(ms: Long) = context.dataStore.edit { it[K.lastUpdateCheck] = ms }
    suspend fun setSkippedUpdate(version: String?) = context.dataStore.edit {
        if (version == null) it.remove(K.skippedUpdate) else it[K.skippedUpdate] = version
    }

    suspend fun recordHealthDenial(reset: Boolean = false) = context.dataStore.edit {
        it[K.hcDenials] = if (reset) 0 else (it[K.hcDenials] ?: 0) + 1
    }

    suspend fun setLastHealthSync(ms: Long) = context.dataStore.edit { it[K.hcLastSync] = ms }

    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
