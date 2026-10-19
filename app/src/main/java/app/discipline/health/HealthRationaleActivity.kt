package app.discipline.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.discipline.ui.components.BottomActionBar
import app.discipline.ui.components.Group
import app.discipline.ui.components.GroupDivider
import app.discipline.ui.components.IconBadge
import app.discipline.ui.components.ListRow
import app.discipline.ui.components.OneUiScreen
import app.discipline.ui.components.PillButton
import app.discipline.ui.theme.DisciplineTheme
import app.discipline.ui.theme.OneUi

/**
 * Privacy rationale shown by Health Connect (the "privacy policy" link in its permission screen,
 * and the Android 14+ permission-usage entry point).
 */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DisciplineTheme {
                val c = OneUi.colors
                OneUiScreen(
                    title = "How Discipline uses your health data",
                    onBack = ::finish,
                    headerFraction = 0.30f,
                    bottomBar = { BottomActionBar { PillButton("Got it", onClick = ::finish, modifier = Modifier.fillMaxWidth()) } },
                ) {
                    item {
                        Group(label = "What we write") {
                            ListRow("Nutrition and water", summary = "Each food you log, with its meal and nutrients, and each drink of water.", leading = { IconBadge(Icons.Rounded.Restaurant, c.calories) })
                            GroupDivider(76.dp)
                            ListRow("Weight and exercise", summary = "Weigh-ins and workouts you enter, including calories burned.", leading = { IconBadge(Icons.Rounded.MonitorWeight, c.weight) })
                        }
                    }
                    item {
                        Group(label = "What we read") {
                            ListRow("Steps and active calories", summary = "To credit activity against your daily calorie goal.", leading = { IconBadge(Icons.AutoMirrored.Rounded.DirectionsWalk, c.steps) })
                            GroupDivider(76.dp)
                            ListRow("Workouts and weight", summary = "From other apps and smart scales, so you don't log them twice.", leading = { IconBadge(Icons.Rounded.FitnessCenter, c.exercise) })
                        }
                    }
                    item {
                        Group(label = "Your privacy") {
                            ListRow(
                                "Stays on this device",
                                summary = "Discipline has no servers and no account. Health data is never uploaded, sold or used for advertising. " +
                                    "You can revoke access or delete Discipline's records at any time in Health Connect.",
                                leading = { IconBadge(Icons.Rounded.Lock, c.success) },
                            )
                        }
                    }
                }
            }
        }
    }
}
