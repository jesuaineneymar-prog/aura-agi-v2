package com.jc.aura

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ghost UI - no buttons, no interaction
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )

        setContentView(R.layout.activity_main)

        // Check if Accessibility Service is enabled
        if (!isAccessibilityServiceEnabled()) {
            // Guide user to enable it
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // Start the voice service
        val serviceIntent = Intent(this, AuraVoiceService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Fade out logo and finish in 1 second
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 1000)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityService = componentName
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(accessibilityService.flattenToString())
    }

    override fun onBackPressed() {
        // Do nothing - prevent exit
    }

    override fun onUserLeaveHint() {
        // Do nothing - prevent home button from stopping
    }
}
