package com.reedkit.delta.service

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
import com.reedkit.delta.data.CalibrationStore
import com.reedkit.delta.data.Keys
import com.reedkit.delta.score.Register
import com.reedkit.delta.score.ScoreEvent
import com.reedkit.delta.score.ScoreParser
import kotlin.coroutines.resume

class HarmonicaAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playJob: kotlinx.coroutines.Job? = null

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

    /** 单击屏幕坐标 */
    private suspend fun tap(x: Float, y: Float): Boolean = suspendCancellableCoroutine { cont ->
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { cont.resume(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { cont.resume(false) }
        }, null)
    }

    private suspend fun doTapKey(key: String): Boolean {
        val pos = store.get(key) ?: return false
        return tap(pos.first, pos.second)
    }

    /** 开始演奏 */
    fun startPlaying(scoreText: String, bpm: Int) {
        stopPlaying()
        val events = ScoreParser.parse(scoreText)
        if (events.isEmpty()) return
        val beatMs = (60000L / bpm).coerceAtLeast(100L)
        val switchGap = 120L // 状态切换与音符之间的间隔

        playJob = scope.launch {
            var register = Register.NATURAL
            var halfStep = false

            for (event in events) {
                when (event) {
                    is ScoreEvent.Rest -> delay(beatMs * event.beats)
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
                        // 音符键
                        val noteKey = if (event.degree == 8) "i" else event.degree.toString()
                        doTapKey(noteKey)
                        delay(beatMs * event.beats)
                    }
                }
            }
            OverlayController.notifyPlayState(false)
        }
    }

    fun stopPlaying() {
        playJob?.cancel()
        playJob = null
    }

    fun isPlaying() = playJob?.isActive == true

    companion object {
        var instance: HarmonicaAccessibilityService? = null
            private set
        lateinit var store: CalibrationStore
            private set

        fun isEnabled() = instance != null
    }
}