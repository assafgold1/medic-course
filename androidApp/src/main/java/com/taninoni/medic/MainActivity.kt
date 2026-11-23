package com.taninoni.medic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.taninoni.medic.ui.App
import com.taninoni.medic.ui.AppBack

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Every screen starts with the orange brand header, so status bar icons are always light.
        enableEdgeToEdge(statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        AndroidContext.app = applicationContext
        AndroidContext.activity = this
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (AppBack.handler?.invoke() != true) finish()
            }
        })
        setContent { App() }
    }

    override fun onDestroy() {
        if (AndroidContext.activity === this) AndroidContext.activity = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in ComponentActivity")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == AndroidContext.NOTIFY_PERMISSION_REQUEST) {
            AndroidContext.permissionCallback?.invoke(grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED)
            AndroidContext.permissionCallback = null
        }
    }
}
