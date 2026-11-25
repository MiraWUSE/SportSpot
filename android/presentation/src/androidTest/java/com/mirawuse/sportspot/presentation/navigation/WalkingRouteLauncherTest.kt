package com.mirawuse.sportspot.presentation.navigation

import android.content.ActivityNotFoundException
import android.content.Intent
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.presentation.ui.navigation.WalkingRouteLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WalkingRouteLauncherTest {
    @Test
    fun sendsAnImplicitIntentAndIgnoresQuickRepeatedTaps() {
        val intents = mutableListOf<Intent>()
        var now = 0L
        val launcher = WalkingRouteLauncher({ intents.add(it) }, { error("Unexpected failure") }) { now }
        val first = GeoPoint(56.3269, 44.0059)
        val second = GeoPoint(56.4, 44.1)

        launcher.open(first)
        now = 100L
        launcher.open(first)
        assertEquals(1, intents.size)
        val intent = intents.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertNull(intent.`package`)
        assertNull(intent.component)
        assertEquals("56.3269,44.0059", intent.data?.getQueryParameter("destination"))
        assertNull(intent.data?.getQueryParameter("origin"))

        now = 800L
        launcher.open(second)
        assertEquals("56.4,44.1", intents[1].data?.getQueryParameter("destination"))
    }

    @Test
    fun reportsMissingAndBlockedHandlersWithoutThrowing() {
        val failures = ArrayDeque(listOf(ActivityNotFoundException(), SecurityException()))
        var messages = 0
        var now = 0L
        val launcher = WalkingRouteLauncher({ throw failures.removeFirst() }, { messages++ }) { now }

        launcher.open(GeoPoint(56.3269, 44.0059))
        now = 800L
        launcher.open(GeoPoint(56.3269, 44.0059))

        assertEquals(2, messages)
    }
}
