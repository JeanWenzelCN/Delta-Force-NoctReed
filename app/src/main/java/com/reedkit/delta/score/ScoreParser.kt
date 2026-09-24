package com.reedkit.delta.score

/** 音区状态（互斥） */
enum class Register { NATURAL, SHARP, FLAT }

/** 解析后的单个演奏事件 */
sealed class ScoreEvent {
    /** 演奏一个音符。degree: 1..8 (8 即高音 i)；beats: 拍数 */
    data class Note(val degree: Int, val register: Register, val halfStep: Boolean, val beats: Int) : ScoreEvent()
    /** 休止 */
    data class Rest(val beats: Int) : ScoreEvent()
}

/**
 * 标准纯文本简谱解析器（数字简谱通行记法，可直接从现成简谱文本粘贴）：
 *
 *   音级：1 2 3 4 5 6 7；高音加点：1. 2.（上加点，亦可用 8 或 i 表示高音 1）
 *   低音加点：.1 .2（下加点）；音符后写 "-" 为延长线，每多一个 "-" 加一拍
 *   升半音：#4；降半音：b7（降号写在音符前）
 *   休止符：0（一拍），0- 为两拍，依此类推
 *   小节线 "|" 与空白、换行仅作分隔，自动忽略
 *
 * 说明：游戏内按键模型为「音区互斥 + 半音开关」，解析时把简谱记号
 *   映射为：高音/低音点 → 音区切换（升调/降调），# 与 b → 半音开关。
 */
object ScoreParser {

    fun parse(text: String): List<ScoreEvent> {
        val events = mutableListOf<ScoreEvent>()
        val tokens = text
            .replace('|', ' ')
            .replace('，', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        for (t in tokens) {
            when {
                // 小节线等残留：忽略
                t == "-" -> extendLast(events, 1)
                t.all { it == '0' || it == '-' } && t.contains('0') -> {
                    // 休止符：0、0-、0-- …
                    val beats = 1 + t.count { it == '-' }
                    events.add(ScoreEvent.Rest(beats))
                }
                else -> parseNoteToken(t, events)
            }
        }
        return events
    }

    private fun parseNoteToken(token: String, events: MutableList<ScoreEvent>) {
        var s = token
        var halfStep = false
        var register = Register.NATURAL

        // 前置降号 / 升号
        while (s.isNotEmpty()) {
            when (s.first()) {
                '#', '♯' -> { halfStep = true; s = s.substring(1) }
                'b', 'B', '♭' -> { halfStep = true; s = s.substring(1) }
                else -> break
            }
        }
        // 前置低音点 .1
        while (s.startsWith(".")) { register = Register.FLAT; s = s.substring(1) }
        // 后置高音点 1.（逐个取）
        var highDots = 0
        while (s.endsWith(".")) { highDots++; s = s.dropLast(1) }
        if (highDots > 0) register = Register.SHARP

        // 延长线：核心数字后的 "-"
        var beats = 1
        while (s.endsWith("-")) { beats++; s = s.dropLast(1) }

        if (s.isEmpty()) {
            // 纯 "-" token（前面已处理过，这里兜底）
            extendLast(events, beats - 1)
            return
        }
        val degree = when (s) {
            "i", "I", "8" -> 8
            else -> s.toIntOrNull()
        } ?: return
        if (degree !in 1..8) return
        // degree 8（高音1）本身即升调音区
        val finalRegister = if (degree == 8) Register.SHARP else register
        events.add(ScoreEvent.Note(degree, finalRegister, halfStep, beats))
    }

    /** 把上一音符延长 beats 拍；若没有上一音符则当作休止 */
    private fun extendLast(events: MutableList<ScoreEvent>, beats: Int) {
        if (beats <= 0) return
        when (val last = events.lastOrNull()) {
            is ScoreEvent.Note -> events[events.lastIndex] = last.copy(beats = last.beats + beats)
            is ScoreEvent.Rest -> events[events.lastIndex] = last.copy(beats = last.beats + beats)
            null -> events.add(ScoreEvent.Rest(beats))
        }
    }

    /** 示例：标准简谱《小星星》片段 */
    val SAMPLE = "1 1 5 5 | 6 6 5- | 4 4 3 3 | 2 2 1-"
}
