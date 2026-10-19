package app.discipline.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.discipline.domain.ActivityLevel
import app.discipline.domain.GoalCalculator
import app.discipline.domain.MacroSplit
import app.discipline.domain.UnitSystem
import app.discipline.domain.Units
import app.discipline.ui.components.BottomActionBar
import app.discipline.ui.components.Group
import app.discipline.ui.components.OneUiScreen
import app.discipline.ui.components.PillButton
import app.discipline.ui.components.RadioRow
import app.discipline.ui.components.Segmented
import app.discipline.ui.components.Stat
import app.discipline.ui.theme.OneUi
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val c = appContainer()
    val s = rememberSettings() ?: return
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var pickingBirth by remember { mutableStateOf(false) }
    val draft = remember(s.units) { ProfileDraft(s) }
    val last = 5

    BackHandler(enabled = step > 0) { step-- }

    fun finish() {
        val p = draft.toProfile() ?: return
        scope.launch {
            c.settings.saveProfile(p)
            c.settings.setMacros(MacroSplit())
            c.diary.logWeight(LocalDate.now(), p.currentWeightKg)
            c.settings.setOnboarded()
            onDone()
        }
    }

    AnimatedContent(
        targetState = step,
        transitionSpec = { (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(220))) togetherWith fadeOut(tween(120)) },
        label = "onboarding",
    ) { current ->
        when (current) {
            0 -> Welcome(onNext = { step = 1 })
            1 -> OneUiScreen(
                title = "What's your goal?",
                subtitle = "Step 1 of 4",
                onBack = { step-- },
                headerFraction = 0.30f,
                bottomBar = { BottomActionBar { PillButton("Next", onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) } },
            ) {
                item {
                    Group {
                        WeeklyOptions.forEach { kg -> RadioRow(weeklyLabel(kg, s.units), draft.weekly == kg, onClick = { draft.weekly = kg }) }
                    }
                }
            }
            2 -> OneUiScreen(
                title = "About you",
                subtitle = "Step 2 of 4 · used to estimate your energy needs",
                onBack = { step-- },
                headerFraction = 0.26f,
                bottomBar = {
                    BottomActionBar { PillButton("Next", enabled = draft.toProfile() != null, onClick = { step = 3 }, modifier = Modifier.fillMaxWidth()) }
                },
            ) {
                item {
                    Segmented(
                        listOf(UnitSystem.METRIC, UnitSystem.IMPERIAL), s.units,
                        { if (it == UnitSystem.METRIC) "kg · cm" else "lb · in" },
                        { scope.launch { c.settings.setUnits(it) } },
                        Modifier.padding(horizontal = 16.dp),
                    )
                }
                item { ProfileFields(draft, onPickBirth = { pickingBirth = true }, label = null) }
            }
            3 -> OneUiScreen(
                title = "How active are you?",
                subtitle = "Step 3 of 4 · not counting exercise",
                onBack = { step-- },
                headerFraction = 0.30f,
                bottomBar = { BottomActionBar { PillButton("See my plan", onClick = { step = 4 }, modifier = Modifier.fillMaxWidth()) } },
            ) {
                item {
                    Group {
                        ActivityLevel.entries.forEach { a -> RadioRow(a.label, draft.activity == a, onClick = { draft.activity = a }, summary = a.description) }
                    }
                }
            }
            4 -> {
                val p = draft.toProfile()
                OneUiScreen(
                    title = "Your plan",
                    subtitle = "Step 4 of 4 · you can change this any time",
                    onBack = { step-- },
                    headerFraction = 0.28f,
                    bottomBar = { BottomActionBar { PillButton("Continue", enabled = p != null, onClick = { step = last }, modifier = Modifier.fillMaxWidth()) } },
                ) {
                    if (p != null) {
                        val goals = GoalCalculator.goals(p, MacroSplit(), null, s.waterGoalMl, s.stepGoal)
                        item {
                            Group(contentPadding = PaddingValues(24.dp)) {
                                Text("Daily calorie goal", style = MaterialTheme.typography.bodyMedium, color = OneUi.colors.textSecondary)
                                Text(Units.kcal(goals.calories), style = MaterialTheme.typography.displayLarge, color = OneUi.colors.accent)
                                Text(weeklyLabel(p.weeklyChangeKg, s.units), style = MaterialTheme.typography.bodyLarge, color = OneUi.colors.textPrimary)
                                Spacer(Modifier.height(20.dp))
                                Row(Modifier.fillMaxWidth()) {
                                    Stat("${goals.carbsG} g", "Carbs", Modifier.weight(1f), color = OneUi.colors.carbs)
                                    Stat("${goals.proteinG} g", "Protein", Modifier.weight(1f), color = OneUi.colors.protein)
                                    Stat("${goals.fatG} g", "Fat", Modifier.weight(1f), color = OneUi.colors.fat)
                                }
                            }
                        }
                        item {
                            Group(contentPadding = PaddingValues(20.dp)) {
                                Text(
                                    "Your body burns about ${Units.kcal(goals.tdee)} kcal a day (BMR ${Units.kcal(goals.bmr)} × activity). " +
                                        (GoalCalculator.projectedGoalDate(p)?.let { "At this pace you could reach your goal weight by ${it.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy"))}." } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium, color = OneUi.colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
            else -> OneUiScreen(
                title = "Connect your health data",
                subtitle = "Optional",
                onBack = { step-- },
                headerFraction = 0.26f,
                bottomBar = {
                    BottomActionBar {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PillButton("Start tracking", onClick = ::finish, modifier = Modifier.fillMaxWidth())
                        }
                    }
                },
            ) {
                item { HealthConnectPanel(showToggles = false) }
            }
        }
    }

    if (pickingBirth) DayPicker(draft.birth, onDismiss = { pickingBirth = false }, onPicked = {
        pickingBirth = false
        if (it.isBefore(LocalDate.now().minusYears(12))) draft.birth = it
    })
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    val colors = OneUi.colors
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.TrackChanges, null, tint = Color.White, modifier = Modifier.size(54.dp))
                }
                Spacer(Modifier.height(28.dp))
                Text("Discipline", style = MaterialTheme.typography.displayLarge, color = colors.textPrimary)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Track calories, macros, water and weight.\nSimple, private, and synced with Health Connect.",
                    style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, textAlign = TextAlign.Center,
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Get started", onClick = onNext, modifier = Modifier.fillMaxWidth())
            Text(
                "Everything stays on your device. No account needed.",
                style = MaterialTheme.typography.bodySmall, color = colors.textTertiary, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

