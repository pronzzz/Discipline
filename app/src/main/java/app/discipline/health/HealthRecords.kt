package app.discipline.health

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalBodyTemperatureRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureMeasurementLocation
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
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Pressure
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.TemperatureDelta
import androidx.health.connect.client.units.Velocity
import androidx.health.connect.client.units.Volume
import app.discipline.data.db.HealthLogEntity
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** A value pulled out of a record, in the [HcType.unit] of its type. */
data class Reading(val value: Double, val value2: Double? = null, val choice: Int? = null)

/** Converts between Discipline's simple values and Health Connect records for every [HcType]. */
object HealthRecords {

    private fun offset(i: Instant): ZoneOffset = ZonedDateTime.ofInstant(i, ZoneId.systemDefault()).offset

    /**
     * Builds the Health Connect record for a value entered with "Log health data".
     * Returns null for types with their own screens (food, water, weight, workouts).
     */
    fun build(log: HealthLogEntity, meta: Metadata): Record? {
        val s = Instant.ofEpochMilli(log.start)
        val e = Instant.ofEpochMilli(maxOf(log.end, log.start + 60_000))
        val so = offset(s)
        val eo = offset(e)
        val v = log.value ?: 0.0
        val choice = log.choice ?: 0
        return when (log.type) {
            HcType.STEPS -> StepsRecord(s, so, e, eo, v.toLong().coerceAtLeast(1), meta)
            HcType.DISTANCE -> DistanceRecord(s, so, e, eo, Length.kilometers(v), meta)
            HcType.ACTIVE_CALORIES -> ActiveCaloriesBurnedRecord(s, so, e, eo, Energy.kilocalories(v), meta)
            HcType.TOTAL_CALORIES -> TotalCaloriesBurnedRecord(s, so, e, eo, Energy.kilocalories(v), meta)
            HcType.FLOORS -> FloorsClimbedRecord(s, so, e, eo, v, meta)
            HcType.ELEVATION -> ElevationGainedRecord(s, so, e, eo, Length.meters(v), meta)
            HcType.WHEELCHAIR_PUSHES -> WheelchairPushesRecord(s, so, e, eo, v.toLong().coerceAtLeast(1), meta)
            HcType.VO2_MAX -> Vo2MaxRecord(s, so, meta, v, Vo2MaxRecord.MEASUREMENT_METHOD_OTHER)
            HcType.POWER -> PowerRecord(s, so, e, eo, listOf(PowerRecord.Sample(s, Power.watts(v))), meta)
            HcType.SPEED -> SpeedRecord(s, so, e, eo, listOf(SpeedRecord.Sample(s, Velocity.kilometersPerHour(v))), meta)
            HcType.CYCLING_CADENCE -> CyclingPedalingCadenceRecord(s, so, e, eo, listOf(CyclingPedalingCadenceRecord.Sample(s, v)), meta)
            HcType.STEPS_CADENCE -> StepsCadenceRecord(s, so, e, eo, listOf(StepsCadenceRecord.Sample(s, v)), meta)

            HcType.HEIGHT -> HeightRecord(s, so, Length.meters(v / 100.0), meta)
            HcType.BODY_FAT -> BodyFatRecord(s, so, Percentage(v), meta)
            HcType.BMR -> BasalMetabolicRateRecord(s, so, Power.kilocaloriesPerDay(v), meta)
            HcType.LEAN_MASS -> LeanBodyMassRecord(s, so, Mass.kilograms(v), meta)
            HcType.BONE_MASS -> BoneMassRecord(s, so, Mass.kilograms(v), meta)
            HcType.BODY_WATER_MASS -> BodyWaterMassRecord(s, so, Mass.kilograms(v), meta)

            HcType.MENSTRUATION_FLOW -> MenstruationFlowRecord(s, so, meta, choice)
            HcType.MENSTRUATION_PERIOD -> MenstruationPeriodRecord(s, so, Instant.ofEpochMilli(maxOf(log.end, log.start + 86_400_000)), offset(Instant.ofEpochMilli(log.end)), meta)
            HcType.OVULATION_TEST -> OvulationTestRecord(s, so, choice, meta)
            HcType.CERVICAL_MUCUS -> CervicalMucusRecord(s, so, meta, choice, CervicalMucusRecord.SENSATION_UNKNOWN)
            HcType.INTERMENSTRUAL_BLEEDING -> IntermenstrualBleedingRecord(s, so, meta)
            HcType.SEXUAL_ACTIVITY -> SexualActivityRecord(s, so, meta, choice)

            HcType.SLEEP -> SleepSessionRecord(s, so, e, eo, meta, null, null, emptyList())
            HcType.MINDFULNESS -> MindfulnessSessionRecord(s, so, e, eo, meta, MindfulnessSessionRecord.MINDFULNESS_SESSION_TYPE_UNKNOWN, null, null)

            HcType.HEART_RATE -> HeartRateRecord(s, so, e, eo, listOf(HeartRateRecord.Sample(s, v.toLong())), meta)
            HcType.RESTING_HEART_RATE -> RestingHeartRateRecord(s, so, v.toLong(), meta)
            HcType.HRV -> HeartRateVariabilityRmssdRecord(s, so, v, meta)
            HcType.BLOOD_PRESSURE -> BloodPressureRecord(
                s, so, meta, Pressure.millimetersOfMercury(v), Pressure.millimetersOfMercury(log.value2 ?: 0.0),
                BloodPressureRecord.BODY_POSITION_UNKNOWN, BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN,
            )
            HcType.BLOOD_GLUCOSE -> BloodGlucoseRecord(
                s, so, meta, BloodGlucose.millimolesPerLiter(v),
                BloodGlucoseRecord.SPECIMEN_SOURCE_UNKNOWN, androidx.health.connect.client.records.MealType.MEAL_TYPE_UNKNOWN,
                BloodGlucoseRecord.RELATION_TO_MEAL_UNKNOWN,
            )
            HcType.OXYGEN_SATURATION -> OxygenSaturationRecord(s, so, Percentage(v), meta)
            HcType.RESPIRATORY_RATE -> RespiratoryRateRecord(s, so, v, meta)
            HcType.BODY_TEMPERATURE -> BodyTemperatureRecord(s, so, meta, Temperature.celsius(v), BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_UNKNOWN)
            HcType.BASAL_BODY_TEMPERATURE -> BasalBodyTemperatureRecord(s, so, meta, Temperature.celsius(v), BodyTemperatureMeasurementLocation.MEASUREMENT_LOCATION_UNKNOWN)
            HcType.SKIN_TEMPERATURE -> SkinTemperatureRecord(
                s, so, e, eo, meta, listOf(SkinTemperatureRecord.Delta(s, TemperatureDelta.celsius(0.0))),
                Temperature.celsius(v), SkinTemperatureRecord.MEASUREMENT_LOCATION_UNKNOWN,
            )

            HcType.EXERCISE, HcType.WEIGHT, HcType.NUTRITION, HcType.HYDRATION -> null
        }
    }

