package app.discipline.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.FloorsClimbedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.Vo2MaxRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Volume
import app.discipline.data.SettingsRepository
import app.discipline.data.db.DisciplineDatabase
import app.discipline.data.db.FoodEntryEntity
import app.discipline.data.db.HealthDayEntity
import app.discipline.data.db.ImportedExerciseEntity
import app.discipline.data.db.ImportedFoodEntity
import app.discipline.data.db.HealthReadingEntity
import app.discipline.data.db.OutboxEntity
import app.discipline.data.db.SyncKind
import app.discipline.data.db.SyncOp
import app.discipline.data.db.WeightEntity
import app.discipline.data.db.WeightSource
import app.discipline.domain.ExerciseKind
import app.discipline.domain.Meal
import app.discipline.domain.Nutrients
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.reflect.KClass

enum class HealthAvailability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

data class HealthStatus(
    val availability: HealthAvailability = HealthAvailability.UNAVAILABLE,
    val granted: Set<String> = emptySet(),
    val backgroundReadSupported: Boolean = false,
    /** Optional Health Connect features (FEATURE_*) available on this device. */
    val features: Set<Int> = emptySet(),
) {
    val connected get() = availability == HealthAvailability.AVAILABLE && granted.any { it in HcType.allPermissions }
    fun has(permission: String) = permission in granted
    fun canRead(t: HcType) = t.readPermission in granted
    fun canWrite(t: HcType) = t.writePermission in granted

    /** Types this device's Health Connect supports. */
    fun supported(t: HcType) = t.feature == null || t.feature in features
}

