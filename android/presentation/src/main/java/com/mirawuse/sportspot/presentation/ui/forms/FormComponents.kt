package com.mirawuse.sportspot.presentation.ui.forms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.validation.FormError
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun FormScreen(
    title: String,
    onBack: (() -> Unit)? = null,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .padding(horizontal = if (onBack == null) SportSpotSpacing.xl else SportSpotSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs),
        ) {
            if (onBack != null) {
                val back = stringResource(R.string.details_back)
                IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = back }) {
                    Text("‹", style = MaterialTheme.typography.headlineLarge)
                }
            }
            Text(
                title,
                modifier = if (onBack == null) Modifier else Modifier.padding(SportSpotSpacing.sm),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        if (scrollable) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(SportSpotSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.lg),
                content = content,
            )
        } else {
            content()
        }
    }
}

@Composable
internal fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: FormError? = null,
    hint: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    obscure: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            placeholder = hint?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
            isError = error != null,
            supportingText = error?.let { { FormErrorText(it) } },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 4,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = if (obscure) PasswordVisualTransformation() else VisualTransformation.None,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surface,
                    unfocusedContainerColor = colors.surface,
                    errorContainerColor = colors.surface,
                    focusedBorderColor = colors.onSurfaceVariant,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = colors.onSurface,
                ),
        )
    }
}

@Composable
internal fun FormErrorText(error: FormError) {
    val message =
        when (error) {
            FormError.REQUIRED -> R.string.form_required
            FormError.EMAIL_FORMAT -> R.string.form_email_format
            FormError.RATING_REQUIRED -> R.string.form_rating_required
            FormError.SPORT_REQUIRED -> R.string.form_sport_required
            FormError.COORDINATE_FORMAT -> R.string.form_coordinate_format
            FormError.COORDINATE_RANGE -> R.string.form_coordinate_range
        }
    Text(stringResource(message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun FormCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(SportSpotSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.sm),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}