    /**
     * Per-day aggregate used to read a type, when Health Connect offers one. Totals for amounts,
     * averages for sampled metrics.
     */
    fun dailyMetric(t: HcType): AggregateMetric<out Any>? = when (t) {
        HcType.STEPS -> StepsRecord.COUNT_TOTAL
        HcType.DISTANCE -> DistanceRecord.DISTANCE_TOTAL
        HcType.ACTIVE_CALORIES -> ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL
        HcType.TOTAL_CALORIES -> TotalCaloriesBurnedRecord.ENERGY_TOTAL
        HcType.FLOORS -> FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL
        HcType.ELEVATION -> ElevationGainedRecord.ELEVATION_GAINED_TOTAL
        HcType.WHEELCHAIR_PUSHES -> WheelchairPushesRecord.COUNT_TOTAL
        HcType.EXERCISE -> ExerciseSessionRecord.EXERCISE_DURATION_TOTAL
        HcType.POWER -> PowerRecord.POWER_AVG
        HcType.SPEED -> SpeedRecord.SPEED_AVG
        HcType.CYCLING_CADENCE -> CyclingPedalingCadenceRecord.RPM_AVG
        HcType.STEPS_CADENCE -> StepsCadenceRecord.RATE_AVG
        HcType.NUTRITION -> NutritionRecord.ENERGY_TOTAL
        HcType.HYDRATION -> HydrationRecord.VOLUME_TOTAL
        HcType.HEART_RATE -> HeartRateRecord.BPM_AVG
        HcType.MINDFULNESS -> MindfulnessSessionRecord.MINDFULNESS_DURATION_TOTAL
        else -> null
    }

