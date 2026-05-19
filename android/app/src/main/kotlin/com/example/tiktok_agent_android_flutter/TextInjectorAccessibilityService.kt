package com.example.tiktok_agent_android_flutter

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED
import android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
import android.view.accessibility.AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
import android.view.accessibility.AccessibilityEvent.TYPE_VIEW_CLICKED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

class TextInjectorAccessibilityService : AccessibilityService() {
    private val autoBannerHandler = Handler(Looper.getMainLooper())
    private var pendingHideRunnable: Runnable? = null
    private var lastInputTriggerAt: Long = 0L
    private var lastEditableDetectedAt: Long = 0L
    private var lastShowAttemptAt: Long = 0L
    private var lastPackage: String = ""
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val prefs = getSharedPreferences("quick_text_settings", MODE_PRIVATE)
        val autoEnabled = prefs.getBoolean("auto_banner_enabled", false)
        val assistantEnabled = prefs.getBoolean("assistant_enabled", false)
        if (!autoEnabled || !assistantEnabled) return

        val type = event.eventType
        val pkg = event.packageName?.toString().orEmpty()
        if (pkg.isEmpty()) return

        if (type != TYPE_VIEW_FOCUSED &&
            type != TYPE_VIEW_TEXT_SELECTION_CHANGED &&
            type != TYPE_VIEW_TEXT_CHANGED &&
            type != TYPE_VIEW_CLICKED &&
            type != TYPE_WINDOW_CONTENT_CHANGED &&
            type != TYPE_WINDOW_STATE_CHANGED
        ) {
            return
        }

        val source = event.source
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val hasEditableFocus = focused?.isEditable == true

        val className = source?.className?.toString().orEmpty()
        val sourceEditable = source?.isEditable == true
        val looksLikeInputClass = className.contains("EditText", ignoreCase = true) ||
            className.contains("TextInput", ignoreCase = true)

        val isTextEvent = type == TYPE_VIEW_TEXT_CHANGED || type == TYPE_VIEW_TEXT_SELECTION_CHANGED
        val triggerShow = hasEditableFocus || sourceEditable || looksLikeInputClass || (isTextEvent && source != null)

        if (triggerShow) {
            val now = System.currentTimeMillis()
            lastInputTriggerAt = now
            lastEditableDetectedAt = now
            pendingHideRunnable?.let { autoBannerHandler.removeCallbacks(it) }
            pendingHideRunnable = null

            if (!FloatingBannerService.isVisible()) {
                val now = System.currentTimeMillis()
                if (now - lastShowAttemptAt < 350) {
                    return
                }
                lastShowAttemptAt = now

                val prefs = getSharedPreferences("quick_text_settings", MODE_PRIVATE)
                val savedRows = prefs.getInt("rows", 2).coerceIn(1, 4)
                val savedCompact = prefs.getBoolean("compact_mode", false)
                val savedPrompt = prefs.getString("extra_prompt", "").orEmpty()
                val savedPlatform = prefs.getString("platform", "TikTok").orEmpty()
                val savedCustom = prefs.getStringSet("custom_actions", emptySet())?.toList() ?: emptyList()
                val savedStatic = prefs.getStringSet("static_categories", emptySet())?.toList() ?: emptyList()

                val staticCategories = if (FloatingBannerService.lastStaticCategories.isNotEmpty()) {
                    FloatingBannerService.lastStaticCategories
                } else {
                    savedStatic
                }
                if (staticCategories.isEmpty()) {
                    return
                }
                val customActions = if (FloatingBannerService.lastCustomActions.isNotEmpty()) {
                    FloatingBannerService.lastCustomActions
                } else {
                    savedCustom
                }

                val i = Intent(this, FloatingBannerService::class.java).apply {
                    action = FloatingBannerService.ACTION_SHOW
                    putStringArrayListExtra(FloatingBannerService.EXTRA_TEXTS, ArrayList(FloatingBannerService.lastTexts))
                    putExtra(FloatingBannerService.EXTRA_ROWS, if (FloatingBannerService.lastRows > 0) FloatingBannerService.lastRows else savedRows)
                    putExtra(
                        FloatingBannerService.EXTRA_COMPACT_MODE,
                        if (FloatingBannerService.lastRows > 0) FloatingBannerService.lastCompactMode else savedCompact
                    )
                    putExtra(
                        FloatingBannerService.EXTRA_USER_PROMPT,
                        if (FloatingBannerService.lastUserPrompt.isNotBlank()) FloatingBannerService.lastUserPrompt else savedPrompt
                    )
                    putExtra(
                        FloatingBannerService.EXTRA_PLATFORM,
                        if (FloatingBannerService.lastPlatform.isNotBlank()) FloatingBannerService.lastPlatform else savedPlatform
                    )
                    putExtra(FloatingBannerService.EXTRA_AI_CHIPS_ENABLED, FloatingBannerService.lastAiChipsEnabled)
                    putExtra(FloatingBannerService.EXTRA_CATEGORY_ROWS, FloatingBannerService.lastCategoryRows)
                    putStringArrayListExtra(FloatingBannerService.EXTRA_CUSTOM_ACTIONS, ArrayList(customActions))
                    putStringArrayListExtra(FloatingBannerService.EXTRA_STATIC_CATEGORIES, ArrayList(staticCategories))
                }
                startService(i)
            }
        } else {
            if (type == TYPE_VIEW_TEXT_CHANGED) {
                return
            }
            pendingHideRunnable?.let { autoBannerHandler.removeCallbacks(it) }
            pendingHideRunnable = Runnable {
                val currentFocused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                val stillEditable = currentFocused?.isEditable == true
                val editableInTree = findFirstEditable(rootInActiveWindow) != null
                val recentlyTriggered = (System.currentTimeMillis() - lastInputTriggerAt) < 1200
                val recentlyEditableSeen = (System.currentTimeMillis() - lastEditableDetectedAt) < 1200
                if ((stillEditable || editableInTree) && !recentlyTriggered) {
                    lastEditableDetectedAt = System.currentTimeMillis()
                }
                if (!stillEditable && !editableInTree && !recentlyTriggered && !recentlyEditableSeen && FloatingBannerService.isVisible()) {
                    val i = Intent(this, FloatingBannerService::class.java).apply {
                        action = FloatingBannerService.ACTION_HIDE
                    }
                    startService(i)
                }
            }
            autoBannerHandler.postDelayed(pendingHideRunnable!!, 900)
        }

