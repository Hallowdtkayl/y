package com.drx.casefiles.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.drx.casefiles.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Picks one language out of the app's bilingual "English · العربية" strings, so a report never mixes the two. */
private class Tx(val ctx: Context, val ar: Boolean) {
    fun pick(raw: String): String = Lang.pickFor(raw, ar)

    fun s(id: Int, vararg a: Any): String = pick(if (a.isEmpty()) ctx.getString(id) else ctx.getString(id, *a))

    /** True when [v] is the text of string [id] in either language (values are stored in the language of the screen). */
    fun isText(v: String, id: Int): Boolean {
        val raw = ctx.getString(id)
        return v == raw || v == Lang.pickFor(raw, true) || v == Lang.pickFor(raw, false)
    }

    /** A stored dropdown value shown in the report language when it is one of the ready options. */
    fun preset(v: String): String {
        if (v.isBlank()) return v
        val id = ProcPresets.values.flatten().firstOrNull { isText(v, it) }
        return if (id != null) s(id) else pick(v)
    }
}

/** Minimal flowing-text + image PDF writer (A4). [rtl] right-aligns and mirrors the layout for Arabic reports. */
private class PdfWriter(val rtl: Boolean, private val footer: String) {
    val doc = PdfDocument()
    val pw = 595
    val ph = 842
    val mg = 36f
    var page: PdfDocument.Page? = null
    var canvas: Canvas = Canvas()
    var y = 0f
    var n = 0
    val body = TextPaint().apply { textSize = 10.5f; isAntiAlias = true; color = Color.BLACK }
    val title = TextPaint(body).apply { textSize = 18f; isFakeBoldText = true }
    val head = TextPaint(body).apply { textSize = 13f; isFakeBoldText = true; color = Color.rgb(21, 101, 192) }
    val sub = TextPaint(body).apply { textSize = 11f; isFakeBoldText = true }
    val small = TextPaint(body).apply { textSize = 8.5f; color = Color.DKGRAY }
    private val foot = TextPaint(small).apply { textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT }
    private val dir = if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR

    init {
        newPage()
    }

    fun newPage() {
        page?.let { doc.finishPage(it) }
        n++
        val p = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, n).create())
        page = p
        canvas = p.canvas
        val label = if (footer.isBlank()) "Doctor Class · $n" else "$footer · $n"
        canvas.drawText(label, if (rtl) pw - mg else mg, ph - 18f, foot)
        y = mg
    }

    fun ensure(h: Float) {
        if (y + h > ph - mg) newPage()
    }

    fun layout(s: String, p: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(s, 0, s.length, p, width).setTextDirection(dir).build()

    /** Draws lines [from, to) of [l] with the layout's left edge at x and the first of them at top. Returns the height used. */
    fun drawLines(l: StaticLayout, x: Float, top: Float, from: Int, to: Int): Float {
        val t0 = l.getLineTop(from)
        val h = l.getLineBottom(to - 1) - t0
        canvas.save()
        canvas.translate(x, top - t0)
        canvas.clipRect(0f, t0.toFloat(), l.width.toFloat(), (t0 + h).toFloat())
        l.draw(canvas)
        canvas.restore()
        return h.toFloat()
    }

    fun text(s: String, p: TextPaint = body, indent: Float = 0f, gap: Float = 2f) {
        if (s.isBlank()) return
        val width = (pw - 2 * mg - indent).toInt()
        val l = layout(s, p, width)
        val x = if (rtl) mg else mg + indent   // the indent eats the right edge in Arabic
        var i = 0
        while (i < l.lineCount) {
            var j = i
            while (j < l.lineCount && y + (l.getLineBottom(j) - l.getLineTop(i)) <= ph - mg) j++
            if (j == i) {
                if (y > mg + 1f) { newPage(); continue }
                j = i + 1
            }
            y += drawLines(l, x, y, i, j)
            i = j
        }
        y += gap
    }

    fun heading(s: String) {
        ensure(40f)
        y += 6f
        text(s, head, gap = 3f)
        canvas.drawLine(mg, y, pw - mg, y, small)
        y += 4f
    }

    fun finish(file: File) {
        page?.let { doc.finishPage(it) }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}

private fun drawMarks(c: Canvas, w: Int, h: Int, marks: List<Mark>) {
    val sw = (w / 200f).coerceAtLeast(2f)
    marks.forEach { mk ->
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mk.color; strokeWidth = sw; style = Paint.Style.STROKE }
        val x1 = mk.x1 * w
        val y1 = mk.y1 * h
        val x2 = mk.x2 * w
        val y2 = mk.y2 * h
        if (mk.type == "ARROW") {
            c.drawLine(x1, y1, x2, y2, p)
            val ang = atan2(y2 - y1, x2 - x1)
            val len = w / 25f
            for (da in floatArrayOf(0.5f, -0.5f)) {
                val a = ang + Math.PI.toFloat() - da
                c.drawLine(x2, y2, x2 + len * cos(a), y2 + len * sin(a), p)
            }
        } else {
            val r = hypot(x2 - x1, y2 - y1)
            if (mk.filled) {
                val f = Paint(p).apply { style = Paint.Style.FILL; alpha = 80 }
                c.drawCircle(x1, y1, r, f)
            }
            c.drawCircle(x1, y1, r, p)
        }
    }
}

