package com.personal.calisthenicsguide.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.calisthenicsguide.ui.theme.AppColors
import com.personal.calisthenicsguide.ui.theme.Space

private val CardShape = RoundedCornerShape(Space.corner)

/** The one card style of the app: a flat, slightly lighter panel with even padding and spacing. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    container: Color = AppColors.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier.fillMaxWidth().clip(CardShape).background(container)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Column(clickable.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm), content = content)
}

/** Screen title with an optional one-line subtitle. */
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
    }
}

/** Small muted heading above a group of cards. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = AppColors.TextSecondary) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = color, modifier = modifier)
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 56.dp, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = height),
        shape = CardShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = AppColors.Accent,
            contentColor = AppColors.AccentOn,
            disabledContainerColor = AppColors.SurfaceHigh,
            disabledContentColor = AppColors.TextSecondary,
        ),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 56.dp) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = height),
        shape = CardShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.TextPrimary),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

/** A card with a header row (title, one-line summary, chevron) that opens and closes its body. */
@Composable
fun CollapsibleCard(
    title: String,
    summary: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(AppColors.Surface)
            .animateContentSize(),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).heightIn(min = 64.dp).padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = AppColors.TextSecondary)
            }
            Text(if (expanded) "Hide" else "Show", style = MaterialTheme.typography.labelLarge, color = AppColors.Accent)
        }
        if (expanded) {
            Column(Modifier.padding(start = Space.lg, end = Space.lg, bottom = Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm), content = content)
        }
    }
}

/** Small rounded label, for example "Deload" or "Cold weather". */
@Composable
fun Pill(text: String, color: Color = AppColors.Accent, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = Space.sm, vertical = Space.xs),
    )
}
