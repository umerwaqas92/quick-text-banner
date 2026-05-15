package com.example.tiktok_agent_android_flutter

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.ceil

class FloatingBannerService : Service() {
    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private var statusView: TextView? = null
    private val chipViews = mutableListOf<TextView>()
    @Volatile
    private var isGenerating: Boolean = false
    private var userPrompt: String = ""
    private var platform: String = "TikTok"
    private var customActions: List<Pair<String, String>> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val texts = intent.getStringArrayListExtra(EXTRA_TEXTS) ?: arrayListOf()
                val rows = intent.getIntExtra(EXTRA_ROWS, 2).coerceIn(1, 4)
                val compactMode = intent.getBooleanExtra(EXTRA_COMPACT_MODE, false)
                userPrompt = intent.getStringExtra(EXTRA_USER_PROMPT).orEmpty().trim()
                platform = intent.getStringExtra(EXTRA_PLATFORM) ?: "TikTok"
                val rawCustom = intent.getStringArrayListExtra(EXTRA_CUSTOM_ACTIONS) ?: arrayListOf()
                customActions = rawCustom.mapNotNull { line ->
                    val parts = line.split("\t", limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) parts[0] to parts[1] else null
                }
                showOverlay(texts, rows, compactMode)
            }

            ACTION_HIDE -> {
                hideOverlay()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }

    private fun showOverlay(texts: List<String>, rows: Int, compactMode: Boolean) {
        lastTexts = texts
        lastRows = rows
        lastCompactMode = compactMode
        lastUserPrompt = userPrompt
        lastPlatform = platform
        lastCustomActions = customActions.map { "${it.first}\t${it.second}" }

        hideOverlay()
        if (texts.isEmpty()) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val displayWidth = resources.displayMetrics.widthPixels
        val compactWidth = (displayWidth * 0.30f).toInt()

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#CC111111"))
            setPadding(10, 10, 10, 10)
            isVerticalScrollBarEnabled = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val aiRowTop = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val aiRowBottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val aiRowsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val aiScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 8
            layoutParams = lp
        }

        statusView = createActionChip("Ready") { }
        statusView?.setBackgroundColor(Color.parseColor("#455A64"))

        val completeChip = createActionChip("🧩 Complete Reply") {
            runDraftCompletionAction()
        }
        completeChip.setBackgroundColor(Color.parseColor("#6A1B9A"))
        aiRowTop.addView(completeChip, 0)

        customActions.forEachIndexed { idx, action ->
            val customChip = createActionChip("➕ ${action.first}") {
                runCustomCompletionAction(action.second)
            }
            customChip.setBackgroundColor(Color.parseColor("#455A64"))
            if (idx % 2 == 0) aiRowTop.addView(customChip) else aiRowBottom.addView(customChip)
        }

        val styles = getStylesForPlatform(platform)

        styles.forEachIndexed { index, (label, instruction) ->
            val chip = createActionChip(label) {
                runAiAction(instruction)
            }
            if (index % 2 == 0) aiRowTop.addView(chip) else aiRowBottom.addView(chip)
        }
        statusView?.let { aiRowTop.addView(it) }
        aiRowsContainer.addView(aiRowTop)
        aiRowsContainer.addView(aiRowBottom)
        aiScroll.addView(aiRowsContainer)
        container.addView(aiScroll)

        val horizontalScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        val rowViews = (0 until rows).map {
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = if (it == rows - 1) 0 else 8
                layoutParams = lp
            }
        }

        val contentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            rowViews.forEach { addView(it) }
        }

        val columns = ceil(texts.size / rows.toDouble()).toInt().coerceAtLeast(1)
        for (col in 0 until columns) {
            for (row in 0 until rows) {
                val idx = col * rows + row
                if (idx >= texts.size) continue
                val text = texts[idx]
                val chip = TextView(this).apply {
                    this.text = text
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setPadding(14, 8, 14, 8)
                    setBackgroundColor(Color.parseColor("#2E7D32"))
                    val lp = LinearLayout.LayoutParams(
                        if (compactMode) compactWidth else LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    lp.marginEnd = 6
                    layoutParams = lp
                    if (compactMode) {
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    setOnClickListener {
                        TextInjectorAccessibilityService.injectText(text)
                    }
                    setOnLongClickListener {
                        hideOverlay()
                        stopSelf()
                        true
                    }
                }
                rowViews[row].addView(chip)
            }
        }

        horizontalScroll.addView(contentColumn)
        container.addView(horizontalScroll)
        rootScroll.addView(container)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = 80
        }

        windowManager?.addView(rootScroll, params)
        rootView = rootScroll
        visible = true
    }

    private fun getStylesForPlatform(name: String): List<Pair<String, String>> {
        return when (name) {
            "LinkedIn" -> listOf(
                "💼 Professional" to "Write a concise professional reply for LinkedIn. No emojis.",
                "🤝 Networking" to "Write a short networking-style reply showing interest in connecting. No emojis.",
                "🧠 Insight" to "Add one thoughtful professional insight in one short reply. No emojis.",
                "❓ Smart Q" to "Write one intelligent follow-up question for LinkedIn. No emojis.",
                "👏 Appreciative" to "Write a short appreciative professional reply. No emojis.",
                "📈 Value-add" to "Write one short value-adding reply with practical takeaway. No emojis.",
                "🧵 Carousel Hook" to "Write a short LinkedIn carousel-style hook reply. No emojis.",
                "📊 Data Point" to "Write one concise data-driven LinkedIn reply. No emojis.",
                "🎯 Contrarian" to "Write a polite contrarian professional reply with one reason. No emojis.",
                "🚀 Builder" to "Write a founder/builder tone LinkedIn reply. No emojis.",
                "📝 Case Study" to "Write a short case-study style LinkedIn reply. No emojis.",
                "🔁 Repost-worthy" to "Write a short repost-worthy line for LinkedIn comments. No emojis."
            )
            "X" -> listOf(
                "⚡ Snappy" to "Write a very short punchy X reply. No emojis.",
                "🔥 Hot Take" to "Write a short hot-take style X reply without toxicity. No emojis.",
                "😂 Funny" to "Write a short witty X reply. No emojis.",
                "❓ Debate Q" to "Write one short debate-provoking question for X. No emojis.",
                "🧵 Thread-style" to "Write a concise thread-worthy reply opener. No emojis.",
                "📌 Sharp" to "Write a sharp concise perspective in one line. No emojis.",
                "🫢 Ratio Bait" to "Write a short bold reply likely to trigger discussion. No abuse. No emojis.",
                "🧪 Nerdy" to "Write a concise nerdy/technical X reply. No emojis.",
                "📣 Viral Hook" to "Write a short viral-hook style X reply. No emojis.",
                "🧨 Spicy-lite" to "Write a spicy but safe short X reply. No insults. No emojis.",
                "👀 Subtweet Vibe" to "Write a subtle sub-tweet style reply. No emojis.",
                "✅ Fact Check" to "Write a concise fact-checking style reply. No emojis."
            )
            "Instagram" -> listOf(
                "❤️ Friendly" to "Write a warm short Instagram reply. No emojis.",
                "👏 Hype" to "Write a short hype/supportive IG comment style reply. No emojis.",
                "😂 Casual" to "Write a short casual/funny IG-style reply. No emojis.",
                "🤔 Thoughtful" to "Write a short thoughtful IG reply. No emojis.",
                "❓ Engage" to "Write a short engaging question-style IG reply. No emojis.",
                "✨ Clean" to "Write a clean aesthetic short IG reply. No emojis.",
                "📸 Aesthetic" to "Write an aesthetic minimalist IG-style comment. No emojis.",
                "🫶 Relatable" to "Write a relatable short IG comment. No emojis.",
                "🧠 Micro-tip" to "Write a short useful micro-tip comment for IG. No emojis.",
                "📍 Save-worthy" to "Write a save-worthy concise IG reply. No emojis.",
                "🎬 Reel Energy" to "Write a fast-paced reel-style comment line. No emojis.",
                "📣 CTA Soft" to "Write a soft engagement CTA for IG comments. No emojis."
            )
            else -> listOf(
                "🎯 Hooky" to "Write a short hooky TikTok-style reply. No emojis.",
                "😈 Hot Take" to "Write a short mildly controversial TikTok reply, not toxic. No emojis.",
                "😂 Funny" to "Write a short funny TikTok-style reply. No emojis.",
                "🤝 Support" to "Write a short creator-support style TikTok reply. No emojis.",
                "❓ Question" to "Write a short engagement question for TikTok comments. No emojis.",
                "⚡ POV" to "Write a short 'pov:' style TikTok reply. No emojis.",
                "🧃 Unhinged-lite" to "Write a playful chaotic TikTok-style reply without abuse. No emojis.",
                "🎬 Storytime" to "Write a short storytime-react TikTok comment. No emojis.",
                "📈 Algo Bait" to "Write an engagement-friendly TikTok comment line. No spam. No emojis.",
                "🪝 Watchtime" to "Write a reply that encourages watching till the end. No emojis.",
                "🧠 Niche Expert" to "Write a niche-expert TikTok reply in one short line. No emojis.",
                "📌 Pin-worthy" to "Write a pin-worthy concise TikTok comment. No emojis."
            )
        }
    }

    private fun createActionChip(label: String, onTap: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(14, 8, 14, 8)
            setBackgroundColor(Color.parseColor("#1565C0"))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = 6
            layoutParams = lp
            setOnClickListener { onTap() }
            chipViews.add(this)
        }
    }

    private fun setStatus(text: String, color: String) {
        Handler(Looper.getMainLooper()).post {
            statusView?.text = text
            statusView?.setBackgroundColor(Color.parseColor(color))
        }
    }


    private fun setChipsEnabled(enabled: Boolean) {
        Handler(Looper.getMainLooper()).post {
            chipViews.forEach { chip ->
                chip.isEnabled = enabled
                chip.alpha = if (enabled) 1.0f else 0.45f
            }
        }
    }

    private fun runDraftCompletionAction() {
        if (isGenerating) {
            Toast.makeText(this, "AI already generating", Toast.LENGTH_SHORT).show()
            return
        }

        isGenerating = true
        setChipsEnabled(false)
        setStatus("Generating...", "#EF6C00")

        Thread {
            val ok = TextInjectorAccessibilityService.generateCompletionFromActiveInputAndInject(userPrompt)
            isGenerating = false
            if (ok) {
                setStatus("Done", "#2E7D32")
            } else {
                setStatus("Failed", "#B71C1C")
            }
            setChipsEnabled(true)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    this,
                    if (ok) "Reply completed" else "No draft input found or AI failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }


    private fun runCustomCompletionAction(customPrompt: String) {
        if (isGenerating) {
            Toast.makeText(this, "AI already generating", Toast.LENGTH_SHORT).show()
            return
        }

        isGenerating = true
        setChipsEnabled(false)
        setStatus("Generating...", "#EF6C00")

        Thread {
            val mergedPrompt = if (userPrompt.isNotBlank()) {
                "$customPrompt\n\nExtra user requirement: $userPrompt"
            } else {
                customPrompt
            }
            val ok = TextInjectorAccessibilityService.generateCompletionFromActiveInputAndInject(mergedPrompt)
            isGenerating = false
            if (ok) {
                setStatus("Done", "#2E7D32")
            } else {
                setStatus("Failed", "#B71C1C")
            }
            setChipsEnabled(true)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    this,
                    if (ok) "Custom completion done" else "No draft input found or AI failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    private fun runAiAction(instruction: String) {
        if (isGenerating) {
            Toast.makeText(this, "AI already generating", Toast.LENGTH_SHORT).show()
            return
        }

        isGenerating = true
        setChipsEnabled(false)
        setStatus("Generating...", "#EF6C00")

        Thread {
            val finalInstruction = if (userPrompt.isNotEmpty()) "$instruction\n\nExtra user request: $userPrompt" else instruction
            val ok = TextInjectorAccessibilityService.generateAiReplyAndInject(finalInstruction)
            isGenerating = false
            if (ok) {
                setStatus("Done", "#2E7D32")
            } else {
                setStatus("Failed", "#B71C1C")
            }
            setChipsEnabled(true)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    this,
                    if (ok) "AI reply inserted" else "AI reply failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }.start()
    }

    private fun hideOverlay() {
        rootView?.let {
            windowManager?.removeView(it)
        }
        rootView = null
        statusView = null
        chipViews.clear()
        isGenerating = false
        visible = false
    }

    companion object {
        @Volatile private var visible: Boolean = false
        var lastTexts: List<String> = emptyList()
        var lastRows: Int = 2
        var lastCompactMode: Boolean = false
        var lastUserPrompt: String = ""
        var lastPlatform: String = "TikTok"
        var lastCustomActions: List<String> = emptyList()

        fun isVisible(): Boolean = visible

        const val ACTION_SHOW = "com.example.tiktok_agent_android_flutter.SHOW_OVERLAY"
        const val ACTION_HIDE = "com.example.tiktok_agent_android_flutter.HIDE_OVERLAY"
        const val EXTRA_TEXTS = "texts"
        const val EXTRA_ROWS = "rows"
        const val EXTRA_COMPACT_MODE = "compactMode"
        const val EXTRA_USER_PROMPT = "userPrompt"
        const val EXTRA_PLATFORM = "platform"
        const val EXTRA_CUSTOM_ACTIONS = "customActions"
    }
}