/**
 * Loads an image at the resolution the page can show: its placed size (fit in cellW x maxH points) at [dpi],
 * never larger than the original. Marks are drawn after resizing so their strokes stay proportional.
 */
private fun loadBitmap(file: File, cellW: Float, maxH: Float, dpi: Int, marks: List<Mark>): Bitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, o)
    if (o.outWidth <= 0 || o.outHeight <= 0) return null
    val rot = try {
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
            6 -> 90
            3 -> 180
            8 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }
    val ow = if (rot == 90 || rot == 270) o.outHeight else o.outWidth
    val oh = if (rot == 90 || rot == 270) o.outWidth else o.outHeight
    val fit = min(cellW / ow, maxH / oh)                       // points per source pixel when placed
    val tw = min((ow * fit / 72f * dpi).roundToInt(), ow).coerceAtLeast(1)
    val th = (tw.toFloat() * oh / ow).roundToInt().coerceAtLeast(1)
    val longSide = max(tw, th)
    var s = 1
    while (max(o.outWidth, o.outHeight) / (s * 2) >= longSide) s *= 2
    var b = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
    if (rot != 0) {
        val r = Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
        if (r !== b) b.recycle()
        b = r
    }
    if (b.width > tw) {
        val r = Bitmap.createScaledBitmap(b, tw, th, true)
        if (r !== b) b.recycle()
        b = r
    }
    if (marks.isNotEmpty() || b.config != Bitmap.Config.ARGB_8888) {
        val m = b.copy(Bitmap.Config.ARGB_8888, true)
        b.recycle()
        drawMarks(Canvas(m), m.width, m.height, marks)
        b = m
    }
    b.setHasAlpha(false)   // opaque, so the PDF stores plain RGB (the compressor needs that)
    return b
}

