package app.discipline.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        FoodEntity::class,
        FoodEntryEntity::class,
        ExerciseEntity::class,
        WaterEntity::class,
        WeightEntity::class,
        OutboxEntity::class,
        HealthDayEntity::class,
        ImportedExerciseEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DisciplineDatabase : RoomDatabase() {
    abstract fun foods(): FoodDao
    abstract fun diary(): DiaryDao
    abstract fun outbox(): OutboxDao

    companion object {
        fun build(context: Context): DisciplineDatabase =
            Room.databaseBuilder(context, DisciplineDatabase::class.java, "discipline.db").build()
    }
}
