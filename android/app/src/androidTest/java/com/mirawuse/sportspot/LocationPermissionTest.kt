package com.mirawuse.sportspot

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityCompat
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class LocationPermissionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun locationExplanationAppearsOnlyAfterUserActionAndCanBeDismissed() {
        val activity = compose.activity
        val permission = Manifest.permission.ACCESS_COARSE_LOCATION
        // This scenario applies to a fresh install or a retryable permission denial.
        val requested = activity.getSharedPreferences("location_permission", 0).getBoolean("requested", false)
        assumeTrue(activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
        assumeTrue(!requested || ActivityCompat.shouldShowRequestPermissionRationale(activity, permission))

        compose.onNodeWithText("Доступ к местоположению").assertDoesNotExist()
        compose.onNodeWithText("Найти рядом со мной").performClick()
        compose.onNodeWithText("Доступ к местоположению").assertIsDisplayed()
        compose.onNodeWithText("Продолжить").assertIsDisplayed()
        compose.onNodeWithText("Не сейчас").performClick()
        compose.onNodeWithText("Доступ к местоположению").assertDoesNotExist()
        compose.onNodeWithText("Найти рядом со мной").assertIsDisplayed()
    }
}
