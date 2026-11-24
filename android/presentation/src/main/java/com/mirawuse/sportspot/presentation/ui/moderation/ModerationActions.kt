package com.mirawuse.sportspot.presentation.ui.moderation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButton
import com.mirawuse.sportspot.presentation.ui.components.SportSpotButtonVariant
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun ModerationHeading(
    title: String,
    icon: Int? = null,
    action: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.25f) {
            Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
                ModerationTitle(title, icon)
                action()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModerationTitle(title, icon, Modifier.weight(1f))
                action()
            }
        }
    }
}

@Composable
private fun ModerationTitle(
    title: String,
    icon: Int?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(painterResource(icon), contentDescription = null)
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
internal fun ModerationDecisionButtons(
    enabled: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 240.dp || LocalDensity.current.fontScale > 1.25f) {
            Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm)) {
                DecisionButton(Modifier.fillMaxWidth(), true, enabled, onApprove)
                DecisionButton(Modifier.fillMaxWidth(), false, enabled, onReject)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
            ) {
                DecisionButton(Modifier.weight(1f).fillMaxHeight(), true, enabled, onApprove)
                DecisionButton(Modifier.weight(1f).fillMaxHeight(), false, enabled, onReject)
            }
        }
    }
}

@Composable
private fun DecisionButton(
    modifier: Modifier,
    approve: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SportSpotButton(
        text = stringResource(if (approve) R.string.moderation_approve else R.string.moderation_reject),
        onClick = onClick,
        modifier = modifier,
        variant = if (approve) SportSpotButtonVariant.PRIMARY else SportSpotButtonVariant.DANGER,
        enabled = enabled,
    )
}

@Composable
internal fun ModerationField(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