/**
 * Discipline's Health Connect bridge.
 *
 * Writes: every local change is queued in the Room outbox; [flushOutbox] turns each row into an
 * idempotent upsert (client record id + version) or a delete by client record id. No remote-id
 * mapping table is needed.
 *
 * Reads: per-day aggregates (steps, calories, distance, floors, heart rate, other apps' water),
 * sleep sessions, other apps' workouts, weights, food, and the latest body and vitals readings,
 * over a rolling window. Each read replaces the local cache for that window.
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
        fun available(f: Int) = runCatching {
            c?.features?.getFeatureStatus(f) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        }.getOrDefault(false)
        val features = listOf(
            HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND,
            HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE,
            HealthConnectFeatures.FEATURE_MINDFULNESS_SESSION,
        ).filterTo(mutableSetOf(), ::available)
        return HealthStatus(
            availability, granted,
            backgroundReadSupported = HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND in features,
            features = features,
        ).also { _status.value = it }
    }

    /** Permissions to request, given the user's per-type toggles. */
    fun permissionsFor(t: HealthToggles, includeBackground: Boolean = true): Set<String> = buildSet {
        val status = _status.value
        HcType.entries.filter(status::supported).forEach {
            if (t.canRead(it)) add(it.readPermission)
            if (t.canWrite(it)) add(it.writePermission)
        }
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
        suspend fun q(kind: SyncKind, id: Long) = outbox.enqueue(OutboxEntity(kind = kind, localId = id, op = SyncOp.UPSERT))
        diary.entryIdsSince(from).forEach { q(SyncKind.NUTRITION, it) }
        diary.waterIdsSince(from).forEach { q(SyncKind.HYDRATION, it) }
        diary.exerciseIdsSince(from).forEach { q(SyncKind.EXERCISE, it) }
        diary.localWeightDaysSince(from).forEach { q(SyncKind.WEIGHT, it) }
        diary.localBodyFatDaysSince(from).forEach { q(SyncKind.BODY_FAT, it) }
        diary.logIdsSince(LocalDate.now().minusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()).forEach { q(SyncKind.LOG, it) }
        q(SyncKind.HEIGHT, LocalDate.now().toEpochDay())
    }

    // --------------------------------------------------------------------------- system screens

    /**
     * Screens that let the user review Discipline's Health Connect access, best first.
     *
     * `MANAGE_HEALTH_PERMISSIONS` is deliberately not used: on Android 14+ it is reserved for
     * holders of GRANT_RUNTIME_PERMISSIONS, and starting it from an app throws a SecurityException.
     */
    fun settingsIntents(): List<Intent> = buildList {
        // Discipline's own page in Health Connect (permissions and data).
        add(HealthConnectClient.getHealthConnectManageDataIntent(context, PROVIDER))
        // Health Connect home.
        add(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) add(Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"))
        // Last resort: Android's app info page, which links to Health Connect permissions.
        add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }

    /** Opens the first of [settingsIntents] the system accepts. Never throws. */
    fun openSettings(from: Context): Boolean {
        for (intent in settingsIntents()) {
            try {
                from.startActivity(intent)
                return true
            } catch (e: Exception) {
                // ActivityNotFoundException or SecurityException: try the next screen.
                Log.i(TAG, "Couldn't open ${intent.action}", e)
            }
        }
        return false
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
            if (kind == SyncKind.LOG) {
                val (d, f) = flushLogs(c, group, granted, toggles)
                done += d; failed += f
                continue
            }
            val type = when (kind) {
                SyncKind.NUTRITION -> HcType.NUTRITION
                SyncKind.HYDRATION -> HcType.HYDRATION
                SyncKind.WEIGHT -> HcType.WEIGHT
                SyncKind.EXERCISE -> HcType.EXERCISE
                SyncKind.BODY_FAT -> HcType.BODY_FAT
                SyncKind.HEIGHT -> HcType.HEIGHT
                SyncKind.LOG -> error("handled above")
            }
            if (!toggles.canWrite(type) || type.writePermission !in granted) {
                // Nothing to do for this type; drop so the queue doesn't grow forever.
                outbox.remove(group.map { it.id })
                continue
            }
            val upserts = group.filter { it.op == SyncOp.UPSERT }
            val deletes = group.filter { it.op == SyncOp.DELETE }
            try {
                val records = upserts.flatMap { recordsFor(kind, it, granted) }
                if (records.isNotEmpty()) c.insertRecords(records)
                if (deletes.isNotEmpty()) deleteByClientIds(c, kind, deletes, granted)
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

    /** Writes and deletes entries from "Log health data". Each type succeeds or fails on its own. */
    private suspend fun flushLogs(c: HealthConnectClient, items: List<OutboxEntity>, granted: Set<String>, toggles: HealthToggles): Pair<Int, Int> {
        val outbox = db.outbox()
        val diary = db.diary()
        var done = 0
        var failed = 0
        // Resolve each outbox row to (type, log id, entry-or-null-for-delete).
        val resolved = items.map { item ->
            if (item.op == SyncOp.DELETE) {
                val (type, id) = app.discipline.data.decodeLogOutboxId(item.localId)
                Triple(item, type, id)
            } else {
                Triple(item, diary.log(item.localId)?.type, item.localId)
            }
        }
        for ((type, group) in resolved.groupBy { it.second }) {
            val ids = group.map { it.first.id }
            if (type == null || !toggles.canWrite(type) || type.writePermission !in granted) {
                outbox.remove(ids)
                continue
            }
            try {
                val records = group.filter { it.first.op == SyncOp.UPSERT }.mapNotNull { (_, _, id) ->
                    diary.log(id)?.let { log ->
                        HealthRecords.build(log, Metadata.manualEntry(clientRecordId = clientId("log", log.id), clientRecordVersion = log.updatedAt))
                    }
                }
                if (records.isNotEmpty()) c.insertRecords(records)
                val deletes = group.filter { it.first.op == SyncOp.DELETE }.map { clientId("log", it.third) }
                if (deletes.isNotEmpty()) c.deleteRecords(type.record, emptyList(), deletes)
                outbox.remove(ids)
                done += ids.size
            } catch (e: Exception) {
                Log.w(TAG, "Health Connect write failed for $type", e)
                outbox.bumpAttempts(ids)
                failed += ids.size
            }
        }
        return done to failed
    }

    private suspend fun recordsFor(kind: SyncKind, item: OutboxEntity, granted: Set<String>): List<Record> {
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
                val time = morningOf(w.epochDay)
                listOf(
                    WeightRecord(
                        time = time, zoneOffset = offset(time),
                        weight = Mass.kilograms(w.kg),
                        metadata = Metadata.manualEntry(clientRecordId = clientId("weight", w.epochDay), clientRecordVersion = w.updatedAt),
                    )
                )
            }.orEmpty()
            SyncKind.BODY_FAT -> diary.weight(item.localId)?.takeIf { it.source == WeightSource.LOCAL && it.bodyFatPct != null }?.let { w ->
                val time = morningOf(w.epochDay)
                listOf(
                    BodyFatRecord(
                        time = time, zoneOffset = offset(time),
                        percentage = Percentage(w.bodyFatPct!!.coerceIn(0.0, 100.0)),
                        metadata = Metadata.manualEntry(clientRecordId = clientId("bodyfat", w.epochDay), clientRecordVersion = w.updatedAt),
                    )
                )
            }.orEmpty()
            SyncKind.LOG -> emptyList()
            SyncKind.HEIGHT -> {
                val cm = settings.current().profile.heightCm.takeIf { it in 50.0..280.0 } ?: return emptyList()
                val day = LocalDate.ofEpochDay(item.localId)
                val time = if (day == LocalDate.now()) Instant.now() else morningOf(item.localId)
                listOf(
                    HeightRecord(
                        time = time, zoneOffset = offset(time),
                        height = Length.meters(cm / 100.0),
                        metadata = Metadata.manualEntry(clientRecordId = clientId("height", item.localId), clientRecordVersion = System.currentTimeMillis()),
                    )
                )
            }
            SyncKind.EXERCISE -> diary.exercise(item.localId)?.let { e ->
                val start = Instant.ofEpochMilli(e.startedAt)
                val end = start.plus(Duration.ofMinutes(e.minutes.coerceAtLeast(1).toLong()))
                buildList<Record> {
                    add(
                        ExerciseSessionRecord(
                            startTime = start, startZoneOffset = offset(start),
                            endTime = end, endZoneOffset = offset(end),
                            exerciseType = e.hcExerciseType,
                            title = e.name,
                            metadata = Metadata.manualEntry(clientRecordId = clientId("exercise", e.id), clientRecordVersion = e.updatedAt),
                        )
                    )
                    if (write(ActiveCaloriesBurnedRecord::class) in granted) add(
                        ActiveCaloriesBurnedRecord(
                            startTime = start, startZoneOffset = offset(start),
                            endTime = end, endZoneOffset = offset(end),
                            energy = Energy.kilocalories(e.calories.toDouble()),
                            metadata = Metadata.manualEntry(clientRecordId = clientId("exercise-kcal", e.id), clientRecordVersion = e.updatedAt),
                        )
                    )
                    val meters = e.distanceMeters
                    if (meters != null && meters > 0 && write(DistanceRecord::class) in granted) add(
                        DistanceRecord(
                            startTime = start, startZoneOffset = offset(start),
                            endTime = end, endZoneOffset = offset(end),
                            distance = Length.meters(meters.coerceAtMost(1_000_000.0)),
                            metadata = Metadata.manualEntry(clientRecordId = clientId("exercise-distance", e.id), clientRecordVersion = e.updatedAt),
                        )
                    )
                }
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

    private suspend fun deleteByClientIds(c: HealthConnectClient, kind: SyncKind, items: List<OutboxEntity>, granted: Set<String>) {
        when (kind) {
            SyncKind.NUTRITION -> c.deleteRecords(NutritionRecord::class, emptyList(), items.map { clientId("food", it.localId) })
            SyncKind.HYDRATION -> c.deleteRecords(HydrationRecord::class, emptyList(), items.map { clientId("water", it.localId) })
            SyncKind.WEIGHT -> c.deleteRecords(WeightRecord::class, emptyList(), items.map { clientId("weight", it.localId) })
            SyncKind.BODY_FAT -> c.deleteRecords(BodyFatRecord::class, emptyList(), items.map { clientId("bodyfat", it.localId) })
            SyncKind.HEIGHT, SyncKind.LOG -> Unit
            SyncKind.EXERCISE -> {
                c.deleteRecords(ExerciseSessionRecord::class, emptyList(), items.map { clientId("exercise", it.localId) })
                if (write(ActiveCaloriesBurnedRecord::class) in granted) {
                    c.deleteRecords(ActiveCaloriesBurnedRecord::class, emptyList(), items.map { clientId("exercise-kcal", it.localId) })
                }
                if (write(DistanceRecord::class) in granted) {
                    c.deleteRecords(DistanceRecord::class, emptyList(), items.map { clientId("exercise-distance", it.localId) })
                }
            }
        }
    }

    /** Removes every record Discipline ever wrote to Health Connect. */
    suspend fun deleteAllOwnData() {
        val c = client ?: return
        val all = TimeRangeFilter.between(Instant.EPOCH, Instant.now().plus(Duration.ofDays(2)))
        HcType.entries.map { it.record }.distinct()
            .forEach { type -> runCatching { c.deleteRecords(type, all) } }
    }

    // ------------------------------------------------------------------------------------- reads

    /** Reads the last [days] days (including today) into the local cache. */
    suspend fun readRecent(days: Int): Int {
        val c = client ?: return 0
        val status = refreshStatus()
        val granted = status.granted
        val toggles = settings.current().health
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val from = today.minusDays((days - 1).toLong())
        val diary = db.diary()
        val own = DataOrigin(context.packageName)
        fun can(t: HcType) = toggles.canRead(t) && t.readPermission in granted && status.supported(t)
        val window = TimeRangeFilter.between(from.atStartOfDay(), today.plusDays(1).atStartOfDay())

        /** Per-day aggregate in the type's unit. null = the read failed, so keep what's cached. */
        suspend fun daily(t: HcType, origins: Set<DataOrigin> = emptySet()): Map<LocalDate, Double>? {
            val metric = HealthRecords.dailyMetric(t) ?: return emptyMap()
            if (!can(t)) return emptyMap()
            return try {
                c.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(metric), window, Period.ofDays(1), origins))
                    .mapNotNull { g -> g.result[metric]?.let { g.startTime.toLocalDate() to HealthRecords.number(t, it) } }.toMap()
            } catch (e: Exception) {
                Log.w(TAG, "Health Connect aggregate failed: $t", e)
                null
            }
        }

        // -------- every type, for the Health data screen
        val dailyValues = mutableMapOf<HcType, Map<LocalDate, Double>?>()
        for (t in HcType.entries) {
            if (!can(t)) continue
            safely(t.name) {
                val items: List<HealthReadingEntity> = when {
                    t == HcType.SLEEP -> sleepByWakeDay(c, from, today, zone).map { (day, min) ->
                        HealthReadingEntity(t, day.toEpochDay(), min.toDouble(), time = day.atStartOfDay(zone).toInstant().toEpochMilli(), sourceApp = "Health Connect")
                    }
                    HealthRecords.dailyMetric(t) != null -> {
                        val values = daily(t).also { dailyValues[t] = it } ?: return@safely
                        values.filterValues { it > 0 }.map { (day, v) ->
                            HealthReadingEntity(t, day.toEpochDay(), v, time = day.atStartOfDay(zone).toInstant().toEpochMilli(), sourceApp = "Health Connect")
                        }
                    }
                    else -> readAll(c, t.record, from, today, zone)
                        .groupBy { HealthRecords.timeOf(it).atZone(zone).toLocalDate() }
                        .mapNotNull { (day, list) ->
                            val latest = list.maxBy { HealthRecords.timeOf(it) }
                            val r = HealthRecords.reading(latest) ?: return@mapNotNull null
                            val value = if (t.kind == EntryKind.EVENT) list.size.toDouble() else r.value
                            HealthReadingEntity(
                                t, day.toEpochDay(), value, r.value2, r.choice,
                                HealthRecords.timeOf(latest).toEpochMilli(), appLabel(latest.metadata.dataOrigin.packageName),
                            )
                        }
                }
                diary.replaceReadings(t, from.toEpochDay(), today.toEpochDay(), items)
            }
        }

        // -------- the totals that feed calorie and water maths
        val steps = if (HcType.STEPS in dailyValues) dailyValues[HcType.STEPS] else daily(HcType.STEPS)
        val activeAll = if (HcType.ACTIVE_CALORIES in dailyValues) dailyValues[HcType.ACTIVE_CALORIES] else daily(HcType.ACTIVE_CALORIES)
        val activeOwn = daily(HcType.ACTIVE_CALORIES, setOf(own))
        val waterAll = if (HcType.HYDRATION in dailyValues) dailyValues[HcType.HYDRATION] else daily(HcType.HYDRATION)
        val waterOwn = daily(HcType.HYDRATION, setOf(own))
        if (can(HcType.STEPS) || can(HcType.ACTIVE_CALORIES) || can(HcType.HYDRATION)) {
            var d = from
            while (!d.isAfter(today)) {
                val old = diary.healthDayNow(d.toEpochDay()) ?: HealthDayEntity(d.toEpochDay())
                val active = if (activeAll == null || activeOwn == null) old.activeCalories
                else ((activeAll[d] ?: 0.0) - (activeOwn[d] ?: 0.0)).coerceAtLeast(0.0)
                val otherWater = if (waterAll == null || waterOwn == null) old.otherWaterMl
                else ((waterAll[d] ?: 0.0) - (waterOwn[d] ?: 0.0)).coerceAtLeast(0.0).roundToInt()
                diary.upsertHealthDay(
                    HealthDayEntity(
                        epochDay = d.toEpochDay(),
                        steps = steps?.let { (it[d] ?: 0.0).toLong() } ?: old.steps,
                        activeCalories = active,
                        otherWaterMl = otherWater,
                    )
                )
                d = d.plusDays(1)
            }
        }

        // -------- other apps' workouts, shown in the diary
        if (can(HcType.EXERCISE)) safely("exercise") {
            val sessions = readAll(c, ExerciseSessionRecord::class, from, today, zone)
                .filter { it.metadata.dataOrigin.packageName != context.packageName }
            val canReadKcal = HcType.ACTIVE_CALORIES.readPermission in granted
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

        // -------- weight from smart scales, into the weight log
        if (can(HcType.WEIGHT)) safely("weight") {
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

        // -------- height from other apps keeps the profile current
        if (can(HcType.HEIGHT)) safely("height") {
            readAll(c, HeightRecord::class, from, today, zone)
                .filter { it.metadata.dataOrigin.packageName != context.packageName }
                .maxByOrNull { it.time }?.let { h ->
                    val cm = h.height.inMeters * 100
                    if (cm in 50.0..280.0 && abs(cm - settings.current().profile.heightCm) > 0.5) settings.setHeight(cm)
                }
        }

        // -------- food other apps logged, counted only when the user asks for it
        if (can(HcType.NUTRITION) && toggles.countOtherFood) safely("nutrition") {
            val items = readAll(c, NutritionRecord::class, from, today, zone)
                .filter { it.metadata.dataOrigin.packageName != context.packageName }
                .map { r ->
                    val local = r.startTime.atZone(zone)
                    ImportedFoodEntity(
                        hcId = r.metadata.id,
                        epochDay = local.toLocalDate().toEpochDay(),
                        meal = when (r.mealType) {
                            MealType.MEAL_TYPE_BREAKFAST -> Meal.BREAKFAST
                            MealType.MEAL_TYPE_LUNCH -> Meal.LUNCH
                            MealType.MEAL_TYPE_DINNER -> Meal.DINNER
                            MealType.MEAL_TYPE_SNACK -> Meal.SNACKS
                            else -> Meal.forHour(local.hour)
                        }.ordinal,
                        name = r.name?.takeIf { it.isNotBlank() } ?: "Food",
                        nutrients = Nutrients(
                            calories = r.energy?.inKilocalories ?: 0.0,
                            protein = r.protein?.inGrams ?: 0.0,
                            carbs = r.totalCarbohydrate?.inGrams ?: 0.0,
                            fat = r.totalFat?.inGrams ?: 0.0,
                            fiber = r.dietaryFiber?.inGrams,
                            sugar = r.sugar?.inGrams,
                            saturatedFat = r.saturatedFat?.inGrams,
                            sodiumMg = r.sodium?.inMilligrams,
                            cholesterolMg = r.cholesterol?.inMilligrams,
                            potassiumMg = r.potassium?.inMilligrams,
                        ),
                        sourceApp = appLabel(r.metadata.dataOrigin.packageName),
                        time = r.startTime.toEpochMilli(),
                    )
                }
            diary.replaceImportedFood(from.toEpochDay(), today.toEpochDay(), items)
        } else {
            // Turned off: other apps' food must stop counting towards Discipline's calories.
            diary.clearAllImportedFood()
        }

        settings.setLastHealthSync(System.currentTimeMillis())
        return days
    }

    /** Minutes asleep per wake-up day. Awake stages inside a session are not counted. */
    private suspend fun sleepByWakeDay(c: HealthConnectClient, from: LocalDate, to: LocalDate, zone: ZoneId): Map<LocalDate, Int> {
        // A night that ends on [from] starts the evening before.
        val sessions = readAll(c, SleepSessionRecord::class, from.minusDays(1), to, zone)
        val awake = setOf(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED, SleepSessionRecord.STAGE_TYPE_OUT_OF_BED)
        return sessions
            .groupBy { it.endTime.atZone(zone).toLocalDate() }
            .filterKeys { !it.isBefore(from) && !it.isAfter(to) }
            .mapValues { (_, list) ->
                list.sumOf { s ->
                    val total = Duration.between(s.startTime, s.endTime).toMinutes()
                    val awakeMin = s.stages.filter { it.stage in awake }.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                    (total - awakeMin).coerceAtLeast(0)
                }.toInt()
            }
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

    private val labels = mutableMapOf<String, String>()

    @Suppress("DEPRECATION")
    private fun appLabel(pkg: String): String = labels.getOrPut(pkg) {
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    private fun offset(i: Instant) = ZonedDateTime.ofInstant(i, ZoneId.systemDefault()).offset
    private fun morningOf(epochDay: Long) = LocalDate.ofEpochDay(epochDay).atTime(LocalTime.of(8, 0)).atZone(ZoneId.systemDefault()).toInstant()

    companion object {
        const val PROVIDER = "com.google.android.apps.healthdata"
        private const val TAG = "DisciplineHC"

        fun clientId(kind: String, id: Long) = "discipline-$kind-$id"
        fun write(t: KClass<out Record>) = HealthPermission.getWritePermission(t)
        fun read(t: KClass<out Record>) = HealthPermission.getReadPermission(t)
    }
}
