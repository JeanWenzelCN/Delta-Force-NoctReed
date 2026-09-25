package cc.eu.jeanwenzel.Noctreed.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import cc.eu.jeanwenzel.Noctreed.data.CalibrationStore
import cc.eu.jeanwenzel.Noctreed.data.Keys
import cc.eu.jeanwenzel.Noctreed.score.Register
import cc.eu.jeanwenzel.Noctreed.score.ScoreEvent
import cc.eu.jeanwenzel.Noctreed.score.ScoreParser
import kotlin.coroutines.resume

class HarmonicaAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playJob: kotlinx.coroutines.Job? = null

    @Volatile
    private var paused = false
    private val pauseLock = Object()

    override fun onServiceConnected() {
        instance = this
        store = CalibrationStore(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        stopPlaying()
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        super.onDestroy()
    }

    /** 按住屏幕坐标 durationMs 毫秒（短触即单击） */
    private suspend fun tap(x: Float, y: Float, durationMs: Long = 60): Boolean = suspendCancellableCoroutine { cont ->
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { cont.resume(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { cont.resume(false) }
        }, null)
    }
    private suspend fun doTapKey(key: String, durationMs: Long = 60): Boolean {
        val pos = store.get(key) ?: return false
        return tap(pos.first, pos.second, durationMs)
    }

    /** 开始演奏 */
    fun startPlaying(scoreText: String, @Suppress("UNUSED_PARAMETER") bpm: Int) {
        stopPlaying()
        val parsed = ScoreParser.parseWithPhrases(scoreText)
        val events = parsed.events
        if (events.isEmpty()) return
        val phraseCount = parsed.phraseCount
        val phrases = parsed.phrases
        OverlayController.notifyPhrase(if (phraseCount > 0) 1 else 0, phraseCount, phrases.getOrNull(0) ?: "")
        // 每次取当前 BPM 计算节拍时长，悬浮窗调速可实时生效
        fun beatMsNow(): Long = (60000L / OverlayController.bpm.value).coerceAtLeast(100L)
        val switchGap = 120L // 状态切换与音符之间的间隔
        paused = false

        playJob = scope.launch {
            var register = Register.NATURAL
            var halfStep = false
            var lastPhrase = -1

            for (event in events) {
                awaitIfPaused()
                // 乐句变化时上报悬浮窗
                val pi = when (event) {
                    is ScoreEvent.Note -> event.phraseIndex
                    is ScoreEvent.Rest -> event.phraseIndex
                }
                if (pi != lastPhrase) {
                    lastPhrase = pi
                    OverlayController.notifyPhrase(pi + 1, phraseCount, phrases.getOrNull(pi) ?: "")
                }
                when (event) {
                    is ScoreEvent.Rest -> delayInterruptibly(beatMsNow() * event.beats)
                    is ScoreEvent.Note -> {
                        // 维度一：音区（互斥），已在目标状态则跳过
                        if (event.register != register) {
                            val key = when (event.register) {
                                Register.NATURAL -> Keys.NATURAL
                                Register.SHARP -> Keys.SHARP
                                Register.FLAT -> Keys.FLAT
                            }
                            doTapKey(key)
                            register = event.register
                            delay(switchGap)
                        }
                        // 维度二：半音（独立开关）
                        if (event.halfStep != halfStep) {
                            doTapKey(Keys.HALF_STEP)
                            halfStep = event.halfStep
                            delay(switchGap)
                        }
                        // 音符键：长按，按住时长约为音长的 90%（长音保持按下，短音近似点触）
                        val noteKey = if (event.degree == 8) "i" else event.degree.toString()
                        val noteMs = beatMsNow() * event.beats
                        val holdMs = (noteMs * 9 / 10).coerceAtLeast(60L)
                        doTapKey(noteKey, holdMs)
                        delayInterruptibly(noteMs - holdMs)
                    }
                }
            }
            OverlayController.notifyPlayState(false)
        }
    }

    /** 暂停时挂起等待，恢复后继续 */
    private suspend fun awaitIfPaused() {
        while (paused) {
            kotlinx.coroutines.delay(100)
        }
    }

    /** 可被暂停感知的 delay：分段等待，暂停时暂停计时 */
    private suspend fun delayInterruptibly(ms: Long) {
        var remaining = ms
        val step = 50L
        while (remaining > 0) {
            awaitIfPaused()
            val d = minOf(step, remaining)
            kotlinx.coroutines.delay(d)
            remaining -= d
        }
    }

    fun pausePlaying() {
        if (playJob?.isActive == true) paused = true
    }

    fun resumePlaying() {
        paused = false
    }

    fun stopPlaying() {
        playJob?.cancel()
        playJob = null
        paused = false
    }

    fun isPlaying() = playJob?.isActive == true && !paused

    companion object {
        var instance: HarmonicaAccessibilityService? = null
            private set
        lateinit var store: CalibrationStore
            private set

        fun isEnabled() = instance != null
    }
}