package app.discipline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.discipline.data.ThemeMode
import app.discipline.health.HealthAvailability
import app.discipline.health.HealthSyncWorker
import app.discipline.ui.DisciplineAppUi
import app.discipline.ui.theme.DisciplineTheme
import app.discipline.ui.theme.OneUi
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Sync with Health Connect each time the app comes to the foreground.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val status = container.health.refreshStatus()
                if (status.availability == HealthAvailability.AVAILABLE && status.connected) {
                    val firstSync = container.settings.current().lastHealthSync == 0L
                    HealthSyncWorker.enqueueFull(this@MainActivity, readDays = if (firstSync) 30 else 7)
                }
            }
        }

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            val dark = when (s.theme) {
                ThemeMode.SYSTEM -> null
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DisciplineTheme(darkOverride = dark) {
                Box(Modifier.fillMaxSize().background(OneUi.colors.background)) {
                    DisciplineAppUi(s)
                }
            }
        }
    }
}
