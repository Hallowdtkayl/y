package com.drx.casefiles.data

import android.content.Context
import androidx.annotation.StringRes
import com.drx.casefiles.R
import java.util.Locale

/**
 * Image quality of the exported PDF. Pictures are resized to the resolution the page can actually show
 * (never enlarged) and then JPEG-compressed. X-rays, scans and documents keep a higher resolution than
 * clinical photos, so fracture lines and text stay sharp.
 */
enum class PdfQuality(val dpiImaging: Int, val dpiPhoto: Int, val jpeg: Int, @StringRes val label: Int) {
    SMALL(170, 130, 76, R.string.q_small),
    BALANCED(220, 170, 88, R.string.q_balanced),
    HIGH(300, 220, 95, R.string.q_high)
}

/** What the person chose in the export dialog. [lang] is "AR" or "EN": the whole report uses that one language. */
data class PdfOptions(
    val hideIdentity: Boolean,
    val withImages: Boolean,
    val lang: String,
    val sign: Boolean
)

/** Report settings kept on the device: doctor signature, image quality and the language last used per patient. */
object ExportPrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("dc", Context.MODE_PRIVATE)

    fun doctorName(ctx: Context): String = p(ctx).getString("doc_name", "").orEmpty()
    fun doctorTitle(ctx: Context): String = p(ctx).getString("doc_title", "").orEmpty()
    fun setDoctor(ctx: Context, name: String, title: String) =
        p(ctx).edit().putString("doc_name", name.trim()).putString("doc_title", title.trim()).apply()

    fun quality(ctx: Context): PdfQuality =
        enumOrNull<PdfQuality>(p(ctx).getString("img_q", "") ?: "") ?: PdfQuality.BALANCED
    fun setQuality(ctx: Context, q: PdfQuality) = p(ctx).edit().putString("img_q", q.name).apply()

    /** Language for this patient's next export: what was used for them last time, else the last one used, else the device language. */
    fun lang(ctx: Context, patientId: Long): String =
        p(ctx).getString("lang_$patientId", null)
            ?: p(ctx).getString("lang_last", null)
            ?: if (Locale.getDefault().language == "ar") "AR" else "EN"

    fun setLang(ctx: Context, patientId: Long, lang: String) =
        p(ctx).edit().putString("lang_$patientId", lang).putString("lang_last", lang).apply()
}
