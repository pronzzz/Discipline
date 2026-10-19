package app.discipline.domain

import androidx.health.connect.client.records.ExerciseSessionRecord
import kotlin.math.roundToInt

/** Built-in activities with approximate MET values (Compendium-of-Physical-Activities style). */
enum class ExerciseKind(val label: String, val met: Double, val hcType: Int) {
    WALKING("Walking", 3.5, ExerciseSessionRecord.EXERCISE_TYPE_WALKING),
    RUNNING("Running", 9.8, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING),
    CYCLING("Cycling", 7.5, ExerciseSessionRecord.EXERCISE_TYPE_BIKING),
    SWIMMING("Swimming", 7.0, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL),
    STRENGTH("Strength training", 5.0, ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING),
    HIIT("HIIT", 8.0, ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING),
    YOGA("Yoga", 2.5, ExerciseSessionRecord.EXERCISE_TYPE_YOGA),
    PILATES("Pilates", 3.0, ExerciseSessionRecord.EXERCISE_TYPE_PILATES),
    ROWING("Rowing machine", 7.0, ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE),
    ELLIPTICAL("Elliptical", 5.0, ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL),
    HIKING("Hiking", 6.0, ExerciseSessionRecord.EXERCISE_TYPE_HIKING),
    STAIRS("Stair climbing", 8.0, ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING),
    DANCING("Dancing", 5.0, ExerciseSessionRecord.EXERCISE_TYPE_DANCING),
    TENNIS("Tennis", 7.3, ExerciseSessionRecord.EXERCISE_TYPE_TENNIS),
    BASKETBALL("Basketball", 6.5, ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL),
    FOOTBALL("Football (soccer)", 7.0, ExerciseSessionRecord.EXERCISE_TYPE_SOCCER),
    OTHER("Other workout", 4.0, ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT);

    fun calories(minutes: Int, weightKg: Double): Int = (met * weightKg * minutes / 60.0).roundToInt()

    companion object {
        fun forHcType(type: Int) = entries.firstOrNull { it.hcType == type }
    }
}
