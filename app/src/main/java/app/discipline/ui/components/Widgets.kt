package app.discipline.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.discipline.ui.theme.OneUi

/** Small section label shown above a rounded group. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = OneUi.colors.textSecondary,
        modifier = modifier.padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** One UI rounded container. */
@Composable
fun Group(
    modifier: Modifier = Modifier,
    label: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (label != null) SectionLabel(label) else Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .padding(horizontal = OneUi.ScreenPadding)
                .fillMaxWidth()
                .clip(OneUi.ContainerShape)
                .background(OneUi.colors.container)
                .padding(contentPadding),
            content = content,
        )
    }
}

@Composable
fun GroupDivider(inset: Dp = 24.dp) {
    HorizontalDivider(Modifier.padding(start = inset, end = 24.dp), thickness = 0.7.dp, color = OneUi.colors.divider)
}

/** Coloured rounded-square icon, One UI settings style. */
@Composable
fun IconBadge(icon: ImageVector, tint: Color, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(androidx.compose.foundation.shape.RoundedCornerShape(size * 0.32f)).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.58f))
    }
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    summaryColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val c = OneUi.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) c.textPrimary else c.textTertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary != null) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = summaryColor ?: c.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun OneUiSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, enabled: Boolean = true) {
    val c = OneUi.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = c.accent,
            checkedBorderColor = c.accent,
            uncheckedThumbColor = if (c.isDark) Color(0xFFBDBDC2) else Color.White,
            uncheckedTrackColor = if (c.isDark) Color(0xFF3A3A3E) else Color(0xFFC9C9CE),
            uncheckedBorderColor = Color.Transparent,
        ),
        thumbContent = null,
    )
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    ListRow(
        title = title,
        summary = summary,
        leading = leading,
        enabled = enabled,
        onClick = { haptics.performHapticFeedback(HapticFeedbackType.ToggleOn); onCheckedChange(!checked) },
        trailing = { OneUiSwitch(checked, null, enabled) },
    )
}

@Composable
fun RadioRow(title: String, selected: Boolean, onClick: () -> Unit, summary: String? = null) {
    ListRow(
        title = title,
        summary = summary,
        onClick = onClick,
        leading = {
            RadioButton(
                selected = selected, onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = OneUi.colors.accent, unselectedColor = OneUi.colors.textTertiary),
            )
        },
    )
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = OneUi.colors.accent,
    content: Color = Color.White,
    leadingIcon: ImageVector? = null,
) {
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onClick() },
        modifier = modifier.heightIn(min = 52.dp),
        enabled = enabled,
        shape = OneUi.PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container, contentColor = content,
            disabledContainerColor = OneUi.colors.containerHigh, disabledContentColor = OneUi.colors.textTertiary,
        ),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun TonalPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, leadingIcon: ImageVector? = null, enabled: Boolean = true) =
    PillButton(text, onClick, modifier, enabled, container = OneUi.colors.containerHigh, content = OneUi.colors.accent, leadingIcon = leadingIcon)

@Composable
fun AccentTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = OneUi.colors.accent) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

/** Bottom action area for pushed screens. */
@Composable
fun BottomActionBar(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) { content() }
}

/** Circular progress ring with rounded caps; [progress] can exceed 1 (over goal turns the ring [overColor]). */
@Composable
fun Ring(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 14.dp,
    track: Color = OneUi.colors.track,
    overColor: Color = OneUi.colors.danger,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    val over = progress > 1f
    val ringColor by animateColorAsState(if (over) overColor else color, label = "ringColor")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSizeCompat()) {
            val sw = stroke.toPx()
            val d = size.minDimension - sw
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(sw))
            if (animated > 0f) {
                drawArc(ringColor, -90f, 360f * animated, false, topLeft, Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

private fun Modifier.matchParentSizeCompat() = this.then(Modifier.fillMaxWidth().fillMaxHeight())

/** Horizontal rounded meter. */
@Composable
fun Meter(progress: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "meter")
    val c = OneUi.colors
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(c.track)) {
        Box(
            Modifier.fillMaxWidth(animated).height(height).clip(CircleShape)
                .background(if (progress > 1.001f) c.danger else color)
        )
    }
}

/** One UI segmented "tabs" control. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = OneUi.colors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier.fillMaxWidth().clip(OneUi.PillShape).background(c.containerHigh).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { opt ->
            val isSel = opt == selected
            val bg by animateColorAsState(if (isSel) c.container else Color.Transparent, label = "seg")
            Box(
                Modifier.weight(1f).clip(OneUi.PillShape).background(bg)
                    .clickable { if (!isSel) { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onSelect(opt) } }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(opt),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium),
                    color = if (isSel) c.textPrimary else c.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Filled rounded text field, One UI style. */
@Composable
fun OneUiField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    isError: Boolean = false,
    placeholder: String? = null,
) {
    val c = OneUi.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = c.textTertiary) } },
        suffix = suffix?.let { { Text(it, color = c.textSecondary) } },
        singleLine = singleLine,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = OneUi.InnerShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.containerHigh,
            unfocusedContainerColor = c.containerHigh,
            errorContainerColor = c.containerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.textSecondary,
            cursorColor = c.accent,
        ),
    )
}

/** Big number + caption, used inside cards. */
@Composable
fun Stat(value: String, caption: String, modifier: Modifier = Modifier, color: Color = OneUi.colors.textPrimary, align: Alignment.Horizontal = Alignment.Start) {
    Column(modifier, horizontalAlignment = align) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = color, maxLines = 1)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = OneUi.colors.textSecondary, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = OneUi.colors.textTertiary,
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
    )
}
