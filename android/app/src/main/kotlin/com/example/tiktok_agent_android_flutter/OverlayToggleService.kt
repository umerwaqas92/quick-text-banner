package com.example.tiktok_agent_android_flutter

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView

class OverlayToggleService : Service() {
    private var windowManager: WindowManager? = null
    private var toggleView: View? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_TOGGLE -> showToggle()
            ACTION_HIDE_TOGGLE -> hideToggle()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        hideToggle()
    }

    private fun showToggle() {
        if (toggleView != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val view = TextView(this).apply {
            text = "AI"
            textSize = 18f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC1565C0"))
            setPadding(44, 32, 44, 32)
            setOnClickListener {
                val action = if (FloatingBannerService.isVisible()) {
                    FloatingBannerService.ACTION_HIDE
                } else {
                    FloatingBannerService.ACTION_SHOW
                }
                val i = Intent(this@OverlayToggleService, FloatingBannerService::class.java)
                i.action = action
                if (action == FloatingBannerService.ACTION_SHOW) {
                    val prefs = getSharedPreferences("quick_text_settings", MODE_PRIVATE)
                    val savedRows = prefs.getInt("rows", 2).coerceIn(1, 4)
                    val savedCategoryRows = prefs.getInt("category_rows", 1).coerceIn(1, 3)
                    val savedCompact = prefs.getBoolean("compact_mode", false)
                    val savedAiChipsEnabled = prefs.getBoolean("ai_chips_enabled", true)
                    val savedPrompt = prefs.getString("extra_prompt", "").orEmpty()
                    val savedPlatform = prefs.getString("platform", "TikTok").orEmpty()
                    val savedCustom = prefs.getStringSet("custom_actions", emptySet())?.toList() ?: emptyList()
                    val savedStatic = prefs.getStringSet("static_categories", emptySet())?.toList() ?: emptyList()

                    val staticCategories = if (FloatingBannerService.lastStaticCategories.isNotEmpty()) {
                        FloatingBannerService.lastStaticCategories
                    } else {
                        savedStatic
                    }
                    val customActions = if (FloatingBannerService.lastCustomActions.isNotEmpty()) {
                        FloatingBannerService.lastCustomActions
                    } else {
                        savedCustom
                    }

                    i.putStringArrayListExtra(FloatingBannerService.EXTRA_TEXTS, ArrayList(FloatingBannerService.lastTexts))
                    i.putExtra(FloatingBannerService.EXTRA_ROWS, if (FloatingBannerService.lastRows > 0) FloatingBannerService.lastRows else savedRows)
                    i.putExtra(
                        FloatingBannerService.EXTRA_COMPACT_MODE,
                        if (FloatingBannerService.lastRows > 0) FloatingBannerService.lastCompactMode else savedCompact
                    )
                    i.putExtra(
                        FloatingBannerService.EXTRA_USER_PROMPT,
                        if (FloatingBannerService.lastUserPrompt.isNotBlank()) FloatingBannerService.lastUserPrompt else savedPrompt
                    )
                    i.putExtra(
                        FloatingBannerService.EXTRA_PLATFORM,
                        if (FloatingBannerService.lastPlatform.isNotBlank()) FloatingBannerService.lastPlatform else savedPlatform
                    )
                    i.putExtra(
                        FloatingBannerService.EXTRA_AI_CHIPS_ENABLED,
                        if (FloatingBannerService.lastRows > 0) FloatingBannerService.lastAiChipsEnabled else savedAiChipsEnabled
                    )
                    i.putExtra(
                        FloatingBannerService.EXTRA_CATEGORY_ROWS,
                        if (FloatingBannerService.lastCategoryRows > 0) FloatingBannerService.lastCategoryRows else savedCategoryRows
                    )
                    i.putStringArrayListExtra(FloatingBannerService.EXTRA_CUSTOM_ACTIONS, ArrayList(customActions))
                    i.putStringArrayListExtra(FloatingBannerService.EXTRA_STATIC_CATEGORIES, ArrayList(staticCategories))
                    Log.d("OverlayToggleService", "FAB SHOW clicked: categories=${staticCategories.size}")
                }
                startService(i)
            }
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 320
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    params?.x = initialX + (event.rawX - initialTouchX).toInt()
                    params?.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }

                else -> false
            }
        }

        windowManager?.addView(view, params)
        toggleView = view
    }

    private fun hideToggle() {
        toggleView?.let {
            windowManager?.removeView(it)
        }
        toggleView = null
    }

    companion object {
        const val ACTION_SHOW_TOGGLE = "com.example.tiktok_agent_android_flutter.SHOW_TOGGLE"
        const val ACTION_HIDE_TOGGLE = "com.example.tiktok_agent_android_flutter.HIDE_TOGGLE"
    }
}
