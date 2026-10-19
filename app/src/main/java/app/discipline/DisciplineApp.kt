package app.discipline

import android.app.Application
import android.content.Context
import app.discipline.data.DiaryRepository
import app.discipline.data.SettingsRepository
import app.discipline.data.db.DisciplineDatabase
import app.discipline.data.food.FoodRepository
import app.discipline.data.food.OpenFoodFactsClient
import app.discipline.health.HealthConnectManager
import app.discipline.health.HealthSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = DisciplineDatabase.build(app)
    val settings = SettingsRepository(app)
    val foods = FoodRepository(app, db.foods(), OpenFoodFactsClient("Discipline/${BuildConfig.VERSION_NAME} (Android; personal project)"))
    val diary = DiaryRepository(db) { HealthSyncWorker.enqueueWrite(app) }
    val health = HealthConnectManager(app, db, settings)
}

class DisciplineApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scope.launch {
            container.foods.seedIfNeeded()
            container.health.refreshStatus()
        }
        HealthSyncWorker.schedulePeriodic(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as DisciplineApp).container
