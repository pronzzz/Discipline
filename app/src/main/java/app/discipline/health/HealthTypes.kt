package app.discipline.health

import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.CervicalMucusRecord
import androidx.health.connect.client.records.CyclingPedalingCadenceRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ElevationGainedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.IntermenstrualBleedingRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MenstruationFlowRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.MindfulnessSessionRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OvulationTestRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SexualActivityRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.SpeedRecord
import androidx.health.connect.client.records.StepsCadenceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import kotlin.reflect.KClass

/** The groups Health Connect uses in its own permission screen. */
enum class HealthCategory(val label: String) {
    ACTIVITY("Activity"),
    BODY("Body measurements"),
    CYCLE("Cycle tracking"),
    NUTRITION("Nutrition"),
    SLEEP("Sleep"),
    VITALS("Vitals"),
    WELLNESS("Wellness"),
}

/** How a data type is entered in the "Log health data" form. */
enum class EntryKind {
    /** A total over a time span (steps, distance, calories). */
    AMOUNT,
    /** One value at a moment (body fat, VO2 max). */
    VALUE,
    /** Systolic / diastolic. */
    PAIR,
    /** One reading of a continuously sampled metric (heart rate, power, speed). */
    SAMPLE,
    /** A span of time (sleep, mindfulness). */
    SESSION,
    /** A span of days (menstruation period). */
    PERIOD,
    /** A moment with one of a few options (flow, test result). */
    CHOICE,
    /** A moment with no value (spotting). */
    EVENT,
    /** Has its own screen elsewhere in the app (food, water, weight, workouts). */
    DEDICATED,
}

/**
 * Every Health Connect data type Discipline can read and write. [unit] is the unit values are
 * entered and stored in. Exercise routes, training plans and medical records are not included:
 * they use separate, per-record consent flows.
 */
