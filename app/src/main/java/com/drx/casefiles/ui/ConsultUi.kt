package com.drx.casefiles.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drx.casefiles.R
import com.drx.casefiles.data.Media
import com.drx.casefiles.data.ProcGroup
import com.drx.casefiles.data.ProcStatus
import com.drx.casefiles.data.ProcType
import com.drx.casefiles.data.Procedure
import com.drx.casefiles.data.Repo
import com.drx.casefiles.data.enumOrNull
import com.drx.casefiles.data.sectionOf
import kotlinx.coroutines.launch

@Composable
private fun consultLabel(p: Procedure): String =
    (enumOrNull<ProcType>(p.type)?.let { stringResource(it.label) } ?: p.type) +
        (if (p.choice.isNotBlank()) " · " + p.choice else "")

/**
 * Section 6: requested consultations (general surgery, neurosurgery, internal, anesthesia, cardiology...).
 * A closed dropdown by default so the page stays short; the tag shows how many are still pending.
 *
 * Request images can be merged or kept apart: an image is shared by all consultations of the visit until it is
 * linked to one consultation (long-press it), and can be moved back to the shared pool the same way.
 * Images imported from a consultation row are linked to it at once; camera shots go to the shared pool.
 */
@Composable
fun ConsultSection(repo: Repo, visitId: Long, nav: Nav) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var importFor by remember { mutableStateOf<Long?>(null) }
    var linkTarget by remember { mutableStateOf<Media?>(null) }
    var delTarget by remember { mutableStateOf<Media?>(null) }
    val visit by remember(visitId) { repo.dao.visit(visitId) }.collectAsState(null)
    val procs by remember(visitId) { repo.dao.procedures(visitId) }.collectAsState(emptyList())
    val media by remember(visitId) { repo.dao.media(visitId) }.collectAsState(emptyList())

    val rows = procs.filter { it.sectionOf() == ProcGroup.CONSULT }
    val images = media.filter { it.category == "CONSULT" && !Undo.isHidden("m" + it.id) }
    val rowIds = rows.map { it.id }.toSet()
    // Images whose consultation was deleted fall back to the shared pool, so nothing is ever hidden.
    val shared = images.filter { m -> m.procedureId == null || !rowIds.contains(m.procedureId) }
    val pending = rows.count { it.status != ProcStatus.DONE.name }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        val v = visit
        if (v != null && uris.isNotEmpty()) {
            val target = importFor
            scope.launch {
                repo.importImages(
                    uris,
                    Media(visitId = v.id, patientId = v.patientId, path = "", category = "CONSULT", procedureId = target)
                )
            }
        }
    }
    fun pickImages(target: Long?) {
        importFor = target
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    }

    FoldCard(
        icon = "🩺",
        title = stringResource(R.string.sec_consults),
        badge = if (pending > 0) pending.toString() else if (rows.isNotEmpty()) rows.size.toString() else null,
        badgeAlert = pending > 0
    ) {
        run {
            ProcedureBlock(
                repo, visitId, ProcGroup.CONSULT, nav,
                rowExtra = { p ->
                    val mine = images.filter { it.procedureId == p.id }
                    Column(Modifier.padding(start = 12.dp, top = 4.dp)) {
                        if (mine.isNotEmpty()) {
                            MediaGrid(
                                items = mine, repo = repo, selected = emptySet(), maxRows = null, onExpand = {},
                                onClick = { m -> nav.push(Screen.Viewer(mine, mine.indexOf(m).coerceAtLeast(0))) },
                                onLong = { m -> linkTarget = m },
                                columns = 4
                            )
                        }
                    }
                }
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.cons_shared) + " (" + shared.size + ")",
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { pickImages(null) }) { Text("🖼", fontSize = 20.sp) }
                IconButton(onClick = { nav.push(Screen.Camera(visitId, "CONSULT")) }) { Text("📷", fontSize = 20.sp) }
            }
            if (shared.isNotEmpty()) {
                MediaGrid(
                    items = shared, repo = repo, selected = emptySet(), maxRows = null, onExpand = {},
                    onClick = { m -> nav.push(Screen.Viewer(shared, shared.indexOf(m).coerceAtLeast(0))) },
                    onLong = { m -> linkTarget = m },
                    columns = 4
                )
            }
        }
    }

    linkTarget?.let { m ->
        val opts = buildList {
            add("S" to stringResource(R.string.cons_shared_opt))
            rows.forEach { p -> add(("P" + p.id) to consultLabel(p)) }
            add("D" to stringResource(R.string.cons_delete))
        }
        ChoiceDialog(
            title = stringResource(R.string.cons_link),
            options = opts,
            onPick = { key ->
                linkTarget = null
                if (key == "S") {
                    repo.appScope.launch { repo.dao.setMediaProcedure(m.id, null) }
                } else if (key == "D") {
                    delTarget = m
                } else {
                    key.drop(1).toLongOrNull()?.let { pid -> repo.appScope.launch { repo.dao.setMediaProcedure(m.id, pid) } }
                }
            },
            onDismiss = { linkTarget = null }
        )
    }
    delTarget?.let { m ->
        LaunchedEffect(m.id) {
            delTarget = null
            Undo.run(repo, ctx, listOf("m" + m.id)) { repo.deleteMedia(listOf(m)) }
        }
    }
}
