package com.mirawuse.sportspot.presentation.ui.home

import android.Manifest
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DeviceLocationControlTest {
    @get:Rule
    val compose = createComposeRule()

    private val registry = PermissionRegistry()
    private val registryOwner =
        object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
    private val owner =
        object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
    private var locations = 0
    private var denials = 0

    @Before
    fun resetRequestHistory() {
        // This is the isolated library test host, not the SportSpot application.
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getSharedPreferences("location_permission", 0)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun interruptedPermissionRequestCanBeRetriedWithoutFalseDenial() {
        showControl()
        requestPermission()
        compose.runOnIdle { registry.respond(emptyMap()) }
        compose.runOnIdle {
            assertEquals(0, locations)
            assertEquals(0, denials)
        }
        requestPermission()
        compose.runOnIdle { registry.respond(mapOf(Manifest.permission.ACCESS_COARSE_LOCATION to false)) }
        compose.runOnIdle {
            assertEquals(0, locations)
            assertEquals(1, denials)
        }
        compose.onNodeWithText("Найти рядом со мной").performClick()
        compose.onNodeWithText("Открыть настройки").assertIsDisplayed()
        compose.onNodeWithText("Не сейчас").performClick()
        compose.onNodeWithText("Открыть настройки").assertDoesNotExist()
    }

    @Test
    fun permissionResultForStoppedScreenDoesNotReportDenialOrRestartOnResume() {
        showControl()
        requestPermission()
        compose.runOnIdle {
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            registry.respond(mapOf(Manifest.permission.ACCESS_COARSE_LOCATION to false))
            owner.lifecycle.currentState = Lifecycle.State.RESUMED
        }
        compose.runOnIdle {
            assertEquals(0, locations)
            assertEquals(0, denials)
        }
        compose.onNodeWithText("Найти рядом со мной").assertIsDisplayed()
    }

    private fun showControl() {
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner, LocalLifecycleOwner provides owner) {
                SportSpotTheme {
                    DeviceLocationControl(SearchLocationState(), onLocate = { locations++ }, onDenied = { denials++ }, onCancel = {})
                }
            }
        }
    }

    private fun requestPermission() {
        compose.onNodeWithText("Найти рядом со мной").performClick()
        compose.onNodeWithText("Продолжить").performClick()
    }

    private class PermissionRegistry : ActivityResultRegistry() {
        private var request = 0

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            request = requestCode
        }

        fun respond(result: Map<String, Boolean>) {
            dispatchResult(request, result)
        }
    }
}
