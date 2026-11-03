package app.discipline.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.discipline.domain.Nutrients
import app.discipline.health.HcType

enum class FoodSource { GENERIC, USER, OPEN_FOOD_FACTS }

/** A food definition. [nutrients] are per one [servingLabel]. */
@Entity(
    tableName = "foods",
    indices = [Index("barcode"), Index("searchKey"), Index(value = ["source", "remoteId"], unique = true)],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val servingLabel: String,
    /** Grams in one serving, when known. Enables entering an amount in grams. */
    val servingGrams: Double? = null,
    @Embedded val nutrients: Nutrients,
    val source: FoodSource,
    /** Stable id within the source (barcode for Open Food Facts, slug for generic foods). */
    val remoteId: String? = null,
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /** Lower-cased, accent-stripped "name brand" used for search. */
    val searchKey: String,
)

/**
 * One logged item. Nutrients are a snapshot of the totals at log time so editing or deleting
 * the food later never rewrites the diary.
 */
@Entity(tableName = "food_entries", indices = [Index("epochDay"), Index("foodId")])
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val meal: Int,
    /** Null for a quick-add entry. */
    val foodId: Long?,
    val name: String,
    val brand: String?,
    val servingLabel: String,
    val servingGrams: Double?,
    val servings: Double,
    @Embedded val nutrients: Nutrients,
    val loggedAt: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "exercise_entries", indices = [Index("epochDay")])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val name: String,
    val minutes: Int,
    val calories: Int,
    val startedAt: Long,
    /** Health Connect ExerciseSessionRecord.EXERCISE_TYPE_* value. */
    val hcExerciseType: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    /** Optional distance covered, written to Health Connect as a DistanceRecord. */
    val distanceMeters: Double? = null,
)

@Entity(tableName = "water_entries", indices = [Index("epochDay")])
data class WaterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val ml: Int,
    val loggedAt: Long,
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class WeightSource { LOCAL, HEALTH_CONNECT }

@Entity(tableName = "weights")
data class WeightEntity(
    @PrimaryKey val epochDay: Long,
    val kg: Double,
    val source: WeightSource = WeightSource.LOCAL,
    val updatedAt: Long = System.currentTimeMillis(),
    /** Optional body fat percentage measured with this weigh-in. */
    val bodyFatPct: Double? = null,
)

/** Stored by name, so new kinds can be appended safely. */
enum class SyncKind { NUTRITION, HYDRATION, WEIGHT, EXERCISE, BODY_FAT, HEIGHT, LOG }
enum class SyncOp { UPSERT, DELETE }

/** Pending Health Connect write. One row per (kind, localId); the latest op wins. */
@Entity(tableName = "hc_outbox", indices = [Index(value = ["kind", "localId"], unique = true)])
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: SyncKind,
    val localId: Long,
    val op: SyncOp,
    val attempts: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Per-day totals read from Health Connect that feed the calorie and water maths. */
@Entity(tableName = "hc_days")
data class HealthDayEntity(
    @PrimaryKey val epochDay: Long,
    val steps: Long = 0,
    /** Active calories from other apps (Discipline's own workouts are already counted). */
    val activeCalories: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis(),
    /** Water other apps logged this day (Discipline's own records excluded). */
    @ColumnInfo(defaultValue = "0") val otherWaterMl: Int = 0,
)

/**
 * The value of one Health Connect data type on one day, as shown on the Health data screen:
 * a daily total for amounts, a daily average for sampled metrics, minutes for sessions, and the
 * latest reading for everything else. [value2] is diastolic blood pressure; [choice] holds the
 * option of choice types (flow, test result).
 */
@Entity(tableName = "hc_readings", primaryKeys = ["type", "epochDay"])
data class HealthReadingEntity(
    val type: HcType,
    val epochDay: Long,
    val value: Double,
    val value2: Double? = null,
    val choice: Int? = null,
    val time: Long,
    val sourceApp: String,
)

/** A value entered with the "Log health data" form, mirrored to Health Connect via the outbox. */
@Entity(tableName = "health_log", indices = [Index("type")])
data class HealthLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: HcType,
    val start: Long,
    /** Equal to [start] for instantaneous types. */
    val end: Long,
    val value: Double? = null,
    val value2: Double? = null,
    val choice: Int? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Food other apps logged to Health Connect, shown read-only in the diary. */
@Entity(tableName = "hc_nutrition", indices = [Index("epochDay")])
data class ImportedFoodEntity(
    @PrimaryKey val hcId: String,
    val epochDay: Long,
    val meal: Int,
    val name: String,
    @Embedded val nutrients: Nutrients,
    val sourceApp: String,
    val time: Long,
)

/** Exercise sessions recorded by other apps, shown read-only in the diary. */
@Entity(tableName = "hc_exercise", indices = [Index("epochDay")])
data class ImportedExerciseEntity(
    @PrimaryKey val hcId: String,
    val epochDay: Long,
    val title: String,
    val minutes: Int,
    val calories: Int?,
    val sourceApp: String,
    val startedAt: Long,
)
