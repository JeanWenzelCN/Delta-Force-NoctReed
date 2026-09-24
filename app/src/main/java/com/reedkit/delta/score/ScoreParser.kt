package com.reedkit.delta.score

/** 音区状态（互斥） */
enum class Register { NATURAL, SHARP, FLAT }

/** 解析后的单个演奏事件 */
sealed class ScoreEvent {
    /** 演奏一个音符。degree: 1..8 (8 即 i)；beats: 拍数 */
    data class Note(val degree: Int, val register: Register, val halfStep: Boolean, val beats: Int) : ScoreEvent()
    /** 休止/延长 */
    data class Rest(val beats: Int) : ScoreEvent()
}

object ScoreParser {

    /**
     * 乐谱格式：
     *   音符: 1 2 3 4 5 6 7 i
     *   升降: #4 （半音）  高音区: ^1  低音区: ,1
     *   状态段: 自然音:1 2 3; 升调:4 5; 降调:6 7
     *   - 延长一拍，0 休止一拍
     */
    fun parse(text: String): List<ScoreEvent> {
        val events = mutableListOf<ScoreEvent>()
        var currentRegister = Register.NATURAL
        val segments = text.split(';', '；')
        for (segment in segments) {
            val seg = segment.trim()
            if (seg.isEmpty()) continue
            // 段级状态标记 "升调:..." / "自然音:..." / "降调:..."
            val body = when {
                seg.startsWith("自然音:") || seg.startsWith("自然音：") -> { currentRegister = Register.NATURAL; seg.substring(4) }
                seg.startsWith("升调:") || seg.startsWith("升调：") -> { currentRegister = Register.SHARP; seg.substring(3) }
                seg.startsWith("降调:") || seg.startsWith("降调：") -> { currentRegister = Register.FLAT; seg.substring(3) }
                else -> seg
            }
            val tokens = body.split(Regex("\\s+")).filter { it.isNotBlank() }
            var i = 0
            while (i < tokens.size) {
                val t = tokens[i]
                var halfStep = false
                var register = currentRegister
                var core = t
                while (core.isNotEmpty()) {
                    when {
                        core.startsWith("#") -> { halfStep = true; core = core.substring(1) }
                        core.startsWith("^") -> { register = Register.SHARP; core = core.substring(1) }
                        core.startsWith(",") -> { register = Register.FLAT; core = core.substring(1) }
                        else -> break
                    }
                }
                when {
                    core == "-" -> {
                        // 延长上一音符一拍
                        val last = events.lastOrNull()
                        if (last is ScoreEvent.Note) {
                            events[events.lastIndex] = last.copy(beats = last.beats + 1)
                        } else {
                            events.add(ScoreEvent.Rest(1))
                        }
                    }
                    core == "0" -> events.add(ScoreEvent.Rest(1))
                    else -> {
                        val degree = when (core) {
                            "i", "8" -> 8
                            else -> core.toIntOrNull()
                        }
                        if (degree != null && degree in 1..8) {
                            events.add(ScoreEvent.Note(degree, register, halfStep, 1))
                        }
                        // 无法识别的 token 静默跳过
                    }
                }
                i++
            }
        }
        return events
    }

    val SAMPLE = "自然音:1 2 3 4 5; 升调:5 4 3 2 1; 自然音:1 - #4 5"
}