suspend fun buildCasePdf(
    ctx: Context,
    repo: Repo,
    patientId: Long,
    scope: String,
    opts: PdfOptions
): File = withContext(Dispatchers.IO) {
    val hideIdentity = opts.hideIdentity
    val withImages = opts.withImages
    val tx = Tx(ctx, opts.lang == "AR")
    val quality = ExportPrefs.quality(ctx)
    val doctor = listOf(ExportPrefs.doctorName(ctx), ExportPrefs.doctorTitle(ctx)).filter { it.isNotBlank() }.joinToString(" — ")
    val signed = opts.sign && ExportPrefs.doctorName(ctx).isNotBlank()
    val dao = repo.dao
    val patient = dao.patient(patientId).first() ?: error("no patient")
    val allVisits = dao.visits(patientId).first()
    val media = dao.mediaOfPatient(patientId)
    val diags = dao.allDiagnoses().first().filter { it.patientId == patientId }
    val marks = dao.allMarks().first()
    val labVals = dao.allLabValues().first()
    val orders = dao.allOrders().first()
    val labels = visitLabels(ctx, allVisits) { id, a -> tx.s(id, *a) }
    // Latin digits in both languages: they read the same in an Arabic and an English report.
    val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM, if (tx.ar) Locale.forLanguageTag("ar-u-nu-latn") else Locale.ENGLISH)
    fun d(t: Long) = fmt.format(Date(t))
    fun s(id: Int) = tx.s(id)

    val visits = when {
        scope.startsWith("V") -> allVisits.filter { it.id == scope.drop(1).toLongOrNull() }
        else -> allVisits
    }.sortedBy { it.date }

    fun region(n: String) = enumOrNull<Region>(n)?.let { tx.s(it.label) } ?: n
    fun view(n: String) = enumOrNull<ViewPos>(n)?.let { tx.s(it.label) } ?: ""
    fun stage(n: String) = enumOrNull<Stage>(n)?.let { tx.s(it.label) } ?: n
    fun cat(n: String) = enumOrNull<Category>(n)?.let { tx.s(it.label) } ?: n
    fun diagOf(m: Media) = diags.firstOrNull { it.region == m.region && it.side == m.side }?.text?.let { tx.pick(it) }

    val w = PdfWriter(tx.ar, if (signed) doctor else "")
    w.text("Doctor Class", w.title, gap = 4f)
    if (signed) w.text(doctor, w.sub, gap = 2f)   // header signature
    w.text(d(System.currentTimeMillis()), w.small, gap = 6f)

    // ---- patient
    if (!hideIdentity) {
        w.text(patient.name.ifBlank { s(R.string.unnamed) }, w.sub)
        w.text(listOf(patient.age, patient.residence, patient.phone).filter { it.isNotBlank() }.joinToString(" · "))
    } else if (patient.age.isNotBlank()) {
        w.text(s(R.string.age) + ": " + patient.age, w.sub)
    }
    fun line(label: String, value: String) { if (value.isNotBlank()) w.text("$label: $value") }
    line(s(R.string.complaint), patient.complaint)
    line(s(R.string.delay_info), patient.delay)
    line(s(R.string.mechanism), patient.mechanism)
    line(s(R.string.diagnosis), patient.diagnosis)
    line(s(R.string.probable_dx), patient.probableDiagnosis)
    line(s(R.string.description), patient.description)
    fun hist(v: String) = if (tx.isText(v, R.string.none_opt)) tx.s(R.string.none_opt) else v
    line(s(R.string.pmh), hist(patient.pmh))
    line(s(R.string.psh), hist(patient.psh))
    line(s(R.string.drugs), hist(patient.drugs))
    line(s(R.string.allergy), hist(patient.allergy))
    line(s(R.string.family), hist(patient.family))
    line(s(R.string.habits), patient.habits)
    // "None" / "Normal" are stored bilingual; show them in the report language like everything else.
    fun hv(v: String) = if (tx.isText(v, R.string.none_opt)) tx.s(R.string.none_opt) else if (tx.isText(v, R.string.normal_opt)) tx.s(R.string.normal_opt) else v
    line(s(R.string.h_smoking), hv(patient.smoking))
    line(s(R.string.h_alcohol), hv(patient.alcohol))
    line(s(R.string.h_urination), hv(patient.urination))
    line(s(R.string.h_defecation), hv(patient.defecation))
    line(s(R.string.h_sleep), hv(patient.sleep))
    line(s(R.string.h_eating), hv(patient.eating))
    val derived = media.mapNotNull { diagOf(it) }.distinct()
    if (derived.isNotEmpty()) line(s(R.string.dx_from_images), derived.joinToString(" · "))

    visits.forEach { v ->
        w.heading(labels[v.id].orEmpty() + " · " + d(v.date) + " · " + tx.s(R.string.day_fmt, dayNumber(v, allVisits)))
        if (v.keywords.isNotBlank()) w.text(v.keywords.split("|").filter { it.isNotBlank() }.joinToString(" ") { "#$it" }, w.small)
        enumOrNull<GeneralCondition>(v.generalCondition)?.let { line(s(R.string.sec_general), tx.s(it.label)) }
        enumOrNull<VitalsState>(v.vitalsStable)?.let { line(s(R.string.sec_vitals), tx.s(it.label)) }
        dao.vitals(v.id).first().forEach { r ->
            val t = enumOrNull<VitalType>(r.type)?.let { tx.s(it.label) } ?: r.type
            w.text("${r.time} · $t · ${r.value}", indent = 8f, gap = 0f)
        }
        dao.procedures(v.id).first().forEach { pr ->
            val t = enumOrNull<ProcType>(pr.type)?.let { tx.s(it.label) } ?: pr.type
            val mark = if (pr.status == ProcStatus.DONE.name) "✔ " else "+ "
            val target = if (pr.region.isNotBlank()) limbName(tx, pr.region, pr.side) else ""
            val parts = listOf(target, pr.bone, tx.preset(pr.choice), tx.preset(pr.position), pr.detail, pr.time).filter { it.isNotBlank() }
            val pending = if (pr.status == ProcStatus.DONE.name) "" else " (" + s(R.string.proc_pending) + ")"
            w.text(mark + t + (if (parts.isEmpty()) "" else " — " + parts.joinToString(" · ")) + pending, indent = 8f, gap = 0f)
        }
        orders.filter { it.visitId == v.id }.forEach { o ->
            val k = enumOrNull<OrderKind>(o.kind)?.let { tx.s(it.label) } ?: ""
            w.text("$k ${o.name} ${o.dose} ${if (o.qty.isNotBlank()) "x " + o.qty else ""} ${if (o.perDay.isNotBlank()) o.perDay + "/d" else ""} ${o.time}".replace(Regex(" +"), " "), indent = 8f, gap = 0f)
        }
        labVals.filter { it.visitId == v.id }.forEach { lv -> w.text("${lv.name}: ${lv.value} ${lv.unit}", indent = 8f, gap = 0f) }
        line(s(R.string.sec_lab_text), v.labText)
        line(s(R.string.notes), v.notes)
        v.extras.split("\u0002").filter { it.isNotBlank() }.forEach {
            val i = it.indexOf('\u0001')
            if (i > 0) line(it.substring(0, i), it.substring(i + 1))
        }

        if (withImages) {
            val vm = media.filter { it.visitId == v.id && !it.isVideo }
            Category.values().forEach { c ->
                val list = vm.filter { it.category == c.name }
                if (list.isEmpty()) return@forEach
                w.ensure(30f)
                w.text(cat(c.name), w.sub, gap = 4f)
                val cellW = (w.pw - 2 * w.mg - 10f) / 2f
                // Fractures, scans and documents keep more resolution than clinical photos.
                val dpi = if (c == Category.CLINICAL) quality.dpiPhoto else quality.dpiImaging
                list.chunked(2).forEach { row ->
                    val bmps = row.map { m -> loadBitmap(repo.file(m.path), cellW, 480f, dpi, marks.filter { it.mediaId == m.id }) }
                    val sizes = bmps.map { b ->
                        if (b == null) 0f to 0f else {
                            val sc = min(cellW / b.width, 480f / b.height)
                            b.width * sc to b.height * sc
                        }
                    }
                    val rowH = (sizes.maxOfOrNull { it.second } ?: 0f) + 34f
                    w.ensure(rowH)
                    row.forEachIndexed { i, m ->
                        // Arabic reads right to left: the first image goes on the right.
                        val x = if (w.rtl) w.pw - w.mg - cellW - i * (cellW + 10f) else w.mg + i * (cellW + 10f)
                        val bx = if (w.rtl) x + cellW - sizes[i].first else x
                        bmps[i]?.let { b ->
                            w.canvas.drawBitmap(b, null, RectF(bx, w.y, bx + sizes[i].first, w.y + sizes[i].second), null)
                            b.recycle()
                        }
                        val cap = listOf(
                            region(m.region) + " " + sideShort(m.side) + " " + view(m.viewPos),
                            if (m.stage.isNotBlank()) stage(m.stage) else "",
                            if (m.healthy) s(R.string.healthy_short) else "",
                            diagOf(m).orEmpty(),
                            m.caption
                        ).filter { it.isNotBlank() }.joinToString(" · ").trim()
                        if (cap.isNotBlank()) {
                            val layout = w.layout(cap, w.small, cellW.toInt())
                            w.drawLines(layout, x, w.y + sizes[i].second + 2f, 0, minOf(layout.lineCount, 3))
                        }
                    }
                    w.y += rowH
                }
            }
        }
    }

    // ---- signature block at the end of the report
    if (signed) {
        w.ensure(110f)
        w.y += 16f
        w.text(s(R.string.pdf_doctor) + ": " + doctor, w.sub)
        w.text(d(System.currentTimeMillis()), w.small, gap = 20f)
        val x0 = if (w.rtl) w.pw - w.mg - 170f else w.mg
        w.canvas.drawLine(x0, w.y, x0 + 170f, w.y, w.small)
        w.y += 4f
        w.text(s(R.string.pdf_signature), w.small)
    }

    val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val base = if (hideIdentity) "case" else patient.name.ifBlank { "case" }.replace(Regex("[^\\p{L}\\p{N}]+"), "_")
    val file = File(dir, "DoctorClass_$base.pdf")
    w.finish(file)
    // Android stores pictures uncompressed; re-encode them as JPEG (falls back to the original on any doubt).
    if (withImages) PdfSlim.slim(file, quality.jpeg, w.n)
    file
}

private fun limbName(tx: Tx, region: String, side: String): String {
    val r = enumOrNull<Region>(region)?.let { tx.s(it.label) } ?: region
    return (r + " " + sideShort(side)).trim()
}

/** Builds the PDF and opens the system share sheet, so the receiver needs no app. */
suspend fun sharePdf(ctx: Context, repo: Repo, patientId: Long, scope: String, opts: PdfOptions) {
    ExportPrefs.setLang(ctx, patientId, opts.lang)
    Toast.makeText(ctx, Lang.get(ctx, R.string.preparing_pdf), Toast.LENGTH_SHORT).show()
    val file = buildCasePdf(ctx, repo, patientId, scope, opts)
    Toast.makeText(ctx, Lang.get(ctx, R.string.pdf_ready_fmt, String.format(Locale.US, "%.1f", file.length() / 1048576.0)), Toast.LENGTH_SHORT).show()
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
