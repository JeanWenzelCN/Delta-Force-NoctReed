package com.reedkit.delta.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 播放状态：空闲 / 演奏中 / 已暂停 */
enum class PlayState { IDLE, PLAYING, PAUSED }

/** 悬浮窗与主界面共享的播放状态总线 */
object OverlayController {
    private val _playState = MutableStateFlow(PlayState.IDLE)
    val playState = _playState.asStateFlow()

    private val _score = MutableStateFlow("")
    val score = _score.asStateFlow()

    private val _bpm = MutableStateFlow(90)
    val bpm = _bpm.asStateFlow()

    /** 校准模式状态（用于悬浮窗 UI 切换） */
    private val _calibrating = MutableStateFlow(false)
    val calibrating = _calibrating.asStateFlow()

    private val _calibPrompt = MutableStateFlow("")
    val calibPrompt = _calibPrompt.asStateFlow()

    fun updateScore(text: String) { _score.value = text }
    fun updateBpm(v: Int) { _bpm.value = v.coerceIn(30, 300) }

    fun start(context: Context) {
        val service = HarmonicaAccessibilityService.instance ?: return
        service.startPlaying(_score.value, _bpm.value)
        setState(PlayState.PLAYING)
    }

    fun pause() {
        HarmonicaAccessibilityService.instance?.pausePlaying() ?: return
        setState(PlayState.PAUSED)
    }

    fun resume() {
        HarmonicaAccessibilityService.instance?.resumePlaying() ?: return
        setState(PlayState.PLAYING)
    }

    fun stop() {
        HarmonicaAccessibilityService.instance?.stopPlaying()
        setState(PlayState.IDLE)
    }

    fun notifyPlayState(playing: Boolean) {
        if (!playing) setState(PlayState.IDLE)
    }

    fun setCalibrating(v: Boolean, prompt: String = "") {
        _calibrating.value = v
        _calibPrompt.value = prompt
    }

    private fun setState(s: PlayState) {
        _playState.value = s
    }
}