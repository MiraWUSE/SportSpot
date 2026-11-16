
package com.mirawuse.sportspot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mirawuse.sportspot.presentation.ui.navigation.SportSpotApp
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            SportSpotTheme {
                SportSpotApp()
            }
        }
    }
}
