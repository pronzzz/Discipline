package app.discipline.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Volume
import app.discipline.data.HealthToggles
import app.discipline.data.SettingsRepository
import app.discipline.data.db.DisciplineDatabase
import app.discipline.data.db.FoodEntryEntity
import app.discipline.data.db.HealthDayEntity
import app.discipline.data.db.ImportedExerciseEntity
import app.discipline.data.db.OutboxEntity
import app.discipline.data.db.SyncKind
import app.discipline.data.db.SyncOp
import app.discipline.data.db.WeightEntity
import app.discipline.data.db.WeightSource
import app.discipline.domain.ExerciseKind
import app.discipline.domain.Meal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.reflect.KClass

enum class HealthAvailability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

data class HealthStatus(
    val availability: HealthAvailability = HealthAvailability.UNAVAILABLE,
    val granted: Set<String> = emptySet(),
    val backgroundReadSupported: Boolean = false,
) {
    val connected get() = availability == HealthAvailability.AVAILABLE && granted.any { it in HealthConnectManager.CORE_PERMISSIONS }
    fun has(permission: String) = permission in granted
}

data class SyncReport(val written: Int, val deleted: Int, val failed: Int, val readDays: Int)

/**
 * Discipline's Health Connect bridge.
 *
 * Writes: every local change is queued in the Room outbox; [flushOutbox] turns each row into an
 * idempotent upsert (client record id + version) or a delete by client record id. No remote-id
 * mapping table is needed.
 *
 * Reads: steps and active calories (per-day aggregates), other apps' exercise sessions and
 * weights, over a rolling window.
 */
