package com.yinnho.upnpcast.demo

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.toColorInt
import com.yinnho.upnpcast.DLNACast

/**
 * 🚀 UPnPCast Demo - Professional Splash Screen
 * Demonstrates modern splash screen implementation with auto-redirect
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide status bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.windowInsetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        }
        
        // Create layout
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
            setBackgroundColor("#F5F5F5".toColorInt())
        }
        
        // Logo text
        val logoView = TextView(this).apply {
            text = "📡"
            textSize = 80f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 30)
        }
        layout.addView(logoView)
        
        // Title
        val titleView = TextView(this).apply {
            text = "UPnPCast Demo"
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor("#333333".toColorInt())
            gravity = Gravity.CENTER
        }
        layout.addView(titleView)
        
        // Subtitle
        val subtitleView = TextView(this).apply {
            text = "Professional DLNA Casting Library"
            textSize = 16f
            setTextColor("#666666".toColorInt())
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 40)
        }
        layout.addView(subtitleView)
        
        // Version info
        val versionView = TextView(this).apply {
            text = "Version 1.1.0"
            textSize = 14f
            setTextColor("#999999".toColorInt())
            gravity = Gravity.CENTER
        }
        layout.addView(versionView)
        
        setContentView(layout)
        
        // 🚀 Initialize UPnPCast in background
        Thread {
            try {
                DLNACast.init(this@SplashActivity)
                Thread.sleep(2000) // Display for 2 seconds
                
                runOnUiThread {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }
            }
        }.start()
    }
} 