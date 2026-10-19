package app.discipline.data

import androidx.room.withTransaction
import app.discipline.data.db.DisciplineDatabase
import app.discipline.data.db.ExerciseEntity
import app.discipline.data.db.FoodEntity
import app.discipline.data.db.FoodEntryEntity
import app.discipline.data.db.OutboxEntity
import app.discipline.data.db.SyncKind
import app.discipline.data.db.SyncOp
import app.discipline.data.db.WaterEntity
import app.discipline.data.db.WeightEntity
import app.discipline.data.db.WeightSource
import app.discipline.domain.Meal
import app.discipline.domain.Nutrients
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * All diary writes go through here so every change is mirrored into the Health Connect outbox
 * in the same transaction. [onChanged] kicks the background sync.
 */
class DiaryRepository(
    private val db: DisciplineDatabase,
    private val onChanged: () -> Unit,
) {
    private val dao = db.diary()
    private val outbox = db.outbox()

    fun entries(day: LocalDate): Flow<List<FoodEntryEntity>> = dao.entriesForDay(day.toEpochDay())
    fun exercise(day: LocalDate): Flow<List<ExerciseEntity>> = dao.exerciseForDay(day.toEpochDay())
    fun water(day: LocalDate): Flow<List<WaterEntity>> = dao.waterForDay(day.toEpochDay())
    fun healthDay(day: LocalDate) = dao.healthDay(day.toEpochDay())
    fun importedExercise(day: LocalDate) = dao.importedExercise(day.toEpochDay())
    fun weights(from: LocalDate, to: LocalDate) = dao.weights(from.toEpochDay(), to.toEpochDay())
    fun latestWeight() = dao.latestWeight()
    fun caloriesByDay(from: LocalDate, to: LocalDate) = dao.caloriesByDay(from.toEpochDay(), to.toEpochDay())
    fun healthDays(from: LocalDate, to: LocalDate) = dao.healthDays(from.toEpochDay(), to.toEpochDay())
    fun loggedDays(today: LocalDate) = dao.loggedDays(today.toEpochDay())

    suspend fun entry(id: Long) = dao.entry(id)

    /** Timestamp used for an entry: now when logging today, otherwise the meal's typical hour. */
    fun loggedAtFor(day: LocalDate, meal: Int): Long {
        val zone = ZoneId.systemDefault()
        return if (day == LocalDate.now()) System.currentTimeMillis()
        else day.atTime(LocalTime.of(Meal.fromOrdinal(meal).defaultHour, 0)).atZone(zone).toInstant().toEpochMilli()
    }

    suspend fun logFood(day: LocalDate, meal: Int, food: FoodEntity, servings: Double): Long = tx {
        val id = dao.insertEntry(
            FoodEntryEntity(
                epochDay = day.toEpochDay(),
                meal = meal,
                foodId = food.id.takeIf { it != 0L },
                name = food.name,
                brand = food.brand,
                servingLabel = food.servingLabel,
                servingGrams = food.servingGrams,
                servings = servings,
                nutrients = food.nutrients * servings,
                loggedAt = loggedAtFor(day, meal),
            )
        )
        enqueue(SyncKind.NUTRITION, id, SyncOp.UPSERT)
        id
    }

    suspend fun quickAdd(day: LocalDate, meal: Int, nutrients: Nutrients, label: String): Long = tx {
        val id = dao.insertEntry(
            FoodEntryEntity(
                epochDay = day.toEpochDay(),
                meal = meal,
                foodId = null,
                name = label.ifBlank { "Quick add" },
                brand = null,
                servingLabel = "Quick add",
                servingGrams = null,
                servings = 1.0,
                nutrients = nutrients,
                loggedAt = loggedAtFor(day, meal),
            )
        )
        enqueue(SyncKind.NUTRITION, id, SyncOp.UPSERT)
        id
    }

    /** Changes servings and/or meal of an existing entry, rescaling its nutrient snapshot. */
    suspend fun updateEntry(id: Long, servings: Double, meal: Int) = tx {
        val e = dao.entry(id) ?: return@tx
        val perServing = if (e.servings > 0) e.nutrients * (1.0 / e.servings) else e.nutrients
        dao.upsertEntry(
            e.copy(
                servings = servings,
                meal = meal,
                nutrients = perServing * servings,
                updatedAt = System.currentTimeMillis(),
            )
        )
        enqueue(SyncKind.NUTRITION, id, SyncOp.UPSERT)
    }

    suspend fun deleteEntry(id: Long): FoodEntryEntity? = tx {
        val e = dao.entry(id)
        dao.deleteEntry(id)
        enqueue(SyncKind.NUTRITION, id, SyncOp.DELETE)
        e
    }

    /** Re-inserts a previously deleted entry (undo). */
    suspend fun restoreEntry(e: FoodEntryEntity) = tx {
        val id = dao.insertEntry(e.copy(id = 0, updatedAt = System.currentTimeMillis()))
        enqueue(SyncKind.NUTRITION, id, SyncOp.UPSERT)
    }

    /** Copies one meal from [from] to [to]. Returns the number of entries copied. */
    suspend fun copyMeal(from: LocalDate, fromMeal: Int, to: LocalDate, toMeal: Int): Int = tx {
        val src = dao.entriesForMeal(from.toEpochDay(), fromMeal)
        val now = System.currentTimeMillis()
        val ids = dao.insertEntries(src.map {
            it.copy(id = 0, epochDay = to.toEpochDay(), meal = toMeal, loggedAt = loggedAtFor(to, toMeal), updatedAt = now)
        })
        ids.forEach { enqueue(SyncKind.NUTRITION, it, SyncOp.UPSERT) }
        ids.size
    }

    suspend fun saveExercise(e: ExerciseEntity): Long = tx {
        val id = dao.upsertExercise(e.copy(updatedAt = System.currentTimeMillis()))
        val realId = if (e.id == 0L) id else e.id
        enqueue(SyncKind.EXERCISE, realId, SyncOp.UPSERT)
        realId
    }

    suspend fun deleteExercise(id: Long) = tx {
        dao.deleteExercise(id)
        enqueue(SyncKind.EXERCISE, id, SyncOp.DELETE)
    }

    suspend fun addWater(day: LocalDate, ml: Int): Long = tx {
        val id = dao.insertWater(WaterEntity(epochDay = day.toEpochDay(), ml = ml, loggedAt = loggedAtFor(day, Meal.SNACKS.ordinal)))
        enqueue(SyncKind.HYDRATION, id, SyncOp.UPSERT)
        id
    }

    suspend fun deleteWater(id: Long) = tx {
        dao.deleteWater(id)
        enqueue(SyncKind.HYDRATION, id, SyncOp.DELETE)
    }

    suspend fun logWeight(day: LocalDate, kg: Double) = tx {
        dao.upsertWeight(WeightEntity(day.toEpochDay(), kg, WeightSource.LOCAL))
        enqueue(SyncKind.WEIGHT, day.toEpochDay(), SyncOp.UPSERT)
    }

    suspend fun deleteWeight(day: LocalDate) = tx {
        val w = dao.weight(day.toEpochDay())
        dao.deleteWeight(day.toEpochDay())
        // Only remove from Health Connect what Discipline wrote there.
        if (w?.source == WeightSource.LOCAL) enqueue(SyncKind.WEIGHT, day.toEpochDay(), SyncOp.DELETE)
    }

    private suspend fun enqueue(kind: SyncKind, localId: Long, op: SyncOp) =
        outbox.enqueue(OutboxEntity(kind = kind, localId = localId, op = op))

    private suspend fun <T> tx(block: suspend () -> T): T {
        val result = db.withTransaction { block() }
        onChanged()
        return result
    }
}
