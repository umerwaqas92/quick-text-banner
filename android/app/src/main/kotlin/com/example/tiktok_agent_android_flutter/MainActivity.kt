package com.example.tiktok_agent_android_flutter

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "quick_text_banner"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "canDrawOverlays" -> result.success(canDrawOverlays())
                    "openOverlaySettings" -> {
                        openOverlaySettings()
                        result.success(true)
                    }

                    "isAccessibilityEnabled" -> result.success(isAccessibilityServiceEnabled())
                    "openAccessibilitySettings" -> {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        result.success(true)
                    }

                    "showOverlay" -> {
                        @Suppress("UNCHECKED_CAST")
                        val texts = call.argument<List<String>>("texts") ?: emptyList()
                        val rows = call.argument<Int>("rows") ?: 2
                        val compactMode = call.argument<Boolean>("compactMode") ?: false
                        val userPrompt = call.argument<String>("userPrompt") ?: ""
                        val platform = call.argument<String>("platform") ?: "TikTok"
                        val aiChipsEnabled = call.argument<Boolean>("aiChipsEnabled") ?: true
                        val categoryRows = call.argument<Int>("categoryRows") ?: 1
                        val apiKey = call.argument<String>("apiKey") ?: ""
                        @Suppress("UNCHECKED_CAST")
                        val customActions = call.argument<List<String>>("customActions") ?: emptyList()
                        @Suppress("UNCHECKED_CAST")
                        val staticCategories = call.argument<List<String>>("staticCategories") ?: emptyList()
                        persistRuntimeSettings(
                            rows = rows,
                            compactMode = compactMode,
                            userPrompt = userPrompt,
                            platform = platform,
                            aiChipsEnabled = aiChipsEnabled,
                            categoryRows = categoryRows,
                            apiKey = apiKey,
                            customActions = customActions,
                            staticCategories = staticCategories
                        )
                        val intent = Intent(this, FloatingBannerService::class.java).apply {
                            action = FloatingBannerService.ACTION_SHOW
                            putStringArrayListExtra(FloatingBannerService.EXTRA_TEXTS, ArrayList(texts))
                            putExtra(FloatingBannerService.EXTRA_ROWS, rows)
                            putExtra(FloatingBannerService.EXTRA_COMPACT_MODE, compactMode)
                            putExtra(FloatingBannerService.EXTRA_USER_PROMPT, userPrompt)
                            putExtra(FloatingBannerService.EXTRA_PLATFORM, platform)
                            putExtra(FloatingBannerService.EXTRA_AI_CHIPS_ENABLED, aiChipsEnabled)
                            putExtra(FloatingBannerService.EXTRA_CATEGORY_ROWS, categoryRows)
                            putStringArrayListExtra(FloatingBannerService.EXTRA_CUSTOM_ACTIONS, ArrayList(customActions))
                            putStringArrayListExtra(FloatingBannerService.EXTRA_STATIC_CATEGORIES, ArrayList(staticCategories))
                        }
                        startService(intent)
                        result.success(true)
                    }

                    "syncRuntimeSettings" -> {
                        val rows = call.argument<Int>("rows") ?: 2
                        val compactMode = call.argument<Boolean>("compactMode") ?: false
                        val userPrompt = call.argument<String>("userPrompt") ?: ""
                        val platform = call.argument<String>("platform") ?: "TikTok"
                        val aiChipsEnabled = call.argument<Boolean>("aiChipsEnabled") ?: true
                        val categoryRows = call.argument<Int>("categoryRows") ?: 1
                        val apiKey = call.argument<String>("apiKey") ?: ""
                        @Suppress("UNCHECKED_CAST")
                        val customActions = call.argument<List<String>>("customActions") ?: emptyList()
                        @Suppress("UNCHECKED_CAST")
                        val staticCategories = call.argument<List<String>>("staticCategories") ?: emptyList()
                        persistRuntimeSettings(
                            rows = rows,
                            compactMode = compactMode,
                            userPrompt = userPrompt,
                            platform = platform,
                            aiChipsEnabled = aiChipsEnabled,
                            categoryRows = categoryRows,
                            apiKey = apiKey,
                            customActions = customActions,
                            staticCategories = staticCategories
                        )
                        result.success(true)
                    }

                    "isBannerVisible" -> {
                        result.success(FloatingBannerService.isVisible())
                    }

                    "hideOverlay" -> {
                        val intent = Intent(this, FloatingBannerService::class.java).apply {
                            action = FloatingBannerService.ACTION_HIDE
                        }
                        startService(intent)
                        result.success(true)
                    }

                    "injectText" -> {
                        val text = call.argument<String>("text") ?: ""
                        result.success(TextInjectorAccessibilityService.injectText(text))
                    }

                    "getVisibleText" -> {
                        result.success(TextInjectorAccessibilityService.getVisibleText())
                    }


                    "showToggle" -> {
                        val i = Intent(this, OverlayToggleService::class.java).apply {
                            action = OverlayToggleService.ACTION_SHOW_TOGGLE
                        }
                        startService(i)
                        result.success(true)
                    }

                    "hideToggle" -> {
                        val i = Intent(this, OverlayToggleService::class.java).apply {
                            action = OverlayToggleService.ACTION_HIDE_TOGGLE
                        }
                        startService(i)
                        result.success(true)
                    }


                    "setAutoBannerEnabled" -> {
                        val enabled = call.argument<Boolean>("enabled") ?: false
                        val prefs = getSharedPreferences("quick_text_settings", MODE_PRIVATE)
                        prefs.edit().putBoolean("auto_banner_enabled", enabled).apply()
                        result.success(true)
                    }

                    "setAssistantEnabled" -> {
                        val enabled = call.argument<Boolean>("enabled") ?: false
                        val prefs = getSharedPreferences("quick_text_settings", MODE_PRIVATE)
                        prefs.edit().putBoolean("assistant_enabled", enabled).apply()
                        result.success(true)
                    }

                    "getAutoBannerEnabled" -> {
                        val prefs = getSharedPreferences("quick_text_settings", MODE_PRIVATE)
                        result.success(prefs.getBoolean("auto_banner_enabled", false))
                    }

                    "getAssistantEnabled" -> {
                        val prefs = getSharedPreferences("quick_text_settings", MODE_PRIVATE)
                        result.success(prefs.getBoolean("assistant_enabled", false))
                    }

                    "getApiLogs" -> {
                        val prefs = getSharedPreferences("quick_text_ai_logs", MODE_PRIVATE)
                        result.success(prefs.getString("entries", "[]"))
                    }

                    "clearApiLogs" -> {
                        val prefs = getSharedPreferences("quick_text_ai_logs", MODE_PRIVATE)
                        prefs.edit().putString("entries", "[]").apply()
                        result.success(true)
                    }

                    else -> result.notImplemented()
                }
            }
    }


    private fun isAccessibilityServiceEnabled(): Boolean {
        val globallyEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!globallyEnabled) return false

        val expected = ComponentName(this, TextInjectorAccessibilityService::class.java)
        val expectedFlat = expected.flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return TextUtils.SimpleStringSplitter(':').run {
            setString(enabled)
            any { raw ->
                val candidate = ComponentName.unflattenFromString(raw) ?: return@any false
                val className = candidate.className
                val normalizedClass = if (className.startsWith(".")) {
                    candidate.packageName + className
                } else {
                    className
                }
                val normalized = ComponentName(candidate.packageName, normalizedClass).flattenToString()
                normalized.equals(expectedFlat, ignoreCase = true)
            }
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    private fun persistRuntimeSettings(
        rows: Int,
        compactMode: Boolean,
        userPrompt: String,
        platform: String,
        aiChipsEnabled: Boolean,
        categoryRows: Int,
        apiKey: String,
        customActions: List<String>,
        staticCategories: List<String>
    ) {
        val prefs = getSharedPreferences("quick_text_settings", MODE_PRIVATE)
        prefs.edit()
            .putInt("rows", rows.coerceIn(1, 4))
            .putBoolean("compact_mode", compactMode)
            .putString("extra_prompt", userPrompt.trim())
            .putString("platform", platform)
            .putBoolean("ai_chips_enabled", aiChipsEnabled)
            .putInt("category_rows", categoryRows.coerceIn(1, 3))
            .putString("openrouter_api_key", apiKey.trim())
            .putStringSet("custom_actions", customActions.toSet())
            .putStringSet("static_categories", staticCategories.toSet())
            .apply()
    }
}