    /** Converts an aggregate result to a number in the type's unit. */
    fun number(t: HcType, v: Any): Double = when (v) {
        is Long -> v.toDouble()
        is Int -> v.toDouble()
        is Double -> v
        is Length -> if (t == HcType.DISTANCE) v.inKilometers else v.inMeters
        is Energy -> v.inKilocalories
        is Power -> if (t == HcType.BMR) v.inKilocaloriesPerDay else v.inWatts
        is Velocity -> v.inKilometersPerHour
        is Volume -> v.inMilliliters
        is Duration -> v.toMinutes().toDouble()
        is TemperatureDelta -> v.inCelsius
        else -> 0.0
    }

    /** Moment a record is attributed to: its time, or the end of an interval. */
    fun timeOf(r: Record): Instant = when (r) {
        // Instantaneous records
        is WeightRecord -> r.time
        is HeightRecord -> r.time
        is BodyFatRecord -> r.time
        is BasalMetabolicRateRecord -> r.time
        is LeanBodyMassRecord -> r.time
        is BoneMassRecord -> r.time
        is BodyWaterMassRecord -> r.time
        is MenstruationFlowRecord -> r.time
        is OvulationTestRecord -> r.time
        is CervicalMucusRecord -> r.time
        is IntermenstrualBleedingRecord -> r.time
        is SexualActivityRecord -> r.time
        is RestingHeartRateRecord -> r.time
        is HeartRateVariabilityRmssdRecord -> r.time
        is BloodPressureRecord -> r.time
        is BloodGlucoseRecord -> r.time
        is OxygenSaturationRecord -> r.time
        is RespiratoryRateRecord -> r.time
        is BodyTemperatureRecord -> r.time
        is BasalBodyTemperatureRecord -> r.time
        is Vo2MaxRecord -> r.time
        // Interval records: attributed to when they ended
        is StepsRecord -> r.endTime
        is DistanceRecord -> r.endTime
        is ActiveCaloriesBurnedRecord -> r.endTime
        is TotalCaloriesBurnedRecord -> r.endTime
        is FloorsClimbedRecord -> r.endTime
        is ElevationGainedRecord -> r.endTime
        is WheelchairPushesRecord -> r.endTime
        is ExerciseSessionRecord -> r.endTime
        is PowerRecord -> r.endTime
        is SpeedRecord -> r.endTime
        is CyclingPedalingCadenceRecord -> r.endTime
        is StepsCadenceRecord -> r.endTime
        is MenstruationPeriodRecord -> r.endTime
        is NutritionRecord -> r.endTime
        is HydrationRecord -> r.endTime
        is SleepSessionRecord -> r.endTime
        is HeartRateRecord -> r.endTime
        is SkinTemperatureRecord -> r.endTime
        is MindfulnessSessionRecord -> r.endTime
        else -> Instant.EPOCH
    }

