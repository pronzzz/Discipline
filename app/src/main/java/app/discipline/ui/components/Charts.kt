package app.discipline.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.discipline.ui.theme.OneUi
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ChartPoint(val date: LocalDate, val value: Double)

/** Smooth line chart with gradient fill and optional dashed target line. */
@Composable
fun LineChart(
    points: List<ChartPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    target: Double? = null,
    from: LocalDate,
    to: LocalDate,
) {
    val c = OneUi.colors
    val reveal = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { reveal.animateTo(1f, tween(800)) }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            if (points.isEmpty()) return@Canvas
            val values = points.map { it.value } + listOfNotNull(target)
            var lo = values.min(); var hi = values.max()
            if (hi - lo < 1.0) { lo -= 1.0; hi += 1.0 }
            val pad = (hi - lo) * 0.12
            lo -= pad; hi += pad
            val span = (to.toEpochDay() - from.toEpochDay()).coerceAtLeast(1).toFloat()
            fun x(d: LocalDate) = (d.toEpochDay() - from.toEpochDay()) / span * size.width
            fun y(v: Double) = (size.height - ((v - lo) / (hi - lo) * size.height)).toFloat()

            // gridlines
            for (i in 0..3) {
                val gy = size.height * i / 3f
                drawLine(c.divider, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }
            target?.let {
                drawLine(
                    c.success, Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 3f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)),
                )
            }
            val pts = points.sortedBy { it.date }.map { Offset(x(it.date), y(it.value)) }
            val line = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                for (i in 1 until pts.size) {
                    val p0 = pts[i - 1]; val p1 = pts[i]
                    val mx = (p0.x + p1.x) / 2
                    cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                }
            }
            clipRectReveal(reveal.value) {
                val fill = Path().apply {
                    addPath(line)
                    lineTo(pts.last().x, size.height)
                    lineTo(pts.first().x, size.height)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
                drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                pts.lastOrNull()?.let {
                    drawCircle(c.container, 6.dp.toPx(), it)
                    drawCircle(color, 4.dp.toPx(), it)
                }
            }
        }
        AxisLabels(from, to)
    }
}

/** Daily bars against a goal line. Bars over goal turn [overColor]. */
@Composable
fun BarChart(
    points: List<ChartPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    goal: Double? = null,
    from: LocalDate,
    to: LocalDate,
    overColor: Color = OneUi.colors.exercise,
) {
    val c = OneUi.colors
    val grow = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { grow.animateTo(1f, tween(700)) }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val days = (to.toEpochDay() - from.toEpochDay() + 1).toInt().coerceAtLeast(1)
            val max = (points.maxOfOrNull { it.value } ?: 0.0).coerceAtLeast(goal ?: 0.0).coerceAtLeast(1.0) * 1.1
            val slot = size.width / days
            val bw = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            val byDay = points.associateBy { it.date }
            for (i in 0 until days) {
                val d = from.plusDays(i.toLong())
                val v = byDay[d]?.value ?: continue
                val h = (v / max * size.height).toFloat() * grow.value
                val left = i * slot + (slot - bw) / 2
                val over = goal != null && v > goal * 1.05
                drawRoundRect(
                    if (over) overColor else color,
                    topLeft = Offset(left, size.height - h),
                    size = Size(bw, h),
                    cornerRadius = CornerRadius(bw / 2, bw / 2),
                )
            }
            goal?.let {
                val gy = size.height - (it / max * size.height).toFloat()
                drawLine(c.textTertiary, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }
        }
        AxisLabels(from, to)
    }
}

@Composable
private fun AxisLabels(from: LocalDate, to: LocalDate) {
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(from.format(fmt), style = MaterialTheme.typography.labelSmall, color = OneUi.colors.textTertiary, modifier = Modifier.weight(1f))
        Text(to.format(fmt), style = MaterialTheme.typography.labelSmall, color = OneUi.colors.textTertiary)
    }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.clipRectReveal(
    fraction: Float,
    block: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit,
) = drawContext.canvas.let {
    it.save()
    it.clipRect(0f, -20f, size.width * fraction + 20f, size.height + 20f)
    block()
    it.restore()
}
