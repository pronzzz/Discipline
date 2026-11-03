package app.discipline.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        HealthReadingEntity::class,
        HealthLogEntity::class,
        ImportedFoodEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class DisciplineDatabase : RoomDatabase() {
    abstract fun foods(): FoodDao
    abstract fun diary(): DiaryDao
    abstract fun outbox(): OutboxDao

    companion object {
        fun build(context: Context): DisciplineDatabase =
            Room.databaseBuilder(context, DisciplineDatabase::class.java, "discipline.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** 1.1.0: every Health Connect data type, exercise distance and body fat. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_entries ADD COLUMN distanceMeters REAL")
                db.execSQL("ALTER TABLE weights ADD COLUMN bodyFatPct REAL")
                db.execSQL("ALTER TABLE hc_days ADD COLUMN otherWaterMl INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hc_readings` (`type` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `value` REAL NOT NULL, " +
                        "`value2` REAL, `choice` INTEGER, `time` INTEGER NOT NULL, `sourceApp` TEXT NOT NULL, PRIMARY KEY(`type`, `epochDay`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `health_log` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, " +
                        "`start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `value` REAL, `value2` REAL, `choice` INTEGER, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_health_log_type` ON `health_log` (`type`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hc_nutrition` (`hcId` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `meal` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, `sourceApp` TEXT NOT NULL, `time` INTEGER NOT NULL, `calories` REAL NOT NULL, `protein` REAL NOT NULL, " +
                        "`carbs` REAL NOT NULL, `fat` REAL NOT NULL, `fiber` REAL, `sugar` REAL, `saturatedFat` REAL, `sodiumMg` REAL, " +
                        "`cholesterolMg` REAL, `potassiumMg` REAL, PRIMARY KEY(`hcId`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_hc_nutrition_epochDay` ON `hc_nutrition` (`epochDay`)")
            }
        }
    }
}
