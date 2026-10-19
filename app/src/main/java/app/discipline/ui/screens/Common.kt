package app.discipline.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.discipline.AppContainer
import app.discipline.container
import app.discipline.data.Settings
import app.discipline.ui.DayUi
import app.discipline.ui.components.IconBadge
import app.discipline.ui.dayUiFlow
import app.discipline.ui.theme.OneUi
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun appContainer(): AppContainer = LocalContext.current.container

@Composable
fun rememberSettings(): Settings? {
    val c = appContainer()
    val s by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    return s
}

@Composable
fun rememberDayUi(day: LocalDate): DayUi? {
    val c = appContainer()
    val flow = remember(day) { dayUiFlow(c, flowOf(day)) }
    val state by flow.collectAsStateWithLifecycle(initialValue = null)
    return state
}

fun friendlyDate(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    today.plusDays(1) -> "Tomorrow"
    else -> day.format(DateTimeFormatter.ofPattern("EEE, d MMM"))
}

/** "Monday, 19 October", with the year only when it isn't this year. */
fun longDate(day: LocalDate): String =
    day.format(DateTimeFormatter.ofPattern(if (day.year == LocalDate.now().year) "EEEE, d MMMM" else "EEEE, d MMMM yyyy"))

/** Half-width rounded tile used on the dashboard. */
@Composable
fun RowScope.Tile(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
    bottom: @Composable () -> Unit = {},
) {
    val c = OneUi.colors
    Column(
        modifier
            .weight(1f)
            .clip(OneUi.ContainerShape)
            .background(c.container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint, size = 30.dp)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(14.dp))
        Text(value, style = MaterialTheme.typography.headlineMedium, color = c.textPrimary, maxLines = 1)
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        bottom()
    }
}

@Composable
fun TileRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = OneUi.ScreenPadding).padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

fun Double.fmt(): String = if (this % 1.0 == 0.0) toLong().toString() else "%.2f".format(this).trimEnd('0').trimEnd('.')
