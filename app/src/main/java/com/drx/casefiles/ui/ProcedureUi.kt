@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.drx.casefiles.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import com.drx.casefiles.data.limbContext
import com.drx.casefiles.data.Region
import com.drx.casefiles.data.Lang
import com.drx.casefiles.data.Media
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drx.casefiles.R
import com.drx.casefiles.data.Category
import com.drx.casefiles.data.ProcGroup
import com.drx.casefiles.data.ProcOption
import com.drx.casefiles.data.ProcPresets
import com.drx.casefiles.data.ProcStatus
import com.drx.casefiles.data.ProcType
import com.drx.casefiles.data.Procedure
import com.drx.casefiles.data.Repo
import com.drx.casefiles.data.Vital
import com.drx.casefiles.data.VitalType
import com.drx.casefiles.data.enumOrNull
import com.drx.casefiles.data.sectionOf
import com.drx.casefiles.data.limbText
import com.drx.casefiles.data.regionText
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun hm(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date())

/** Display texts of the ready-made options of one dropdown key. */
@Composable
private fun presetTexts(key: String?): List<String> {
    if (key == null) return emptyList()
    return (ProcPresets[key] ?: emptyList()).map { stringResource(it) }
}

/**
 * A title and its value on one line. Tapping the title opens the ready options (presets plus everything the
 * user typed here before). Typing a new text and confirming (Done or the tick) adds it to this dropdown for good
 * and saves it in the same field. Options the user added carry a small x to remove them again.
 */
