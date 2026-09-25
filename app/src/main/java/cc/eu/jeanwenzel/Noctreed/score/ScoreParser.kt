package cc.eu.jeanwenzel.Noctreed.score

/** 音区状态（互斥） */
enum class Register { NATURAL, SHARP, FLAT }

sealed class ScoreEvent {
    /** 音符：degree 1..8（8 = 高音 i），beats 拍数，phraseIndex 乐句序号（从 0 开始） */
    data class Note(val degree: Int, val register: Register, val halfStep: Boolean, val beats: Int, val phraseIndex: Int = 0) : ScoreEvent()
    data class Rest(val beats: Int, val phraseIndex: Int = 0) : ScoreEvent()
}

/**
 * 标准数字简谱（jianpu）解析器。
 * 记法约定：
 *  - 基本音级：1 2 3 4 5 6 7，8/i 也表示高音 do
 *  - 高音点：数字后加点，如 1.（点越多越高，但游戏只有一档高音区）
 *  - 低音点：数字前加点，如 .1
 *  - 半音：#4 / ♯4 升半音，b7 / ♭7 / B7 降半音
 *  - 时值：音符后每多一个 `-` 延长一拍，如 5--（共 3 拍）；单独的 `-` 延长上一音符一拍
 *  - 休止：0，后随 `-` 延长，如 0-（共 2 拍）
 *  - 小节线 | 与逗号、换行均视为乐句/拍分隔，自动忽略
 */
object ScoreParser {

    data class Parsed(val events: List<ScoreEvent>, val phraseCount: Int)

    fun parse(text: String): List<ScoreEvent> = parseWithPhrases(text).events

    /** 解析并保留乐句边界：按 | 与换行切分乐句 */
    fun parseWithPhrases(text: String): Parsed {
        val events = mutableListOf<ScoreEvent>()
        var phrase = 0
        var sawTokenInPhrase = false

        // 先统一分隔符：逗号 → 空白；| 与换行是乐句边界
        val normalized = text.replace('，', ' ').replace(',', ' ')
        val tokens = mutableListOf<Pair<String, Boolean>>() // token -> 是否为乐句边界前的最后 token
        val sb = StringBuilder()
        fun flushToken() {
            if (sb.isNotEmpty()) { tokens.add(sb.toString() to false); sb.clear() }
        }
        for (c in normalized) {
            when {
                c == '|' || c == '\n' || c == '\r' -> { flushToken(); tokens.add("" to true) }
                c.isWhitespace() -> flushToken()
                else -> sb.append(c)
            }
        }
        flushToken()

        for ((token, boundary) in tokens) {
            if (boundary) {
                if (sawTokenInPhrase) { phrase++; sawTokenInPhrase = false }
                continue
            }
            val before = events.size
            parseToken(token, events, phrase)
            if (events.size > before) sawTokenInPhrase = true
        }
        return Parsed(events, if (events.isEmpty()) 0 else phrase + 1)
    }

    private fun parseToken(t: String, events: MutableList<ScoreEvent>, phrase: Int) {
        if (t == "-") { extendLast(events, 1, phrase); return }
        if (t.all { it == '0' || it == '-' } && t.contains('0')) {
            val beats = 1 + t.count { it == '-' }
            events.add(ScoreEvent.Rest(beats, phrase))
            return
        }
        parseNoteToken(t, events, phrase)
    }

    private fun parseNoteToken(token: String, events: MutableList<ScoreEvent>, phrase: Int) {
        var s = token
        var halfStep = false
        var register = Register.NATURAL

        // 前置升降记号
        while (s.startsWith("#") || s.startsWith("♯")) { halfStep = true; s = s.drop(1) }
        while (s.startsWith("b") || s.startsWith("B") || s.startsWith("♭")) { halfStep = true; s = s.drop(1) }
        // 前置低音点
        while (s.startsWith(".")) { register = Register.FLAT; s = s.drop(1) }
        // 后置高音点
        var highDots = 0
        while (s.endsWith(".")) { highDots++; s = s.dropLast(1) }
        if (highDots > 0) register = Register.SHARP
        // 后置延音线
        var extra = 0
        while (s.endsWith("-")) { extra++; s = s.dropLast(1) }

        if (s.isEmpty()) return
        val degree = when (s) {
            "i", "I", "8" -> 8
            else -> s.toIntOrNull()
        } ?: return // 无法识别，静默跳过
        if (degree !in 1..8) return

        // 8/i 必在高音区
        val finalRegister = if (degree == 8) Register.SHARP else register
        events.add(ScoreEvent.Note(degree, finalRegister, halfStep, 1 + extra, phrase))
    }

    private fun extendLast(events: MutableList<ScoreEvent>, extra: Int, phrase: Int) {
        val last = events.lastOrNull()
        if (last == null) { events.add(ScoreEvent.Rest(extra, phrase)); return }
        events[events.lastIndex] = when (last) {
            is ScoreEvent.Note -> last.copy(beats = last.beats + extra)
            is ScoreEvent.Rest -> last.copy(beats = last.beats + extra)
        }
    }

    const val SAMPLE = "1 1 5 5 | 6 6 5- | 4 4 3 3 | 2 2 1-"
}