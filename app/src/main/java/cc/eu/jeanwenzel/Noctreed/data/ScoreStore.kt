package cc.eu.jeanwenzel.Noctreed.data

import android.content.Context

/** 乐谱本地存储：基于 SharedPreferences，键为曲名，值为乐谱文本与 BPM */
class ScoreStore(context: Context) {

    private val prefs = context.getSharedPreferences("scores", Context.MODE_PRIVATE)

    data class SavedScore(val name: String, val text: String, val bpm: Int)

    fun list(): List<SavedScore> {
        val names = prefs.getStringSet(KEY_NAMES, emptySet()) ?: emptySet()
        return names.sorted().mapNotNull { name ->
            val text = prefs.getString("score_$name", null) ?: return@mapNotNull null
            SavedScore(name, text, prefs.getInt("bpm_$name", 90))
        }
    }

    fun save(name: String, text: String, bpm: Int) {
        val names = (prefs.getStringSet(KEY_NAMES, emptySet()) ?: emptySet()).toMutableSet()
        names.add(name)
        prefs.edit()
            .putStringSet(KEY_NAMES, names)
            .putString("score_$name", text)
            .putInt("bpm_$name", bpm)
            .apply()
    }

    fun delete(name: String) {
        val names = (prefs.getStringSet(KEY_NAMES, emptySet()) ?: emptySet()).toMutableSet()
        names.remove(name)
        prefs.edit()
            .putStringSet(KEY_NAMES, names)
            .remove("score_$name")
            .remove("bpm_$name")
            .apply()
    }

    /** 重命名：迁移索引与两条数据键。返回 false 表示旧名不存在或新名已被占用 */
    fun rename(old: String, new: String): Boolean {
        if (old == new) return true
        val names = (prefs.getStringSet(KEY_NAMES, emptySet()) ?: emptySet()).toMutableSet()
        if (old !in names || new in names) return false
        val text = prefs.getString("score_$old", null) ?: return false
        val bpm = prefs.getInt("bpm_$old", 90)
        names.remove(old)
        names.add(new)
        prefs.edit()
            .putStringSet(KEY_NAMES, names)
            .remove("score_$old")
            .remove("bpm_$old")
            .putString("score_$new", text)
            .putInt("bpm_$new", bpm)
            .apply()
        return true
    }

    companion object {
        private const val KEY_NAMES = "names"
    }
}