enum class HcType(
    val label: String,
    val category: HealthCategory,
    val record: KClass<out Record>,
    val kind: EntryKind,
    val unit: String = "",
    val range: ClosedFloatingPointRange<Double> = 0.0..1_000_000.0,
    val options: List<Pair<Int, String>> = emptyList(),
    /** Health Connect feature that must be available, or null. */
    val feature: Int? = null,
) {
    // ------------------------------------------------------------------------------ activity
    STEPS("Steps", HealthCategory.ACTIVITY, StepsRecord::class, EntryKind.AMOUNT, "steps", 1.0..200_000.0),
    DISTANCE("Distance", HealthCategory.ACTIVITY, DistanceRecord::class, EntryKind.AMOUNT, "km", 0.01..1_000.0),
    ACTIVE_CALORIES("Active calories burned", HealthCategory.ACTIVITY, ActiveCaloriesBurnedRecord::class, EntryKind.AMOUNT, "kcal", 1.0..20_000.0),
    TOTAL_CALORIES("Total calories burned", HealthCategory.ACTIVITY, TotalCaloriesBurnedRecord::class, EntryKind.AMOUNT, "kcal", 1.0..30_000.0),
    FLOORS("Floors climbed", HealthCategory.ACTIVITY, FloorsClimbedRecord::class, EntryKind.AMOUNT, "floors", 1.0..2_000.0),
    ELEVATION("Elevation gained", HealthCategory.ACTIVITY, ElevationGainedRecord::class, EntryKind.AMOUNT, "m", 1.0..20_000.0),
    WHEELCHAIR_PUSHES("Wheelchair pushes", HealthCategory.ACTIVITY, WheelchairPushesRecord::class, EntryKind.AMOUNT, "pushes", 1.0..100_000.0),
    EXERCISE("Exercise", HealthCategory.ACTIVITY, ExerciseSessionRecord::class, EntryKind.DEDICATED, "min"),
    VO2_MAX("VO2 max", HealthCategory.ACTIVITY, Vo2MaxRecord::class, EntryKind.VALUE, "ml/kg/min", 5.0..100.0),
    POWER("Power", HealthCategory.ACTIVITY, PowerRecord::class, EntryKind.SAMPLE, "W", 1.0..3_000.0),
    SPEED("Speed", HealthCategory.ACTIVITY, SpeedRecord::class, EntryKind.SAMPLE, "km/h", 0.1..200.0),
    CYCLING_CADENCE("Cycling cadence", HealthCategory.ACTIVITY, CyclingPedalingCadenceRecord::class, EntryKind.SAMPLE, "rpm", 1.0..300.0),
    STEPS_CADENCE("Steps cadence", HealthCategory.ACTIVITY, StepsCadenceRecord::class, EntryKind.SAMPLE, "steps/min", 1.0..300.0),

    // ------------------------------------------------------------------------------- body
    WEIGHT("Weight", HealthCategory.BODY, WeightRecord::class, EntryKind.DEDICATED, "kg"),
    HEIGHT("Height", HealthCategory.BODY, HeightRecord::class, EntryKind.VALUE, "cm", 50.0..280.0),
    BODY_FAT("Body fat", HealthCategory.BODY, BodyFatRecord::class, EntryKind.VALUE, "%", 1.0..80.0),
    BMR("Basal metabolic rate", HealthCategory.BODY, BasalMetabolicRateRecord::class, EntryKind.VALUE, "kcal/day", 500.0..6_000.0),
    LEAN_MASS("Lean body mass", HealthCategory.BODY, LeanBodyMassRecord::class, EntryKind.VALUE, "kg", 10.0..300.0),
    BONE_MASS("Bone mass", HealthCategory.BODY, BoneMassRecord::class, EntryKind.VALUE, "kg", 0.5..20.0),
    BODY_WATER_MASS("Body water mass", HealthCategory.BODY, BodyWaterMassRecord::class, EntryKind.VALUE, "kg", 5.0..200.0),

    // ------------------------------------------------------------------------------ cycle
    MENSTRUATION_FLOW(
        "Menstruation flow", HealthCategory.CYCLE, MenstruationFlowRecord::class, EntryKind.CHOICE,
        options = listOf(MenstruationFlowRecord.FLOW_LIGHT to "Light", MenstruationFlowRecord.FLOW_MEDIUM to "Medium", MenstruationFlowRecord.FLOW_HEAVY to "Heavy"),
    ),
    MENSTRUATION_PERIOD("Menstruation period", HealthCategory.CYCLE, MenstruationPeriodRecord::class, EntryKind.PERIOD, "days"),
    OVULATION_TEST(
        "Ovulation test", HealthCategory.CYCLE, OvulationTestRecord::class, EntryKind.CHOICE,
        options = listOf(
            OvulationTestRecord.RESULT_POSITIVE to "Positive", OvulationTestRecord.RESULT_HIGH to "High",
            OvulationTestRecord.RESULT_NEGATIVE to "Negative", OvulationTestRecord.RESULT_INCONCLUSIVE to "Inconclusive",
        ),
    ),
    CERVICAL_MUCUS(
        "Cervical mucus", HealthCategory.CYCLE, CervicalMucusRecord::class, EntryKind.CHOICE,
        options = listOf(
            CervicalMucusRecord.APPEARANCE_DRY to "Dry", CervicalMucusRecord.APPEARANCE_STICKY to "Sticky",
            CervicalMucusRecord.APPEARANCE_CREAMY to "Creamy", CervicalMucusRecord.APPEARANCE_WATERY to "Watery",
            CervicalMucusRecord.APPEARANCE_EGG_WHITE to "Egg white", CervicalMucusRecord.APPEARANCE_UNUSUAL to "Unusual",
        ),
    ),
    INTERMENSTRUAL_BLEEDING("Spotting", HealthCategory.CYCLE, IntermenstrualBleedingRecord::class, EntryKind.EVENT),
    SEXUAL_ACTIVITY(
        "Sexual activity", HealthCategory.CYCLE, SexualActivityRecord::class, EntryKind.CHOICE,
        options = listOf(
            SexualActivityRecord.PROTECTION_USED_PROTECTED to "Protected", SexualActivityRecord.PROTECTION_USED_UNPROTECTED to "Unprotected",
            SexualActivityRecord.PROTECTION_USED_UNKNOWN to "Not specified",
        ),
    ),

    // -------------------------------------------------------------------------- nutrition
    NUTRITION("Nutrition", HealthCategory.NUTRITION, NutritionRecord::class, EntryKind.DEDICATED, "kcal"),
    HYDRATION("Hydration", HealthCategory.NUTRITION, HydrationRecord::class, EntryKind.DEDICATED, "ml"),

    // ------------------------------------------------------------------------------ sleep
    SLEEP("Sleep", HealthCategory.SLEEP, SleepSessionRecord::class, EntryKind.SESSION, "min"),

    // ----------------------------------------------------------------------------- vitals
    HEART_RATE("Heart rate", HealthCategory.VITALS, HeartRateRecord::class, EntryKind.SAMPLE, "bpm", 20.0..250.0),
    RESTING_HEART_RATE("Resting heart rate", HealthCategory.VITALS, RestingHeartRateRecord::class, EntryKind.VALUE, "bpm", 20.0..200.0),
    HRV("Heart rate variability", HealthCategory.VITALS, HeartRateVariabilityRmssdRecord::class, EntryKind.VALUE, "ms", 1.0..200.0),
    BLOOD_PRESSURE("Blood pressure", HealthCategory.VITALS, BloodPressureRecord::class, EntryKind.PAIR, "mmHg", 20.0..250.0),
    BLOOD_GLUCOSE("Blood glucose", HealthCategory.VITALS, BloodGlucoseRecord::class, EntryKind.VALUE, "mmol/L", 0.5..40.0),
    OXYGEN_SATURATION("Oxygen saturation", HealthCategory.VITALS, OxygenSaturationRecord::class, EntryKind.VALUE, "%", 50.0..100.0),
    RESPIRATORY_RATE("Respiratory rate", HealthCategory.VITALS, RespiratoryRateRecord::class, EntryKind.VALUE, "breaths/min", 1.0..100.0),
    BODY_TEMPERATURE("Body temperature", HealthCategory.VITALS, BodyTemperatureRecord::class, EntryKind.VALUE, "°C", 30.0..45.0),
    BASAL_BODY_TEMPERATURE("Basal body temperature", HealthCategory.VITALS, BasalBodyTemperatureRecord::class, EntryKind.VALUE, "°C", 30.0..45.0),
    SKIN_TEMPERATURE(
        "Skin temperature", HealthCategory.VITALS, SkinTemperatureRecord::class, EntryKind.VALUE, "°C", 20.0..45.0,
        feature = HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE,
    ),

    // --------------------------------------------------------------------------- wellness
    MINDFULNESS(
        "Mindfulness", HealthCategory.WELLNESS, MindfulnessSessionRecord::class, EntryKind.SESSION, "min",
        feature = HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION,
    ),
    ;

    val readPermission: String get() = HealthPermission.getReadPermission(record)
    val writePermission: String get() = HealthPermission.getWritePermission(record)

    /** True when this type can be entered with the generic "Log health data" form. */
    val loggable get() = kind != EntryKind.DEDICATED

    companion object {
        fun forRecord(r: KClass<out Record>) = entries.firstOrNull { it.record == r }

        /** Every data permission Discipline declares (excluding history/background). */
        val allPermissions: Set<String> by lazy { entries.flatMapTo(mutableSetOf()) { listOf(it.readPermission, it.writePermission) } }
    }
}

/**
 * Which Health Connect categories the user lets Discipline read and write. The fine-grained,
 * per-type choice lives in Health Connect itself.
 */
data class HealthToggles(
    val read: Set<HealthCategory> = HealthCategory.entries.toSet(),
    val write: Set<HealthCategory> = HealthCategory.entries.toSet(),
    /** Count food other apps logged towards Discipline's calories. Off by default to avoid double counting. */
    val countOtherFood: Boolean = false,
    /** Add water other apps logged to Discipline's daily water. */
    val countOtherWater: Boolean = true,
) {
    fun canRead(t: HcType) = t.category in read
    fun canWrite(t: HcType) = t.category in write
    fun withRead(c: HealthCategory, on: Boolean) = copy(read = if (on) read + c else read - c)
    fun withWrite(c: HealthCategory, on: Boolean) = copy(write = if (on) write + c else write - c)
}
