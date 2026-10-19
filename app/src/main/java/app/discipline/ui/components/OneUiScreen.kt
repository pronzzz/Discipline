package app.discipline.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.discipline.ui.theme.OneUi

private val ToolbarHeight = 56.dp

/**
 * One UI page: a tall "viewing area" with a large title fills the top of the screen, content
 * starts in the lower, thumb-reachable half, and on scroll the title collapses into a compact
 * toolbar. Like One UI, the header snaps fully open or fully closed when a fling ends mid-way.
 */
@Composable
fun OneUiScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    headerFraction: Float = 0.32f,
    padNavigationBar: Boolean = true,
    bottomBar: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val colors = OneUi.colors
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(modifier.fillMaxSize().background(colors.background)) {
        val headerHeight: Dp = (maxHeight * headerFraction).coerceIn(180.dp, 340.dp)
        val collapseRangePx = with(density) { (headerHeight - ToolbarHeight).toPx() }.coerceAtLeast(1f)

        val progress by remember(listState, collapseRangePx) {
            derivedStateOf {
                if (listState.firstVisibleItemIndex > 0) 1f
                else (listState.firstVisibleItemScrollOffset / collapseRangePx).coerceIn(0f, 1f)
            }
        }

        // Snap the header open/closed when scrolling stops inside the collapse range.
        LaunchedEffect(listState, collapseRangePx) {
            snapshotFlowScrollStops(listState) {
                if (listState.firstVisibleItemIndex == 0) {
                    val offset = listState.firstVisibleItemScrollOffset.toFloat()
                    if (offset > 0f && offset < collapseRangePx) {
                        val target = if (offset < collapseRangePx / 2) -offset else collapseRangePx - offset
                        listState.animateScrollBy(target)
                    }
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().imePadding(),
                contentPadding = PaddingValues(
                    top = statusTop,
                    bottom = if (padNavigationBar && bottomBar == null) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp else 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                item(key = "__header") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(headerHeight)
                            .graphicsLayer { alpha = (1f - progress * 1.6f).coerceIn(0f, 1f) },
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        Column(Modifier.padding(start = 28.dp, end = 28.dp, bottom = 28.dp)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.displayMedium,
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (subtitle != null) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }
                }
                content()
            }
            if (bottomBar != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.background)
                        .then(if (padNavigationBar) Modifier.windowInsetsPadding(WindowInsets.navigationBars) else Modifier)
                        .imePadding(),
                ) { bottomBar() }
            }
        }

        // Compact toolbar overlay.
        val barAlpha by animateFloatAsState(if (progress > 0.98f) 1f else progress, label = "bar")
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.background.copy(alpha = barAlpha))
                .padding(top = statusTop)
                .height(ToolbarHeight)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                }
            } else {
                Box(Modifier.width(16.dp))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer { alpha = ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f) },
            )
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

private suspend fun snapshotFlowScrollStops(state: LazyListState, onStop: suspend () -> Unit) {
    androidx.compose.runtime.snapshotFlow { state.isScrollInProgress }
        .collect { scrolling -> if (!scrolling) onStop() }
}
