package com.drx.casefiles.data

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * The language of the screens. The string files keep both languages in one text ("English · العربية");
 * the app shows only the one chosen here (Arabic by default) and mirrors the layout to match.
 * PDF reports choose their own language per file (see Tx in Pdf.kt) and are not affected.
 */
object Lang {
    var ar by mutableStateOf(true)
        private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("dc", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        ar = prefs(ctx).getString("ui_lang", "AR") != "EN"
    }

    fun set(ctx: Context, arabic: Boolean) {
        ar = arabic
        prefs(ctx).edit().putString("ui_lang", if (arabic) "AR" else "EN").apply()
    }

    private fun hasArabic(x: String) = x.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }

    /** One language out of a bilingual "English · العربية" text; texts without that separator pass through. */
    fun pickFor(raw: String, arabic: Boolean): String {
        val parts = raw.split(" · ")
        if (parts.size < 2) return raw
        val chosen = parts.filter { hasArabic(it) == arabic }
        return if (chosen.isEmpty()) raw else chosen.joinToString(" · ")
    }

    fun pick(raw: String): String = pickFor(raw, ar)

    /** For code outside Compose (toasts, labels). The language is picked before the arguments are filled in. */
    fun get(ctx: Context, @StringRes id: Int, vararg args: Any): String {
        val t = pick(ctx.getString(id))
        return if (args.isEmpty()) t else String.format(Locale.getDefault(), t, *args)
    }
}
