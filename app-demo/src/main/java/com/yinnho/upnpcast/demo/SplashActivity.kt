package com.yinnho.upnpcast.demo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.yinnho.upnpcast.DLNACast

/**
 * 🚀 UPnPCast Demo - 专业启动页
 */
class SplashActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 隐藏状态栏
        supportActionBar?.hide()
        
        // 创建布局
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1E1E1E"))
            setPadding(40, 40, 40, 40)
        }
        
        // Logo文字
        val logoView = TextView(this).apply {
            text = "🎯"
            textSize = 60f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        
        // 标题
        val titleView = TextView(this).apply {
            text = "UPnPCast Demo"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        
        // 副标题
        val subtitleView = TextView(this).apply {
            text = "Professional DLNA Casting Library"
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
        }
        
        // 版本信息
        val versionView = TextView(this).apply {
            text = "v1.0.0 | Built with UPnPCast API"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
        }
        
        layout.addView(logoView)
        layout.addView(titleView)
        layout.addView(subtitleView)
        layout.addView(versionView)
        
        setContentView(layout)
        
        // 🚀 在后台初始化UPnPCast
        Thread {
            try {
                DLNACast.init(this@SplashActivity)
                Thread.sleep(2000) // 展示2秒
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                Handler(Looper.getMainLooper()).post {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                }
            }
        }.start()
    }
} 