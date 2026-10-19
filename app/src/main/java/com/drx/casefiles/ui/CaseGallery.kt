package com.drx.casefiles.ui

import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import com.drx.casefiles.data.limbContext
import com.drx.casefiles.data.ImagePrefs
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Spacer
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.drx.casefiles.R
import androidx.compose.ui.unit.dp
import com.drx.casefiles.data.Media
import com.drx.casefiles.data.Repo
import com.drx.casefiles.data.Visit
import com.drx.casefiles.data.regionText
import com.drx.casefiles.data.sideShort
import com.drx.casefiles.data.viewText
import java.text.DateFormat
import java.util.Date

/** Categories shown in the gallery: the pictures that describe the injury itself. */
private val GalleryCats = setOf("XRAY", "CT", "MRI", "CLINICAL", "ECHO")

private const val GALLERY_PAIRS = 4

/** Two images are "the same view" when body part, side and radiographic view match. */
private fun poseKey(m: Media): String = m.region + "|" + m.side + "|" + m.viewPos

@Composable
private fun poseLabel(m: Media): String =
    listOf(regionText(m.region), sideShort(m.side), viewText(m.viewPos)).filter { it.isNotBlank() }.joinToString(" ")

@Composable
private fun GalleryCell(m: Media?, repo: Repo, modifier: Modifier, onClick: () -> Unit) {
    if (m == null) {
        Box(
            modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) { Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        Thumb(m, repo, false, modifier, onClick = onClick, onLong = onClick)
    }
}

/**
 * Section 0. The first diagnostic images (first visit) on one side and the images of the latest follow-up
 * on the other, paired by view (AP, lateral...), so progress can be read at a glance. Tapping a pair opens the
 * comparison screen; "all images" opens the full gallery.
 */
@Composable
fun CaseGallery(patientId: Long, media: List<Media>, visits: List<Visit>, repo: Repo, nav: Nav) {
    val fmt = remember { DateFormat.getDateInstance() }
    var showAll by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    val initial = visits.firstOrNull { it.kind == "INITIAL" } ?: visits.minByOrNull { it.date }
    // Gallery import: nothing is asked. The image joins the first visit with the limb already being worked up
    // and the category used last time (X-ray the first time).
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        val v = initial
        if (v != null && uris.isNotEmpty()) {
            repo.appScope.launch {
                val limb = repo.limbContext(v.patientId)
                repo.importImages(
                    uris,
                    Media(visitId = v.id, patientId = v.patientId, path = "", category = ImagePrefs.category(ctx), region = limb.first, side = limb.second)
                )
            }
        }
    }
    fun imgs(v: Visit?): List<Media> =
        if (v == null) emptyList() else media.filter { it.visitId == v.id && it.category in GalleryCats && !it.isVideo }

    val firstImgs = imgs(initial).sortedWith(
        compareBy<Media>({ if (it.category == "XRAY") 0 else if (it.category == "CT" || it.category == "MRI") 1 else 2 }, { it.region })
    )
    val last = visits.filter { it.id != initial?.id && imgs(it).isNotEmpty() }.maxByOrNull { it.date }
    val lastImgs = imgs(last)

    val keys = (firstImgs.map { poseKey(it) } + lastImgs.map { poseKey(it) }).distinct()
    val pairs = keys.map { k ->
        Triple(k, firstImgs.firstOrNull { poseKey(it) == k }, lastImgs.lastOrNull { poseKey(it) == k })
    }
    val shown = if (showAll) pairs else pairs.take(GALLERY_PAIRS)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { nav.push(Screen.Gallery(patientId)) }) { Text(stringResource(R.string.gal_all)) }
            Spacer(Modifier.weight(1f))
            if (initial != null) {
                IconButton(onClick = {
                    CamPrefs.explicit = false
                    nav.push(Screen.Camera(initial.id, ImagePrefs.category(ctx)))
                }) { Text("📷", fontSize = 22.sp) }
                IconButton(onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }) { Text("🖼", fontSize = 22.sp) }
            }
        }
        if (pairs.isEmpty()) {
            Text(stringResource(R.string.gal_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.gal_first), style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
                Text(
                    if (last != null) stringResource(R.string.gal_last) + " · " + fmt.format(Date(last.date)) else stringResource(R.string.gal_no_followup),
                    style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
                )
            }
            shown.forEach { (_, a, b) ->
                (a ?: b)?.let { Text(poseLabel(it), style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val open = {
                        if (a != null && b != null) nav.push(Screen.Compare(a, b))
                        else (a ?: b)?.let { nav.push(Screen.Viewer(listOf(it), 0)) }
                        Unit
                    }
                    GalleryCell(a, repo, Modifier.weight(1f), open)
                    GalleryCell(b, repo, Modifier.weight(1f), open)
                }
            }
            if (pairs.size > GALLERY_PAIRS) {
                TextButton(onClick = { showAll = !showAll }) {
                    Text(if (showAll) stringResource(R.string.gal_less) else stringResource(R.string.more_fmt, pairs.size - GALLERY_PAIRS))
                }
            }
        }
    }
}
