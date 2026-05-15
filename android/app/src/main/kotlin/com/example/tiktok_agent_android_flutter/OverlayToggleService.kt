package com.example.tiktok_agent_android_flutter

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
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
                    i.putStringArrayListExtra(FloatingBannerService.EXTRA_TEXTS, ArrayList(FloatingBannerService.lastTexts))
                    i.putExtra(FloatingBannerService.EXTRA_ROWS, FloatingBannerService.lastRows)
                    i.putExtra(FloatingBannerService.EXTRA_COMPACT_MODE, FloatingBannerService.lastCompactMode)
                    i.putExtra(FloatingBannerService.EXTRA_USER_PROMPT, FloatingBannerService.lastUserPrompt)
                    i.putExtra(FloatingBannerService.EXTRA_PLATFORM, FloatingBannerService.lastPlatform)
                    i.putStringArrayListExtra(FloatingBannerService.EXTRA_CUSTOM_ACTIONS, ArrayList(FloatingBannerService.lastCustomActions))
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
