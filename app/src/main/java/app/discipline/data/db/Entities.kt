package app.discipline.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.discipline.domain.Nutrients

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
)

enum class SyncKind { NUTRITION, HYDRATION, WEIGHT, EXERCISE }
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

/** Per-day activity read from Health Connect. */
@Entity(tableName = "hc_days")
data class HealthDayEntity(
    @PrimaryKey val epochDay: Long,
    val steps: Long = 0,
    val activeCalories: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis(),
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
