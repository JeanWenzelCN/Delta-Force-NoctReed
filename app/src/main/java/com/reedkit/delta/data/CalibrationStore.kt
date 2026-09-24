package com.reedkit.delta.data

import android.content.Context
import android.content.SharedPreferences

/** 可校准按键的标识 */
object Keys {
    val NOTES = listOf("1", "2", "3", "4", "5", "6", "7", "i")
    const val HALF_STEP = "半音"
    const val SHARP = "升调"
    const val NATURAL = "自然音"
    const val FLAT = "降调"
    val TOGGLES = listOf(HALF_STEP, SHARP, NATURAL, FLAT)
    val ALL = NOTES + TOGGLES
}

/** 校准坐标持久化（SharedPreferences 即可满足需求） */
class CalibrationStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("calibration", Context.MODE_PRIVATE)

    fun save(key: String, x: Float, y: Float) {
        prefs.edit().putFloat("${key}_x", x).putFloat("${key}_y", y).apply()
    }

    fun get(key: String): Pair<Float, Float>? {
        if (!prefs.contains("${key}_x")) return null
        return prefs.getFloat("${key}_x", 0f) to prefs.getFloat("${key}_y", 0f)
    }

    fun isCalibrated(key: String) = prefs.contains("${key}_x")

    fun allCalibrated() = Keys.ALL.all { isCalibrated(it) }

    fun clear() = prefs.edit().clear().apply()
}