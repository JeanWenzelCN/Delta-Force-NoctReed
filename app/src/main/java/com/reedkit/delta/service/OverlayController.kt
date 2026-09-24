package com.reedkit.delta.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 悬浮窗与主界面共享的播放状态总线 */
object OverlayController {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _score = MutableStateFlow("")
    val score = _score.asStateFlow()

    private val _bpm = MutableStateFlow(90)
    val bpm = _bpm.asStateFlow()

    fun updateScore(text: String) { _score.value = text }
    fun updateBpm(v: Int) { _bpm.value = v.coerceIn(30, 300) }

    fun toggle(context: Context) {
        if (_isPlaying.value) stop() else start(context)
    }

    fun start(context: Context) {
        val service = HarmonicaAccessibilityService.instance ?: return
        service.startPlaying(_score.value, _bpm.value)
        _isPlaying.value = true
    }

    fun stop() {
        HarmonicaAccessibilityService.instance?.stopPlaying()
        _isPlaying.value = false
    }

    fun notifyPlayState(playing: Boolean) {
        _isPlaying.value = playing
    }
}