class HealthConnectManager(
    private val context: Context,
    private val db: DisciplineDatabase,
    private val settings: SettingsRepository,
) {
    private val _status = MutableStateFlow(HealthStatus())
    val status: StateFlow<HealthStatus> = _status.asStateFlow()

    private val client: HealthConnectClient? by lazy {
        if (availability() == HealthAvailability.AVAILABLE) HealthConnectClient.getOrCreate(context) else null
    }

    fun availability(): HealthAvailability = when (HealthConnectClient.getSdkStatus(context, PROVIDER)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthAvailability.UPDATE_REQUIRED
        else -> HealthAvailability.UNAVAILABLE
    }

    suspend fun refreshStatus(): HealthStatus {
        val availability = availability()
        val c = if (availability == HealthAvailability.AVAILABLE) client else null
        val granted = runCatching { c?.permissionController?.getGrantedPermissions() }.getOrNull().orEmpty()
        val bg = runCatching {
            c?.features?.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        }.getOrDefault(false)
        return HealthStatus(availability, granted, bg).also { _status.value = it }
    }

    /** Permissions to request, given the user's per-type toggles. */
    fun permissionsFor(t: HealthToggles, includeBackground: Boolean = true): Set<String> = buildSet {
        if (t.writeNutrition) add(write(NutritionRecord::class))
        if (t.writeHydration) add(write(HydrationRecord::class))
        if (t.writeWeight) add(write(WeightRecord::class))
        if (t.writeExercise) { add(write(ExerciseSessionRecord::class)); add(write(ActiveCaloriesBurnedRecord::class)) }
        if (t.readSteps) add(read(StepsRecord::class))
        if (t.readActiveCalories) add(read(ActiveCaloriesBurnedRecord::class))
        if (t.readExercise) { add(read(ExerciseSessionRecord::class)); add(read(ActiveCaloriesBurnedRecord::class)) }
        if (t.readWeight) add(read(WeightRecord::class))
        if (any { it.startsWith("android.permission.health.READ_") }) {
            add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
            if (includeBackground && _status.value.backgroundReadSupported) {
                add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
            }
        }
    }

    fun permissionContract() = PermissionController.createRequestPermissionResultContract()

    /** Called with the result of the permission contract. Tracks denials (HC stops prompting after two). */
    suspend fun onPermissionResult(requested: Set<String>, granted: Set<String>) {
        if (requested.isNotEmpty() && granted.isEmpty()) settings.recordHealthDenial()
        else if (granted.isNotEmpty()) settings.recordHealthDenial(reset = true)
        if (granted.any { it.startsWith("android.permission.health.WRITE_") }) backfill(days = 30)
        refreshStatus()
    }

    /**
     * Queues the recent diary for upload. Writes are idempotent (client record ids), so this is
     * safe to repeat; it covers anything logged before Health Connect was connected.
     */
    suspend fun backfill(days: Long) {
        val from = LocalDate.now().minusDays(days).toEpochDay()
        val diary = db.diary()
        val outbox = db.outbox()
        diary.entryIdsSince(from).forEach { outbox.enqueue(OutboxEntity(kind = SyncKind.NUTRITION, localId = it, op = SyncOp.UPSERT)) }
        diary.waterIdsSince(from).forEach { outbox.enqueue(OutboxEntity(kind = SyncKind.HYDRATION, localId = it, op = SyncOp.UPSERT)) }
        diary.exerciseIdsSince(from).forEach { outbox.enqueue(OutboxEntity(kind = SyncKind.EXERCISE, localId = it, op = SyncOp.UPSERT)) }
        diary.localWeightDaysSince(from).forEach { outbox.enqueue(OutboxEntity(kind = SyncKind.WEIGHT, localId = it, op = SyncOp.UPSERT)) }
    }

    /** Opens Health Connect's own settings / data management screen. */
    fun settingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS")
                .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        } else {
            Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
        }

    fun installIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PROVIDER&url=healthconnect%3A%2F%2Fonboarding"))
            .setPackage("com.android.vending")

    // ------------------------------------------------------------------------------------ writes

    suspend fun flushOutbox(): Pair<Int, Int> {
        val c = client ?: return 0 to 0
        val granted = refreshStatus().granted
        val toggles = settings.current().health
        val outbox = db.outbox()
        var done = 0
        var failed = 0

        outbox.dropExhausted()
        val items = outbox.pending()
        for ((kind, group) in items.groupBy { it.kind }) {
            val enabled = when (kind) {
                SyncKind.NUTRITION -> toggles.writeNutrition && write(NutritionRecord::class) in granted
                SyncKind.HYDRATION -> toggles.writeHydration && write(HydrationRecord::class) in granted
                SyncKind.WEIGHT -> toggles.writeWeight && write(WeightRecord::class) in granted
                SyncKind.EXERCISE -> toggles.writeExercise && write(ExerciseSessionRecord::class) in granted
            }
            if (!enabled) {
                // Nothing to do for this type; drop so the queue doesn't grow forever.
                outbox.remove(group.map { it.id })
                continue
            }
            val upserts = group.filter { it.op == SyncOp.UPSERT }
            val deletes = group.filter { it.op == SyncOp.DELETE }
            try {
                val records = upserts.flatMap { recordsFor(kind, it) }
                if (records.isNotEmpty()) c.insertRecords(records)
                if (deletes.isNotEmpty()) deleteByClientIds(c, kind, deletes)
                outbox.remove(group.map(OutboxEntity::id))
                done += group.size
            } catch (e: Exception) {
                Log.w(TAG, "Health Connect write failed for $kind", e)
                outbox.bumpAttempts(group.map(OutboxEntity::id))
                failed += group.size
            }
        }
        return done to failed
    }

    private suspend fun recordsFor(kind: SyncKind, item: OutboxEntity): List<Record> {
        val diary = db.diary()
        return when (kind) {
            SyncKind.NUTRITION -> diary.entry(item.localId)?.let { listOf(nutritionRecord(it)) }.orEmpty()
            SyncKind.HYDRATION -> diary.water(item.localId)?.let { w ->
                val start = Instant.ofEpochMilli(w.loggedAt)
                listOf(
                    HydrationRecord(
                        startTime = start, startZoneOffset = offset(start),
                        endTime = start.plusSeconds(60), endZoneOffset = offset(start),
                        volume = Volume.milliliters(w.ml.toDouble()),
                        metadata = Metadata.manualEntry(clientRecordId = clientId("water", w.id), clientRecordVersion = w.updatedAt),
                    )
                )
            }.orEmpty()
            SyncKind.WEIGHT -> diary.weight(item.localId)?.takeIf { it.source == WeightSource.LOCAL }?.let { w ->
                val time = LocalDate.ofEpochDay(w.epochDay).atTime(LocalTime.of(8, 0)).atZone(ZoneId.systemDefault()).toInstant()
                listOf(
                    WeightRecord(
                        time = time, zoneOffset = offset(time),
                        weight = Mass.kilograms(w.kg),
                        metadata = Metadata.manualEntry(clientRecordId = clientId("weight", w.epochDay), clientRecordVersion = w.updatedAt),
                    )
                )
            }.orEmpty()
            SyncKind.EXERCISE -> diary.exercise(item.localId)?.let { e ->
                val start = Instant.ofEpochMilli(e.startedAt)
                val end = start.plus(Duration.ofMinutes(e.minutes.coerceAtLeast(1).toLong()))
                listOf<Record>(
                    ExerciseSessionRecord(
                        startTime = start, startZoneOffset = offset(start),
                        endTime = end, endZoneOffset = offset(end),
                        exerciseType = e.hcExerciseType,
                        title = e.name,
                        metadata = Metadata.manualEntry(clientRecordId = clientId("exercise", e.id), clientRecordVersion = e.updatedAt),
                    ),
                    ActiveCaloriesBurnedRecord(
                        startTime = start, startZoneOffset = offset(start),
                        endTime = end, endZoneOffset = offset(end),
                        energy = Energy.kilocalories(e.calories.toDouble()),
                        metadata = Metadata.manualEntry(clientRecordId = clientId("exercise-kcal", e.id), clientRecordVersion = e.updatedAt),
                    ),
                )
            }.orEmpty()
        }
    }

    private fun nutritionRecord(e: FoodEntryEntity): NutritionRecord {
        val start = Instant.ofEpochMilli(e.loggedAt)
        val end = start.plusSeconds(60)
        val n = e.nutrients
        return NutritionRecord(
            startTime = start, startZoneOffset = offset(start),
            endTime = end, endZoneOffset = offset(end),
            name = e.name.take(100),
            mealType = when (Meal.fromOrdinal(e.meal)) {
                Meal.BREAKFAST -> MealType.MEAL_TYPE_BREAKFAST
                Meal.LUNCH -> MealType.MEAL_TYPE_LUNCH
                Meal.DINNER -> MealType.MEAL_TYPE_DINNER
                Meal.SNACKS -> MealType.MEAL_TYPE_SNACK
            },
            energy = Energy.kilocalories(n.calories.coerceIn(0.0, 100_000.0)),
            protein = Mass.grams(n.protein.coerceIn(0.0, 100_000.0)),
            totalCarbohydrate = Mass.grams(n.carbs.coerceIn(0.0, 100_000.0)),
            totalFat = Mass.grams(n.fat.coerceIn(0.0, 100_000.0)),
            dietaryFiber = n.fiber?.let { Mass.grams(it.coerceIn(0.0, 100_000.0)) },
            sugar = n.sugar?.let { Mass.grams(it.coerceIn(0.0, 100_000.0)) },
            saturatedFat = n.saturatedFat?.let { Mass.grams(it.coerceIn(0.0, 100_000.0)) },
            sodium = n.sodiumMg?.let { Mass.milligrams(it.coerceIn(0.0, 100_000_000.0)) },
            cholesterol = n.cholesterolMg?.let { Mass.milligrams(it.coerceIn(0.0, 100_000_000.0)) },
            potassium = n.potassiumMg?.let { Mass.milligrams(it.coerceIn(0.0, 100_000_000.0)) },
            metadata = Metadata.manualEntry(clientRecordId = clientId("food", e.id), clientRecordVersion = e.updatedAt),
        )
    }

    private suspend fun deleteByClientIds(c: HealthConnectClient, kind: SyncKind, items: List<OutboxEntity>) {
        when (kind) {
            SyncKind.NUTRITION -> c.deleteRecords(NutritionRecord::class, emptyList(), items.map { clientId("food", it.localId) })
            SyncKind.HYDRATION -> c.deleteRecords(HydrationRecord::class, emptyList(), items.map { clientId("water", it.localId) })
            SyncKind.WEIGHT -> c.deleteRecords(WeightRecord::class, emptyList(), items.map { clientId("weight", it.localId) })
            SyncKind.EXERCISE -> {
                c.deleteRecords(ExerciseSessionRecord::class, emptyList(), items.map { clientId("exercise", it.localId) })
                c.deleteRecords(ActiveCaloriesBurnedRecord::class, emptyList(), items.map { clientId("exercise-kcal", it.localId) })
            }
        }
    }

    /** Removes every record Discipline ever wrote to Health Connect. */
    suspend fun deleteAllOwnData() {
        val c = client ?: return
        val all = TimeRangeFilter.between(Instant.EPOCH, Instant.now().plus(Duration.ofDays(2)))
        listOf(NutritionRecord::class, HydrationRecord::class, WeightRecord::class, ExerciseSessionRecord::class, ActiveCaloriesBurnedRecord::class)
            .forEach { type -> runCatching { c.deleteRecords(type, all) } }
    }

    // ------------------------------------------------------------------------------------- reads

    /** Reads the last [days] days (including today) into the local cache. */
    suspend fun readRecent(days: Int): Int {
        val c = client ?: return 0
        val granted = refreshStatus().granted
        val toggles = settings.current().health
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val from = today.minusDays((days - 1).toLong())
        val diary = db.diary()
        val own = setOf(DataOrigin(context.packageName))

        val steps = mutableMapOf<LocalDate, Long>()
        val active = mutableMapOf<LocalDate, Double>()
        val window = TimeRangeFilter.between(from.atStartOfDay(), today.plusDays(1).atStartOfDay())

        if (toggles.readSteps && read(StepsRecord::class) in granted) safely("steps") {
            c.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(StepsRecord.COUNT_TOTAL), window, Period.ofDays(1)))
                .forEach { steps[it.startTime.toLocalDate()] = it.result[StepsRecord.COUNT_TOTAL] ?: 0L }
        }
        if (toggles.readActiveCalories && read(ActiveCaloriesBurnedRecord::class) in granted) safely("active") {
            val total = c.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), window, Period.ofDays(1)))
            val mine = c.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), window, Period.ofDays(1), own))
                .associate { it.startTime.toLocalDate() to (it.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0) }
            total.forEach {
                val d = it.startTime.toLocalDate()
                val kcal = it.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
                active[d] = (kcal - (mine[d] ?: 0.0)).coerceAtLeast(0.0)
            }
        }
        var d = from
        while (!d.isAfter(today)) {
            if (steps.containsKey(d) || active.containsKey(d)) {
                diary.upsertHealthDay(HealthDayEntity(d.toEpochDay(), steps[d] ?: 0, active[d] ?: 0.0))
            }
            d = d.plusDays(1)
        }

        if (toggles.readExercise && read(ExerciseSessionRecord::class) in granted) safely("exercise") {
            val sessions = readAll(c, ExerciseSessionRecord::class, from, today, zone)
                .filter { it.metadata.dataOrigin.packageName != context.packageName }
            val canReadKcal = read(ActiveCaloriesBurnedRecord::class) in granted
            val byDay = sessions.groupBy { it.startTime.atZone(zone).toLocalDate() }
            var day = from
            while (!day.isAfter(today)) {
                val items = byDay[day].orEmpty().map { s ->
                    val kcal = if (canReadKcal) runCatching {
                        c.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), TimeRangeFilter.between(s.startTime, s.endTime)))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                    }.getOrNull() else null
                    ImportedExerciseEntity(
                        hcId = s.metadata.id,
                        epochDay = day.toEpochDay(),
                        title = s.title?.takeIf { it.isNotBlank() } ?: ExerciseKind.forHcType(s.exerciseType)?.label ?: "Workout",
                        minutes = Duration.between(s.startTime, s.endTime).toMinutes().toInt(),
                        calories = kcal?.toInt(),
                        sourceApp = appLabel(s.metadata.dataOrigin.packageName),
                        startedAt = s.startTime.toEpochMilli(),
                    )
                }
                diary.replaceImportedExercise(day.toEpochDay(), items)
                day = day.plusDays(1)
            }
        }

        if (toggles.readWeight && read(WeightRecord::class) in granted) safely("weight") {
            val weights = readAll(c, WeightRecord::class, from, today, zone)
                .filter { it.metadata.dataOrigin.packageName != context.packageName }
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .mapValues { (_, v) -> v.maxBy { it.time } }
            for ((day, w) in weights) {
                val existing = diary.weight(day.toEpochDay())
                if (existing == null || existing.source == WeightSource.HEALTH_CONNECT) {
                    diary.upsertWeight(WeightEntity(day.toEpochDay(), w.weight.inKilograms, WeightSource.HEALTH_CONNECT))
                }
            }
            // Keep the profile's current weight in step with the newest measurement.
            diary.allWeights().lastOrNull()?.let { settings.setCurrentWeight(it.kg) }
        }

        settings.setLastHealthSync(System.currentTimeMillis())
        return days
    }

    private suspend fun <T : Record> readAll(c: HealthConnectClient, type: KClass<T>, from: LocalDate, to: LocalDate, zone: ZoneId): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val resp = c.readRecords(
                ReadRecordsRequest(
                    recordType = type,
                    timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant()),
                    pageToken = token,
                )
            )
            out += resp.records
            token = resp.pageToken
        } while (token != null)
        return out
    }

    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: SecurityException) {
            Log.i(TAG, "No permission for $what read", e)
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect read failed: $what", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun appLabel(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun offset(i: Instant) = ZonedDateTime.ofInstant(i, ZoneId.systemDefault()).offset

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
        private const val TAG = "DisciplineHC"

        fun clientId(kind: String, id: Long) = "discipline-$kind-$id"
        fun write(t: KClass<out Record>) = HealthPermission.getWritePermission(t)
        fun read(t: KClass<out Record>) = HealthPermission.getReadPermission(t)

        val CORE_PERMISSIONS = setOf(
            write(NutritionRecord::class), write(HydrationRecord::class), write(WeightRecord::class),
            write(ExerciseSessionRecord::class), read(StepsRecord::class), read(ActiveCaloriesBurnedRecord::class),
            read(ExerciseSessionRecord::class), read(WeightRecord::class),
        )
    }
}
