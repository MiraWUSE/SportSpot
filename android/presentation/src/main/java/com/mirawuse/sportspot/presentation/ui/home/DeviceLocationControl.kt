package com.mirawuse.sportspot.presentation.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotSpacing

@Composable
internal fun DeviceLocationControl(
    state: SearchLocationState,
    onLocate: (Boolean) -> Unit,
    onDenied: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val owner = LocalLifecycleOwner.current
    val actionColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    val permissions =
        remember {
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        }
    // Store only request history, so a permanent denial also works after a process restart.
    val history = remember(context) { context.getSharedPreferences("location_permission", Context.MODE_PRIVATE) }
    var explanation by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }

    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (permissions.any { granted(it) }) {
                onLocate(!granted(Manifest.permission.ACCESS_FINE_LOCATION))
            } else {
                onDenied()
            }
        }
    DisposableEffect(owner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) onCancel()
            }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            onCancel()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SportSpotSpacing.xs),
            ) {
                Icon(
                    painterResource(R.drawable.ic_demo_location),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(state.labelRes), style = MaterialTheme.typography.labelMedium)
            }
            TextButton(
                modifier = Modifier.heightIn(min = 48.dp),
                enabled = !state.locating,
                contentPadding = PaddingValues(horizontal = SportSpotSpacing.xs),
                colors = actionColors,
                onClick = {
                    if (permissions.any { granted(it) }) {
                        onLocate(!granted(Manifest.permission.ACCESS_FINE_LOCATION))
                    } else if (activity != null) {
                        val rationale = permissions.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
                        if (history.getBoolean("requested", false) && !rationale) {
                            settings = true
                        } else {
                            explanation = true
                        }
                    }
                },
            ) {
                Text(
                    stringResource(if (state.locating) R.string.location_locating else R.string.location_find),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        state.message?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
    }
    if (explanation) {
        AlertDialog(
            onDismissRequest = { explanation = false },
            title = { Text(stringResource(R.string.location_permission_title)) },
            text = { Text(stringResource(R.string.location_rationale)) },
            confirmButton = {
                TextButton(onClick = {
                    explanation = false
                    history.edit().putBoolean("requested", true).apply()
                    launcher.launch(permissions)
                }, colors = actionColors) { Text(stringResource(R.string.location_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { explanation = false }, colors = actionColors) { Text(stringResource(R.string.location_later)) }
            },
        )
    }
    if (settings) {
        AlertDialog(
            onDismissRequest = { settings = false },
            title = { Text(stringResource(R.string.location_permission_title)) },
            text = { Text(stringResource(R.string.location_settings_message)) },
            confirmButton = {
                TextButton(onClick = {
                    settings = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }, colors = actionColors) { Text(stringResource(R.string.location_settings)) }
            },
            dismissButton = {
                TextButton(onClick = { settings = false }, colors = actionColors) { Text(stringResource(R.string.location_later)) }
            },
        )
    }
}
