package com.screenguide.app

import android.content.Context

data class Config(
    val apiKey: String,
    val model: String,
    val bubbleDp: Int,
    val colorIdx: Int,
    val alpha: Int,
    val dark: Boolean,
    val textSp: Int,
    val langIdx: Int,
    val autoSend: Boolean
) {
    val bubbleColor: Int
        get() = Prefs.COLORS[colorIdx.coerceIn(0, Prefs.COLORS.lastIndex)].second
    val voiceLang: String
        get() = Prefs.LANGS[langIdx.coerceIn(0, Prefs.LANGS.lastIndex)].second
}

object Prefs {

    val COLORS = listOf(
        "Purple" to 0xFF6200EE.toInt(),
        "Blue" to 0xFF1976D2.toInt(),
        "Green" to 0xFF2E7D32.toInt(),
        "Red" to 0xFFD32F2F.toInt(),
        "Orange" to 0xFFF57C00.toInt(),
        "Pink" to 0xFFE91E63.toInt(),
        "Black" to 0xFF212121.toInt()
    )

    val LANGS = listOf(
        "Hindi" to "hi-IN",
        "English (India)" to "en-IN",
        "English (US)" to "en-US"
    )

    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun load(c: Context): Config {
        val s = sp(c)
        return Config(
            apiKey = s.getString("key", "") ?: "",
            model = s.getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash",
            bubbleDp = s.getInt("bubble_dp", 56),
            colorIdx = s.getInt("color_idx", 0),
            alpha = s.getInt("alpha", 90),
            dark = s.getBoolean("dark", true),
            textSp = s.getInt("text_sp", 14),
            langIdx = s.getInt("lang_idx", 0),
            autoSend = s.getBoolean("auto_send", true)
        )
    }

    fun save(c: Context, cfg: Config) {
        sp(c).edit()
            .putString("key", cfg.apiKey)
            .putString("model", cfg.model)
            .putInt("bubble_dp", cfg.bubbleDp)
            .putInt("color_idx", cfg.colorIdx)
            .putInt("alpha", cfg.alpha)
            .putBoolean("dark", cfg.dark)
            .putInt("text_sp", cfg.textSp)
            .putInt("lang_idx", cfg.langIdx)
            .putBoolean("auto_send", cfg.autoSend)
            .apply()
    }
}