        lastPackage = pkg
    }

    override fun onInterrupt() {
        // No-op.
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }

    private fun setText(text: String): Boolean {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (trySetOnNode(focused, text)) {
            return true
        }

        val editable = findFirstEditable(rootInActiveWindow)
        if (editable != null) {
            editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            editable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (trySetOnNode(editable, text)) {
                return true
            }
        }

        return false
    }

    private fun trySetOnNode(node: AccessibilityNodeInfo?, text: String): Boolean {
        if (node == null || !node.isEditable) return false

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return true
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("quick_text", text))
        return node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun findFirstEditable(root: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isEditable && node.isEnabled) {
                return node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }


    private fun getFocusedOrFirstEditableText(): String {
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val focusedText = focused?.text?.toString()?.trim().orEmpty()
        if (focusedText.isNotEmpty()) return focusedText

        val editable = findFirstEditable(rootInActiveWindow)
        return editable?.text?.toString()?.trim().orEmpty()
    }

    private fun collectVisibleText(maxChars: Int = 4000): String {
        val root = rootInActiveWindow ?: return ""
        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        val lines = ArrayList<String>()
        val seen = HashSet<String>()
        queue.add(root)
        var total = 0

        while (queue.isNotEmpty() && total < maxChars) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()

            if (text.isNotEmpty() && seen.add(text)) {
                lines.add(text)
                total += text.length + 1
            }
            if (desc.isNotEmpty() && seen.add(desc)) {
                lines.add(desc)
                total += desc.length + 1
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return lines.joinToString("\n")
    }

    private fun generateReplyFromOpenRouter(apiKey: String, instruction: String): String? {
        val context = collectVisibleText().trim()
        Log.d(TAG, "Visible text chars=${context.length}")
        if (context.isNotEmpty()) {
            val sample = if (context.length > 300) context.substring(0, 300) + "..." else context
            Log.d(TAG, "Visible text sample=\n$sample")
        }
        if (context.isEmpty()) return null

        val prompt = """
You are writing one short reply.

Priority rules:
1) USER INPUT is highest priority. If USER INPUT exists, optimize the reply around it first.
2) VISIBLE SCREEN TEXT is supporting context only.
3) If USER INPUT conflicts with visible text, follow USER INPUT.
4) Return only the final reply text. No quotes. No emojis unless explicitly requested.

USER INPUT (highest priority):
$instruction

VISIBLE SCREEN TEXT (supporting context):
$context
""".trimIndent()

        val body = JSONObject().apply {
            put("model", "google/gemini-3.1-flash-lite")
            put("temperature", 0.5)
            put("max_tokens", 120)
            put("messages", org.json.JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }))
        }
        Log.d(TAG, "OpenRouter request body=\n${body}")

        val conn = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val statusCode = conn.responseCode
        val stream = if (statusCode in 200..299) conn.inputStream else conn.errorStream
        val raw = BufferedReader(stream.reader()).use { it.readText() }
        Log.d(TAG, "OpenRouter status=$statusCode")
        Log.d(TAG, "OpenRouter response=\n$raw")

        appendApiLog(JSONObject().apply {
            put("time", nowTs())
            put("status", statusCode)
            put("instruction", instruction)
            put("visibleTextChars", context.length)
            put("visibleTextSample", if (context.length > 600) context.substring(0, 600) + "..." else context)
            put("requestBody", body)
            put("responseBody", raw)
        })

        if (statusCode !in 200..299) return null

        val json = JSONObject(raw)
        val choices = json.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null
        val message = choices.getJSONObject(0).optJSONObject("message") ?: return null
        val content = message.optString("content").trim()
        if (content.isNotEmpty()) return content

        val reasoning = message.optString("reasoning").trim()
        if (reasoning.isNotEmpty()) {
            val cleaned = reasoning.lines().firstOrNull { it.trim().isNotEmpty() }?.trim().orEmpty()
            if (cleaned.isNotEmpty()) return cleaned
        }

        val details = message.optJSONArray("reasoning_details")
        if (details != null && details.length() > 0) {
            val first = details.optJSONObject(0)?.optString("text")?.trim().orEmpty()
            if (first.isNotEmpty()) return first
        }

        return null
    }


    private fun appendApiLog(entry: JSONObject) {
        val prefs = getSharedPreferences("quick_text_ai_logs", MODE_PRIVATE)
        val raw = prefs.getString("entries", "[]") ?: "[]"
        val arr = try {
            JSONArray(raw)
        } catch (_: Exception) {
            JSONArray()
        }
        arr.put(entry)
        while (arr.length() > 120) {
            val trimmed = JSONArray()
            for (i in 1 until arr.length()) trimmed.put(arr.get(i))
            for (i in 0 until trimmed.length()) {
                arr.put(i, trimmed.get(i))
            }
        }
        prefs.edit().putString("entries", arr.toString()).apply()
    }

    private fun nowTs(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }

    companion object {
        @Volatile
        private var instance: TextInjectorAccessibilityService? = null

        private const val TAG = "QuickTextAI"

        fun isEnabled(): Boolean = instance != null

        fun injectText(text: String): Boolean {
            return instance?.setText(text) ?: false
        }

        fun getVisibleText(): String {
            return instance?.collectVisibleText().orEmpty()
        }

        fun generateAiReplyAndInject(instruction: String): Boolean {
            val svc = instance ?: return false
            val apiKey = svc.getSharedPreferences("quick_text_settings", MODE_PRIVATE)
                .getString("openrouter_api_key", "")
                .orEmpty()
                .trim()
            if (apiKey.isBlank()) return false
            Log.d(TAG, "Instruction= $instruction")
            val reply = svc.generateReplyFromOpenRouter(apiKey, instruction) ?: return false
            Log.d(TAG, "Generated reply= $reply")
            return svc.setText(reply)
        }

        fun generateCompletionFromActiveInputAndInject(extraUserPrompt: String): Boolean {
            val svc = instance ?: return false
            val apiKey = svc.getSharedPreferences("quick_text_settings", MODE_PRIVATE)
                .getString("openrouter_api_key", "")
                .orEmpty()
                .trim()
            if (apiKey.isBlank()) return false
            val draft = svc.getFocusedOrFirstEditableText()
            if (draft.isBlank()) return false

            val instruction = buildString {
                append("Complete this draft reply naturally. Keep original meaning and tone, just improve and finish it. No extra style. No emojis unless already present in draft.\n\n")
                append("Draft text from input field:\n")
                append(draft)
                if (extraUserPrompt.isNotBlank()) {
                    append("\n\nExtra user requirement:\n")
                    append(extraUserPrompt)
                }
            }

            Log.d(TAG, "Draft completion instruction= $instruction")
            val reply = svc.generateReplyFromOpenRouter(apiKey, instruction) ?: return false
            Log.d(TAG, "Draft completion generated= $reply")
            return svc.setText(reply)
        }
    }
}