    /** The value of one record, for types read record by record rather than aggregated. */
    fun reading(r: Record): Reading? = when (r) {
        is WeightRecord -> Reading(r.weight.inKilograms)
        is HeightRecord -> Reading(r.height.inMeters * 100)
        is BodyFatRecord -> Reading(r.percentage.value)
        is BasalMetabolicRateRecord -> Reading(r.basalMetabolicRate.inKilocaloriesPerDay)
        is LeanBodyMassRecord -> Reading(r.mass.inKilograms)
        is BoneMassRecord -> Reading(r.mass.inKilograms)
        is BodyWaterMassRecord -> Reading(r.mass.inKilograms)
        is Vo2MaxRecord -> Reading(r.vo2MillilitersPerMinuteKilogram)
        is RestingHeartRateRecord -> Reading(r.beatsPerMinute.toDouble())
        is HeartRateVariabilityRmssdRecord -> Reading(r.heartRateVariabilityMillis)
        is BloodPressureRecord -> Reading(r.systolic.inMillimetersOfMercury, r.diastolic.inMillimetersOfMercury)
        is BloodGlucoseRecord -> Reading(r.level.inMillimolesPerLiter)
        is OxygenSaturationRecord -> Reading(r.percentage.value)
        is RespiratoryRateRecord -> Reading(r.rate)
        is BodyTemperatureRecord -> Reading(r.temperature.inCelsius)
        is BasalBodyTemperatureRecord -> Reading(r.temperature.inCelsius)
        is SkinTemperatureRecord -> r.baseline?.let { b ->
            Reading(b.inCelsius + (r.deltas.lastOrNull()?.delta?.inCelsius ?: 0.0))
        } ?: r.deltas.lastOrNull()?.let { Reading(it.delta.inCelsius) }
        is MenstruationFlowRecord -> Reading(1.0, choice = r.flow)
        is OvulationTestRecord -> Reading(1.0, choice = r.result)
        is CervicalMucusRecord -> Reading(1.0, choice = r.appearance)
        is SexualActivityRecord -> Reading(1.0, choice = r.protectionUsed)
        is IntermenstrualBleedingRecord -> Reading(1.0)
        is MenstruationPeriodRecord -> Reading(Duration.between(r.startTime, r.endTime).toDays().coerceAtLeast(1).toDouble())
        else -> null
    }

    fun minutes(value: Double): String {
        val m = Math.round(value)
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }

    /** Text for a reading, e.g. "120/80 mmHg", "7.2 km", "Heavy". */
    fun format(t: HcType, value: Double, value2: Double? = null, choice: Int? = null): String {
        fun n(v: Double, decimals: Int) = if (decimals == 0) "%,d".format(Math.round(v)) else "%,.${decimals}f".format(v).trimEnd('0').trimEnd('.')
        return when (t.kind) {
            EntryKind.CHOICE -> t.options.firstOrNull { it.first == choice }?.second ?: "Logged"
            EntryKind.EVENT -> if (value <= 1.0) "Logged" else "${n(value, 0)} times"
            EntryKind.PAIR -> "${n(value, 0)}/${n(value2 ?: 0.0, 0)} ${t.unit}"
            EntryKind.SESSION -> minutes(value)
            EntryKind.DEDICATED -> when (t) {
                HcType.EXERCISE -> minutes(value)
                HcType.WEIGHT -> "${n(value, 1)} kg"
                else -> "${n(value, 0)} ${t.unit}"
            }
            else -> {
                val decimals = when (t) {
                    HcType.DISTANCE, HcType.BLOOD_GLUCOSE, HcType.BODY_TEMPERATURE, HcType.BASAL_BODY_TEMPERATURE,
                    HcType.SKIN_TEMPERATURE, HcType.VO2_MAX, HcType.SPEED -> 1
                    HcType.BODY_FAT, HcType.LEAN_MASS, HcType.BONE_MASS, HcType.BODY_WATER_MASS, HcType.OXYGEN_SATURATION -> 1
                    else -> 0
                }
                "${n(value, decimals)} ${t.unit}".trim()
            }
        }
    }
}