@Composable
fun SmartField(
    title: String,
    value: String,
    presets: List<String>,
    custom: List<ProcOption>,
    onValue: (String) -> Unit,
    onAddOption: (String) -> Unit,
    onRemoveOption: (ProcOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(Lang.pick(value)) }
    val all = (presets + custom.map { it.text }).distinct()

    fun commit() {
        val t = text.trim()
        if (t.isNotEmpty() && all.none { it.equals(t, true) }) onAddOption(t)
        if (t != Lang.pick(value)) onValue(t)
        open = false
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth()
                .clickable { if (open) commit() else { text = Lang.pick(value); open = true } }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(8.dp))
            Text(
                Lang.pick(value).ifBlank { "—" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Text(if (open) "▴" else "▾")
        }
        if (open) {
            OutlinedTextField(
                text, { text = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.proc_choose)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                trailingIcon = { IconButton(onClick = { commit() }) { Icon(Icons.Default.Check, null) } },
                modifier = Modifier.fillMaxWidth()
            )
            val q = text.trim()
            val shown = all.filter { q.isEmpty() || it.contains(q, true) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEach { o ->
                    val mine = custom.firstOrNull { it.text == o }
                    if (mine != null) {
                        FilterChip(
                            selected = o == Lang.pick(value),
                            onClick = { text = o; onValue(o); open = false },
                            label = { Text(o) },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close, null,
                                    Modifier.size(16.dp).clickable { onRemoveOption(mine) }
                                )
                            }
                        )
                    } else {
                        FilterChip(
                            selected = o == Lang.pick(value),
                            onClick = { text = o; onValue(o); open = false },
                            label = { Text(o) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * One line of a section. Check = done / present; unchecked = still required (the card is tinted).
 * Right side: 📷 camera, 🖼 gallery (both attach to this row, with the limb and stage it implies),
 * and (+) for details and exact measurements. Nothing is asked before an image is added.
 */
@Composable
private fun ProcedureRow(
    p: Procedure,
    options: List<ProcOption>,
    images: List<Media>,
    repo: Repo,
    nav: Nav?,
    onEdit: ((Procedure) -> Procedure) -> Unit,
    onAddOption: (String, String) -> Unit,
    onRemoveOption: (ProcOption) -> Unit,
    onDelete: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onChangeLimb: () -> Unit,
    rowExtra: (@Composable (Procedure) -> Unit)? = null
) {
    val type = enumOrNull<ProcType>(p.type) ?: return
    var details by remember { mutableStateOf(false) }
    val done = p.status == ProcStatus.DONE.name
    val title = stringResource(type.label)
    val ck = type.choiceKey
    val summary = listOf(Lang.pick(p.position), p.detail, p.time).filter { it.isNotBlank() }.joinToString(" · ")

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (done) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(
                    checked = done,
                    onCheckedChange = { on ->
                        onEdit { it.copy(status = if (on) ProcStatus.DONE.name else ProcStatus.PENDING.name) }
                    }
                )
                Column(Modifier.weight(1f)) {
                    if (p.region.isNotBlank()) {
                        // Tap the limb to change it; it was filled in from what is already known about the patient.
                        Text(
                            limbText(p.region, p.side) + " (" + regionText(p.region) + ")" +
                                (if (p.bone.isNotBlank()) " · " + p.bone else ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onChangeLimb() }.padding(top = 10.dp)
                        )
                    }
                    if (ck != null) {
                        SmartField(
                            title = title,
                            value = p.choice,
                            presets = presetTexts(ck),
                            custom = options.filter { it.field == ck },
                            onValue = { v -> onEdit { it.copy(choice = v) } },
                            onAddOption = { t -> onAddOption(ck, t) },
                            onRemoveOption = onRemoveOption
                        )
                    } else {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.clickable { details = !details }.padding(top = 12.dp, bottom = 6.dp)
                        )
                    }
                    if (!details && summary.isNotBlank()) {
                        Text(summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (nav != null) {
                    IconButton(onClick = onCamera) { Text("📷", fontSize = 20.sp) }
                    IconButton(onClick = onGallery) { Text("🖼", fontSize = 20.sp) }
                }
                IconButton(onClick = { details = !details }) {
                    if (details) Text("–", fontSize = 22.sp) else Icon(Icons.Default.Add, null)
                }
            }
            if (details) {
                Column(Modifier.padding(start = 12.dp, end = 8.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    type.positionKey?.let { pk ->
                        SmartField(
                            title = stringResource(R.string.proc_position),
                            value = p.position,
                            presets = presetTexts(pk),
                            custom = options.filter { it.field == pk },
                            onValue = { v -> onEdit { it.copy(position = v) } },
                            onAddOption = { t -> onAddOption(pk, t) },
                            onRemoveOption = onRemoveOption
                        )
                    }
                    DebouncedField(stringResource(R.string.proc_details), p.detail, last = true) { t -> onEdit { it.copy(detail = t) } }
                    if (type.perLimb) {
                        DebouncedField(stringResource(R.string.proc_bone), p.bone, last = true) { t -> onEdit { it.copy(bone = t) } }
                    }
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.proc_remove))
                    }
                }
            }
            if (rowExtra != null) {
                rowExtra(p)
            } else {
                val mine = images.filter { it.procedureId == p.id }
                if (mine.isNotEmpty() && nav != null) {
                    MediaGrid(
                        items = mine, repo = repo, selected = emptySet(), maxRows = null, onExpand = {},
                        onClick = { m -> nav.push(Screen.Viewer(mine, mine.indexOf(m).coerceAtLeast(0))) },
                        onLong = {},
                        columns = 4
                    )
                }
            }
        }
    }
}

/**
 * A section of one visit (4 diagnostic means, 5 procedures, 6 consultations, 7 supplies, 8 follow-up).
 * One "Add" button lists what can be added. A per-limb row takes the limb already known for the patient (latest
 * image, procedure or diagnosis) and only asks when nothing is known yet; tap the limb on the row to change it.
 * [showBp] adds the blood-pressure readings (several per visit). Pictures added from a row are normal images of the
 * visit, so they show in the gallery too. [rowExtra] replaces the default picture strip of a row (consultations).
 */
@Composable
fun ProcedureBlock(
    repo: Repo,
    visitId: Long,
    group: ProcGroup,
    nav: Nav? = null,
    showBp: Boolean = false,
    rowExtra: (@Composable (Procedure) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val visit by remember(visitId) { repo.dao.visit(visitId) }.collectAsState(null)
    val procs by remember(visitId) { repo.dao.procedures(visitId) }.collectAsState(emptyList())
    val options by remember { repo.dao.procOptions() }.collectAsState(emptyList())
    val vitals by remember(visitId) { repo.dao.vitals(visitId) }.collectAsState(emptyList())
    val media by remember(visitId) { repo.dao.media(visitId) }.collectAsState(emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var pendingType by remember { mutableStateOf<ProcType?>(null) }
    var limbFor by remember { mutableStateOf<Procedure?>(null) }
    var importFor by remember { mutableStateOf<Procedure?>(null) }
    var showBpDialog by remember { mutableStateOf(false) }
    val types = ProcType.values().filter { it.offeredIn(group) }

    fun edit(id: Long, f: (Procedure) -> Procedure) {
        repo.appScope.launch {
            val cur = repo.dao.procedureOnce(id) ?: return@launch
            repo.dao.updateProcedure(f(cur))
        }
    }

    fun add(t: ProcType, region: String, side: String) {
        repo.appScope.launch {
            repo.dao.insertProcedure(Procedure(visitId = visitId, type = t.name, section = group.name, region = region, side = side, time = hm()))
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        val p = importFor
        val v = visit
        val t = p?.let { enumOrNull<ProcType>(it.type) }
        if (p != null && v != null && t != null && uris.isNotEmpty()) {
            scope.launch {
                val (r, sd) = if (p.region.isNotBlank()) p.region to p.side else repo.limbContext(v.patientId)
                repo.importImages(
                    uris,
                    Media(
                        visitId = v.id, patientId = v.patientId, path = "", category = t.imageCat,
                        region = r, side = sd, stage = t.imageStage, procedureId = p.id
                    )
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        procs.filter { it.sectionOf() == group && enumOrNull<ProcType>(it.type) != null }.forEach { p ->
            key(p.id) {
                val t = enumOrNull<ProcType>(p.type)
                ProcedureRow(
                    p = p,
                    options = options,
                    images = media,
                    repo = repo,
                    nav = nav,
                    onEdit = { f -> edit(p.id, f) },
                    onAddOption = { field, txt -> repo.appScope.launch { repo.dao.insertProcOption(ProcOption(field = field, text = txt)) } },
                    onRemoveOption = { o -> repo.appScope.launch { repo.dao.deleteProcOption(o) } },
                    onDelete = { repo.appScope.launch { repo.dao.unlinkMedia(p.id); repo.dao.deleteProcedure(p) } },
                    onCamera = {
                        if (t != null) {
                            CamPrefs.explicit = p.region.isNotBlank()
                            if (p.region.isNotBlank()) {
                                CamPrefs.region = p.region
                                CamPrefs.side = p.side
                            }
                            CamPrefs.stage = t.imageStage
                            CamPrefs.procedureId = p.id
                            nav?.push(Screen.Camera(visitId, t.imageCat))
                        }
                    },
                    onGallery = {
                        importFor = p
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    onChangeLimb = { limbFor = p },
                    rowExtra = rowExtra
                )
            }
        }

        if (showBp) {
            val readings = vitals.filter { it.type == "BP" }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pt_bp), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { showBpDialog = true }) { Icon(Icons.Default.Add, null) }
            }
            readings.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.time + " · " + r.value, modifier = Modifier.weight(1f))
                    IconButton(onClick = { repo.appScope.launch { repo.dao.deleteVital(r) } }) { Icon(Icons.Default.Close, null) }
                }
            }
        }

        OutlinedButton(onClick = { showAdd = true }) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.proc_add_btn))
        }
    }

    if (showAdd) {
        ChoiceDialog(
            title = stringResource(R.string.proc_add_btn),
            // A long list shows the first six, the rest sit behind "More".
            options = (if (showAll || types.size <= 7) types else types.take(6)).map { it.name to stringResource(it.label) } +
                (if (!showAll && types.size > 7) listOf("MORE" to stringResource(R.string.more_btn)) else emptyList()),
            onPick = { key ->
                if (key == "MORE") {
                    showAll = true
                    return@ChoiceDialog
                }
                showAdd = false
                showAll = false
                val t = enumOrNull<ProcType>(key)
                if (t != null) {
                    if (!t.perLimb) {
                        add(t, "", "NONE")
                    } else {
                        scope.launch {
                            val ctxLimb = visit?.let { repo.limbContext(it.patientId) }
                            if (ctxLimb != null && ctxLimb.first != Region.OTHER.name) add(t, ctxLimb.first, ctxLimb.second)
                            else pendingType = t   // nothing known yet: ask once
                        }
                    }
                }
            },
            onDismiss = { showAdd = false; showAll = false }
        )
    }
    pendingType?.let { t ->
        LimbPickerDialog(
            onPick = { r, sd ->
                pendingType = null
                add(t, r.name, sd)
            },
            onDismiss = { pendingType = null }
        )
    }
    limbFor?.let { p ->
        LimbPickerDialog(
            onPick = { r, sd ->
                limbFor = null
                edit(p.id) { it.copy(region = r.name, side = sd) }
            },
            onDismiss = { limbFor = null }
        )
    }
    if (showBpDialog) {
        VitalDialog(
            onConfirm = { type, value, time ->
                showBpDialog = false
                repo.appScope.launch { repo.dao.insertVital(Vital(visitId = visitId, time = time, type = type, value = value)) }
            },
            onDismiss = { showBpDialog = false },
            types = listOf(VitalType.BP)
        )
    }
}
