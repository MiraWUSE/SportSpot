package com.mirawuse.sportspot.data.local.preferences

import android.content.ContextWrapper
import com.mirawuse.sportspot.data.di.PreferencesModule
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PreferencesModuleTest {
    @Test
    fun `the module returns one DataStore for application and wrapped contexts`() {
        val application = RuntimeEnvironment.getApplication()

        assertSame(
            PreferencesModule.providePreferencesDataStore(application),
            PreferencesModule.providePreferencesDataStore(ContextWrapper(application)),
        )
    }
}
