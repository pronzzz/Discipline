package app.discipline.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class DayCalories(val epochDay: Long, val calories: Double)

data class FrequentFood(
    val foodId: Long,
    val uses: Int,
    val lastServings: Double,
)

@Dao
interface FoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(foods: List<FoodEntity>)

    @Upsert
    suspend fun upsert(food: FoodEntity): Long

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun byId(id: Long): FoodEntity?

    @Query("SELECT * FROM foods WHERE source = :source AND remoteId = :remoteId LIMIT 1")
    suspend fun byRemote(source: FoodSource, remoteId: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE barcode = :barcode ORDER BY source = 'USER' DESC LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodEntity?

    /** Token search: every token must appear; prefix matches on the whole key rank first. */
    @Query(
        """
        SELECT * FROM foods
        WHERE searchKey LIKE '%' || :t1 || '%' AND searchKey LIKE '%' || :t2 || '%' AND searchKey LIKE '%' || :t3 || '%'
        ORDER BY (searchKey LIKE :t1 || '%') DESC, favorite DESC, source = 'USER' DESC, length(name) ASC
        LIMIT :limit
        """
    )
    suspend fun search(t1: String, t2: String, t3: String, limit: Int = 60): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE source = 'USER' ORDER BY name COLLATE NOCASE")
    fun userFoods(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE favorite = 1 ORDER BY name COLLATE NOCASE")
    fun favorites(): Flow<List<FoodEntity>>

    @Query("UPDATE foods SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("DELETE FROM foods WHERE id = :id AND source = 'USER'")
    suspend fun deleteUserFood(id: Long)

    @Query("SELECT COUNT(*) FROM foods WHERE source = 'GENERIC'")
    suspend fun genericCount(): Int

    /** Distinct foods by most recent use. */
    @Query(
        """
        SELECT f.* FROM foods f
        JOIN (SELECT foodId, MAX(loggedAt) AS lastUsed FROM food_entries WHERE foodId IS NOT NULL GROUP BY foodId) r
        ON r.foodId = f.id ORDER BY r.lastUsed DESC LIMIT :limit
        """
    )
    fun recent(limit: Int = 50): Flow<List<FoodEntity>>

    @Query(
        """
        SELECT f.* FROM foods f
        JOIN (SELECT foodId, COUNT(*) AS uses FROM food_entries WHERE foodId IS NOT NULL GROUP BY foodId) r
        ON r.foodId = f.id ORDER BY r.uses DESC, f.name LIMIT :limit
        """
    )
    fun frequent(limit: Int = 50): Flow<List<FoodEntity>>

    @Query("SELECT servings FROM food_entries WHERE foodId = :foodId ORDER BY loggedAt DESC LIMIT 1")
    suspend fun lastServings(foodId: Long): Double?
}

@Dao
interface DiaryDao {
    // ---- Food entries
    @Insert
    suspend fun insertEntry(e: FoodEntryEntity): Long

    @Insert
    suspend fun insertEntries(e: List<FoodEntryEntity>): List<Long>

    @Upsert
    suspend fun upsertEntry(e: FoodEntryEntity): Long

    @Query("SELECT * FROM food_entries WHERE id = :id")
    suspend fun entry(id: Long): FoodEntryEntity?

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM food_entries WHERE epochDay = :day ORDER BY meal, loggedAt")
    fun entriesForDay(day: Long): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries WHERE epochDay = :day AND meal = :meal ORDER BY loggedAt")
    suspend fun entriesForMeal(day: Long, meal: Int): List<FoodEntryEntity>

    @Query("SELECT epochDay, SUM(calories) AS calories FROM food_entries WHERE epochDay BETWEEN :from AND :to GROUP BY epochDay ORDER BY epochDay")
    fun caloriesByDay(from: Long, to: Long): Flow<List<DayCalories>>

    @Query("SELECT DISTINCT epochDay FROM food_entries WHERE epochDay <= :today ORDER BY epochDay DESC LIMIT 400")
    fun loggedDays(today: Long): Flow<List<Long>>

    @Query("SELECT * FROM food_entries ORDER BY epochDay, meal, loggedAt")
    suspend fun allEntries(): List<FoodEntryEntity>

    @Query("SELECT id FROM food_entries WHERE epochDay >= :from")
    suspend fun entryIdsSince(from: Long): List<Long>

    @Query("SELECT id FROM exercise_entries WHERE epochDay >= :from")
    suspend fun exerciseIdsSince(from: Long): List<Long>

    @Query("SELECT id FROM water_entries WHERE epochDay >= :from")
    suspend fun waterIdsSince(from: Long): List<Long>

    @Query("SELECT epochDay FROM weights WHERE epochDay >= :from AND source = 'LOCAL'")
    suspend fun localWeightDaysSince(from: Long): List<Long>

    // ---- Exercise
    @Upsert
    suspend fun upsertExercise(e: ExerciseEntity): Long

    @Query("SELECT * FROM exercise_entries WHERE id = :id")
    suspend fun exercise(id: Long): ExerciseEntity?

    @Query("DELETE FROM exercise_entries WHERE id = :id")
    suspend fun deleteExercise(id: Long)

    @Query("SELECT * FROM exercise_entries WHERE epochDay = :day ORDER BY startedAt")
    fun exerciseForDay(day: Long): Flow<List<ExerciseEntity>>

    // ---- Water
    @Insert
    suspend fun insertWater(e: WaterEntity): Long

    @Query("SELECT * FROM water_entries WHERE id = :id")
    suspend fun water(id: Long): WaterEntity?

    @Query("DELETE FROM water_entries WHERE id = :id")
    suspend fun deleteWater(id: Long)

    @Query("SELECT * FROM water_entries WHERE epochDay = :day ORDER BY loggedAt")
    fun waterForDay(day: Long): Flow<List<WaterEntity>>

    // ---- Weight
    @Upsert
    suspend fun upsertWeight(w: WeightEntity)

    @Query("SELECT * FROM weights WHERE epochDay = :day")
    suspend fun weight(day: Long): WeightEntity?

    @Query("DELETE FROM weights WHERE epochDay = :day")
    suspend fun deleteWeight(day: Long)

    @Query("SELECT * FROM weights WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun weights(from: Long, to: Long): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weights ORDER BY epochDay DESC LIMIT 1")
    fun latestWeight(): Flow<WeightEntity?>

    @Query("SELECT * FROM weights ORDER BY epochDay")
    suspend fun allWeights(): List<WeightEntity>

    // ---- Health Connect read cache
    @Upsert
    suspend fun upsertHealthDay(d: HealthDayEntity)

    @Query("SELECT * FROM hc_days WHERE epochDay = :day")
    fun healthDay(day: Long): Flow<HealthDayEntity?>

    @Query("SELECT * FROM hc_days WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay")
    fun healthDays(from: Long, to: Long): Flow<List<HealthDayEntity>>

    @Query("SELECT * FROM hc_exercise WHERE epochDay = :day ORDER BY startedAt")
    fun importedExercise(day: Long): Flow<List<ImportedExerciseEntity>>

    @Transaction
    suspend fun replaceImportedExercise(day: Long, items: List<ImportedExerciseEntity>) {
        clearImportedExercise(day)
        insertImportedExercise(items)
    }

    @Query("DELETE FROM hc_exercise WHERE epochDay = :day")
    suspend fun clearImportedExercise(day: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportedExercise(items: List<ImportedExerciseEntity>)

    @Query("DELETE FROM hc_days")
    suspend fun clearHealthDays()

    @Query("DELETE FROM hc_exercise")
    suspend fun clearAllImportedExercise()
}

@Dao
interface OutboxDao {
    @Query("SELECT * FROM hc_outbox ORDER BY createdAt LIMIT :limit")
    suspend fun pending(limit: Int = 200): List<OutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: OutboxEntity)

    @Query("DELETE FROM hc_outbox WHERE id IN (:ids)")
    suspend fun remove(ids: List<Long>)

    @Query("UPDATE hc_outbox SET attempts = attempts + 1 WHERE id IN (:ids)")
    suspend fun bumpAttempts(ids: List<Long>)

    @Query("DELETE FROM hc_outbox WHERE attempts >= :max")
    suspend fun dropExhausted(max: Int = 8)

    @Query("SELECT COUNT(*) FROM hc_outbox")
    fun count(): Flow<Int>

    @Query("DELETE FROM hc_outbox")
    suspend fun clear()
}
