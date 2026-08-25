package com.itantra.offlinevoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.itantra.offlinevoice.ui.VoiceLinkApp
import com.itantra.offlinevoice.ui.theme.VoiceLinkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VoiceLinkTheme { VoiceLinkApp() } }
    }
}
