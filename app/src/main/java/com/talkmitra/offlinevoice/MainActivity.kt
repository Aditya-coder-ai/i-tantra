package com.talkmitra.offlinevoice

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.talkmitra.offlinevoice.network.PermissionHelper
import com.talkmitra.offlinevoice.ui.VoiceLinkApp
import com.talkmitra.offlinevoice.ui.theme.VoiceLinkTheme

class MainActivity : ComponentActivity() {

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            val denied = permissions.filter { !it.value }.keys.map { it.substringAfterLast('.') }
            Toast.makeText(
                this,
                "VoiceLink requires permissions for offline radio discovery: ${denied.joinToString()}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()
        
        setContent { 
            VoiceLinkTheme { 
                VoiceLinkApp() 
            } 
        }
    }

    override fun onResume() {
        super.onResume()
        if (!PermissionHelper.hasAllPermissions(this)) {
            checkAndRequestPermissions()
        }
    }

    private fun checkAndRequestPermissions() {
        val missing = PermissionHelper.getMissingPermissions(this)
        if (missing.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(missing.toTypedArray())
        }
    }
}
