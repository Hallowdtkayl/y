package com.drx.casefiles.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The limb the person is working on, so nothing has to be named or picked by hand: the region and side of the
 * newest thing recorded for the patient (an image or a procedure row), else of their first diagnosis, else "other".
 */
suspend fun Repo.limbContext(patientId: Long): Pair<String, String> = withContext(Dispatchers.IO) {
    val visitIds = dao.visits(patientId).first().map { it.id }.toSet()
    val img = dao.mediaOfPatient(patientId)
        .filter { it.region.isNotBlank() && it.region != Region.OTHER.name }
        .maxByOrNull { it.takenAt }
    val proc = dao.allProcedures().first()
        .filter { it.visitId in visitIds && it.region.isNotBlank() && it.region != Region.OTHER.name }
        .maxByOrNull { it.createdAt }
    val newest = listOfNotNull(
        img?.let { Triple(it.takenAt, it.region, it.side) },
        proc?.let { Triple(it.createdAt, it.region, it.side) }
    ).maxByOrNull { it.first }
    if (newest != null) return@withContext newest.second to newest.third
    val dx = dao.allDiagnoses().first().firstOrNull { it.patientId == patientId && it.region != Region.OTHER.name }
    if (dx != null) dx.region to dx.side else Region.OTHER.name to Side.NONE.name
}

/** Remembers the category (X-ray, clinical photo...) of the last image added, for one-tap imports. */
object ImagePrefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("dc", Context.MODE_PRIVATE)
    fun category(ctx: Context): String = p(ctx).getString("img_cat", Category.XRAY.name) ?: Category.XRAY.name
    fun setCategory(ctx: Context, c: String) = p(ctx).edit().putString("img_cat", c).apply()
}
