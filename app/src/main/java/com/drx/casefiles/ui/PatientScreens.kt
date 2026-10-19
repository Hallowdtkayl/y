@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.drx.casefiles.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AssistChip
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.drx.casefiles.R
import com.drx.casefiles.data.Media
import com.drx.casefiles.data.Patient
import com.drx.casefiles.data.ProcGroup
import com.drx.casefiles.data.ProcStatus
import com.drx.casefiles.data.sectionOf
import com.drx.casefiles.data.Repo
import com.drx.casefiles.data.Visit
import com.drx.casefiles.data.ExportPrefs
import com.drx.casefiles.data.sharePdf
import com.drx.casefiles.data.dayNumber
import com.drx.casefiles.data.Lang
import com.drx.casefiles.data.visitLabels
import kotlinx.coroutines.delay
import com.drx.casefiles.Pin
import kotlinx.coroutines.launch
import kotlin.system.exitProcess
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(repo: Repo, nav: Nav) {
    val scope = rememberCoroutineScope()
    val patients by remember { repo.dao.patients() }.collectAsState(emptyList())
    val media by remember { repo.dao.allMedia() }.collectAsState(emptyList())
    val visits by remember { repo.dao.allVisits() }.collectAsState(emptyList())
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Patient?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showPin by remember { mutableStateOf(false) }
    var pinTick by remember { mutableStateOf(0) }
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            repo.backupTo(uri)
            Toast.makeText(ctx, Lang.get(ctx, R.string.backup_done), Toast.LENGTH_SHORT).show()
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingRestore = uri }

    val thumbs = remember(media) { media.groupBy { it.patientId }.mapValues { it.value.first() } }
    val kwPatients = remember(visits, q) {
        val w = q.trim().trimStart('#').lowercase()
        if (w.isBlank()) emptySet()
        else visits.filter { v -> v.keywords.split("|").any { it.lowercase().contains(w) } }.map { it.patientId }.toSet()
    }
    val filtered = patients.filter {
        q.isBlank() || it.name.contains(q, true) || it.phone.contains(q, true) ||
            it.complaint.contains(q, true) || it.diagnosis.contains(q, true) ||
            it.probableDiagnosis.contains(q, true) || it.id in kwPatients
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { nav.push(Screen.Search) }) { Icon(Icons.Default.Search, null) }
                    IconButton(onClick = { showSettings = true }) { Text("⚙", fontSize = 20.sp) }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Quick capture: photograph first, name the patient later.
                SmallFloatingActionButton(onClick = {
                    scope.launch {
                        val pid = repo.dao.insertPatient(Patient(name = ""))
                        val vid = repo.dao.insertVisit(Visit(patientId = pid, kind = "INITIAL"))
                        nav.push(Screen.PatientDetail(pid))
                        nav.push(Screen.Camera(vid, "CLINICAL"))
                    }
                }) { Text("📷", fontSize = 18.sp) }
                FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null) }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                q, { q = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_patients)) },
                modifier = Modifier.fillMaxWidth().padding(12.dp, 4.dp)
            )
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_patients), textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
                }
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { p ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { nav.push(Screen.PatientDetail(p.id)) },
                                    onLongClick = { toDelete = p }
                                )
                                .padding(12.dp, 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                val t = thumbs[p.id]
                                if (t != null) {
                                    AsyncImage(repo.file(t.path), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(p.name.ifBlank { stringResource(R.string.unnamed) }, style = MaterialTheme.typography.titleMedium)
                                val sub = p.diagnosis.ifBlank { p.complaint }
                                if (sub.isNotBlank()) {
                                    Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAdd) {
        TextInputDialog(
            stringResource(R.string.new_patient), stringResource(R.string.patient_name),
            onConfirm = { name ->
                showAdd = false
                scope.launch {
                    val id = repo.dao.insertPatient(Patient(name = name))
                    repo.dao.insertVisit(Visit(patientId = id, kind = "INITIAL"))
                    nav.push(Screen.PatientDetail(id))
                }
            },
            onDismiss = { showAdd = false }
        )
    }
    if (showSettings) {
        val pinIsSet = pinTick >= 0 && Pin.isSet(ctx)
        SettingsDialog(
            pinSet = pinIsSet,
            onBackup = { showSettings = false; backupLauncher.launch("DoctorClass-backup.zip") },
            onRestore = { showSettings = false; restoreLauncher.launch(arrayOf("*/*")) },
            onPinSet = { showSettings = false; showPin = true },
            onPinClear = { Pin.clear(ctx); pinTick++; showSettings = false },
            onRefRanges = { showSettings = false; nav.push(Screen.RefRanges) },
            onDismiss = { showSettings = false }
        )
    }
    if (showPin) {
        TextInputDialog(
            stringResource(R.string.pin_set), stringResource(R.string.pin_hint),
            onConfirm = { p -> Pin.set(ctx, p); pinTick++; showPin = false },
            onDismiss = { showPin = false }
        )
    }
    pendingRestore?.let { u ->
        ConfirmDialog(
            stringResource(R.string.restore_q),
            onConfirm = {
                pendingRestore = null
                scope.launch {
                    repo.restoreFrom(u)
                    Toast.makeText(ctx, Lang.get(ctx, R.string.done_restart), Toast.LENGTH_LONG).show()
                    kotlinx.coroutines.delay(1500)
                    exitProcess(0)
                }
            },
            onDismiss = { pendingRestore = null }
        )
    }
    toDelete?.let { p ->
        ConfirmDialog(
            stringResource(R.string.delete_patient_q),
            onConfirm = { toDelete = null; scope.launch { repo.deletePatient(p) } },
            onDismiss = { toDelete = null }
        )
    }
}

@Composable
fun PatientScreen(id: Long, repo: Repo, nav: Nav) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val dbPatient by remember(id) { repo.dao.patient(id) }.collectAsState(null)
    val visitsAll by remember(id) { repo.dao.visits(id) }.collectAsState(emptyList())
    val visits = visitsAll.filter { !Undo.isHidden("v" + it.id) }
    val media by remember(id) { repo.dao.mediaOfPatientFlow(id) }.collectAsState(emptyList())
    val diags by remember { repo.dao.allDiagnoses() }.collectAsState(emptyList())
    val allOrders by remember { repo.dao.allOrders() }.collectAsState(emptyList())
    val allProcs by remember { repo.dao.allProcedures() }.collectAsState(emptyList())
    val allVitals by remember { repo.dao.allVitals() }.collectAsState(emptyList())

    var f by remember { mutableStateOf<Patient?>(null) }
    var dirty by remember { mutableStateOf(false) }
    var showAddVisit by remember { mutableStateOf(false) }
    var visitToDelete by remember { mutableStateOf<Visit?>(null) }
    var showScopePick by remember { mutableStateOf(false) }
    var shareScope by remember { mutableStateOf<String?>(null) }
    var historyOpen by remember { mutableStateOf(false) }

    LaunchedEffect(dbPatient) { if (f == null && dbPatient != null) f = dbPatient }
    LaunchedEffect(f) {
        if (dirty) {
            delay(600)
            f?.let { repo.dao.updatePatient(it.copy(updatedAt = System.currentTimeMillis())) }
            dirty = false
        }
    }
    val latest by rememberUpdatedState(f)
    val latestDirty by rememberUpdatedState(dirty)
    DisposableEffect(Unit) {
        onDispose {
            if (latestDirty) latest?.let { repo.appScope.launch { repo.dao.updatePatient(it) } }
        }
    }
    fun edit(n: Patient) {
        f = n
        dirty = true
    }

    val labels = remember(visits, Lang.ar) { visitLabels(ctx, visits) }
    val dateFmt = remember { DateFormat.getDateInstance() }
    val initialVisit = visits.firstOrNull { it.kind == "INITIAL" }
    fun diagFor(m: Media): String? =
        diags.firstOrNull { it.patientId == m.patientId && it.region == m.region && it.side == m.side }?.text

    /** One line for a follow-up card: latest blood pressure, surgical outcome and what is still pending. */
    fun followSummary(v: Visit): String {
        val ps = allProcs.filter { it.visitId == v.id }
        val bp = allVitals.lastOrNull { it.visitId == v.id && it.type == "BP" }?.value
        val outcome = ps.filter { it.type == "OUTCOME" && it.choice.isNotBlank() }.joinToString(" / ") { it.choice }
        val todo = ps.count { it.sectionOf() == ProcGroup.FOLLOWUP && it.status != ProcStatus.DONE.name }
        return listOfNotNull(
            bp?.let { "🩺 $it" },
            outcome.takeIf { it.isNotBlank() },
            if (todo > 0) "⏳ $todo" else null
        ).joinToString("  ·  ")
    }

    @Composable
    fun RenderVisit(v: Visit, indent: Boolean) {
        val vm: List<Media> = media.filter { it.visitId == v.id }
        Box(Modifier.padding(start = if (indent) 16.dp else 0.dp)) {
            VisitBox(
                v = v,
                label = labels[v.id].orEmpty(),
                day = stringResource(R.string.day_fmt, dayNumber(v, visits)),
                date = dateFmt.format(Date(v.date)),
                thumbs = vm.takeLast(4).reversed(),
                extra = followSummary(v),
                repo = repo,
                onClick = { nav.push(Screen.VisitDetail(v.id)) },
                onLong = { if (v.kind != "INITIAL") visitToDelete = v }
            )
        }
    }

    @Composable
    fun RxBox(vs: List<Visit>, modifier: Modifier) {
        val ids = vs.map { it.id }
        val os = allOrders.filter { it.visitId in ids }
        val rx = media.count { it.visitId in ids && it.category == "RX" }
        Card(
            modifier.clickable { vs.firstOrNull()?.let { nav.push(Screen.VisitDetail(it.id)) } },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
        ) {
            Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.sec_rx), style = MaterialTheme.typography.labelLarge)
                os.take(6).forEach { Text("• " + it.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                if (os.size > 6) Text("+" + (os.size - 6), style = MaterialTheme.typography.bodySmall)
                if (rx > 0) Text("📷 " + rx, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((f?.name.orEmpty()).ifBlank { stringResource(R.string.unnamed) }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = { showScopePick = true }) { Text("📤", fontSize = 20.sp) }
                    IconButton(onClick = { nav.push(Screen.Gallery(id)) }) { Text("🖼", fontSize = 20.sp) }
                }
            )
        }
    ) { pad ->
        val p = f
        if (p == null) {
            Box(Modifier.padding(pad).fillMaxSize())
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // One folding card per section. The data sections are open, the rest are closed with a badge
                // (pending items or counts), so the page stays short and calm.
                val iv = initialVisit?.id
                fun pendingOf(g: ProcGroup): String? {
                    val n = allProcs.count { it.visitId == iv && it.sectionOf() == g && it.status != ProcStatus.DONE.name }
                    return if (n > 0) n.toString() else null
                }

                // 0 · Gallery: first diagnostic images beside the latest follow-up, paired by view.
                item(key = "sec0") {
                    FoldCard("🖼", stringResource(R.string.gallery), startOpen = true) { CaseGallery(id, media, visits, repo, nav) }
                }

                // 1 · Basic data
                item(key = "sec1") {
                    FoldCard("👤", stringResource(R.string.sec_basic), startOpen = true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FormField(stringResource(R.string.patient_name), p.name, Modifier.weight(2f)) { edit(p.copy(name = it)) }
                            FormField(stringResource(R.string.age), p.age, Modifier.weight(1f)) { edit(p.copy(age = it)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FormField(stringResource(R.string.residence), p.residence, Modifier.weight(1f)) { edit(p.copy(residence = it)) }
                            FormField(stringResource(R.string.phone), p.phone, Modifier.weight(1f)) { edit(p.copy(phone = it)) }
                        }
                        FormField(stringResource(R.string.diagnosis), p.diagnosis) { edit(p.copy(diagnosis = it)) }
                        val derived = media.mapNotNull { diagFor(it) }.distinct()
                        if (derived.isNotEmpty()) {
                            Text(stringResource(R.string.dx_from_images), style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                derived.forEach { d ->
                                    FilterChip(p.diagnosis.contains(d), {
                                        if (!p.diagnosis.contains(d)) edit(p.copy(diagnosis = (p.diagnosis + " " + d).trim()))
                                    }, { Text(d, maxLines = 1, overflow = TextOverflow.Ellipsis) })
                                }
                            }
                        }
                    }
                }

                // 2 · Complaint, how and when it happened, probable diagnosis
                item(key = "sec2") {
                    FoldCard("📝", stringResource(R.string.sec_complaint), startOpen = true) {
                        FormField(stringResource(R.string.complaint), p.complaint) { edit(p.copy(complaint = it)) }
                        FormField(stringResource(R.string.mechanism), p.mechanism) { edit(p.copy(mechanism = it)) }
                        FormField(stringResource(R.string.delay_info), p.delay) { edit(p.copy(delay = it)) }
                        FormField(stringResource(R.string.probable_dx), p.probableDiagnosis) { edit(p.copy(probableDiagnosis = it)) }
                        FormField(stringResource(R.string.description), p.description, last = true) { edit(p.copy(description = it)) }
                    }
                }

                // 3 · Medical history (closed by default). Habits and instincts sit inside it as a second dropdown.
                item(key = "sec3") {
                    val none = stringResource(R.string.none_opt)
                    val normal = stringResource(R.string.normal_opt)
                    val filled = listOf(
                        p.pmh, p.psh, p.drugs, p.allergy, p.family, p.habits,
                        p.smoking, p.alcohol, p.urination, p.defecation, p.sleep, p.eating
                    ).count { it.isNotBlank() }
                    var habitsOpen by rememberSaveable { mutableStateOf(false) }
                    FoldCard("📋", stringResource(R.string.sec_med_history), badge = if (filled > 0) filled.toString() else null) {
                        // One tap for the usual "nothing significant" history; blank fields only, typed text is never overwritten.
                        FilledTonalButton(
                            onClick = {
                                edit(
                                    p.copy(
                                        pmh = p.pmh.ifBlank { none }, psh = p.psh.ifBlank { none }, drugs = p.drugs.ifBlank { none },
                                        allergy = p.allergy.ifBlank { none }, family = p.family.ifBlank { none },
                                        smoking = p.smoking.ifBlank { none }, alcohol = p.alcohol.ifBlank { none },
                                        urination = p.urination.ifBlank { normal }, defecation = p.defecation.ifBlank { normal },
                                        sleep = p.sleep.ifBlank { normal }, eating = p.eating.ifBlank { normal }
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.hist_all_none)) }
                        HistoryField(stringResource(R.string.pmh), p.pmh, none) { edit(p.copy(pmh = it)) }
                        HistoryField(stringResource(R.string.psh), p.psh, none) { edit(p.copy(psh = it)) }
                        HistoryField(stringResource(R.string.drugs), p.drugs, none) { edit(p.copy(drugs = it)) }
                        HistoryField(stringResource(R.string.allergy), p.allergy, none) { edit(p.copy(allergy = it)) }
                        HistoryField(stringResource(R.string.family), p.family, none) { edit(p.copy(family = it)) }

                        val hFilled = listOf(p.habits, p.smoking, p.alcohol, p.urination, p.defecation, p.sleep, p.eating).count { it.isNotBlank() }
                        Row(
                            Modifier.fillMaxWidth().clickable { habitsOpen = !habitsOpen }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                (if (habitsOpen) "▾ " else "▸ ") + stringResource(R.string.sec_habits),
                                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)
                            )
                            if (hFilled > 0) Tag(hFilled.toString(), bg = MaterialTheme.colorScheme.primary)
                        }
                        if (habitsOpen) {
                            HistoryField(stringResource(R.string.h_smoking), p.smoking, none) { edit(p.copy(smoking = it)) }
                            HistoryField(stringResource(R.string.h_alcohol), p.alcohol, none) { edit(p.copy(alcohol = it)) }
                            HistoryField(stringResource(R.string.h_urination), p.urination, normal) { edit(p.copy(urination = it)) }
                            HistoryField(stringResource(R.string.h_defecation), p.defecation, normal) { edit(p.copy(defecation = it)) }
                            HistoryField(stringResource(R.string.h_sleep), p.sleep, normal) { edit(p.copy(sleep = it)) }
                            HistoryField(stringResource(R.string.h_eating), p.eating, normal) { edit(p.copy(eating = it)) }
                            FormField(stringResource(R.string.habits), p.habits, last = true) { edit(p.copy(habits = it)) }
                        }
                    }
                }

                if (initialVisit != null) {
                    // 4 · Diagnostic means
                    item(key = "sec4") {
                        FoldCard("🔬", stringResource(R.string.sec_diagnostics), badge = pendingOf(ProcGroup.DIAGNOSTIC), badgeAlert = true) {
                            ProcedureBlock(repo, initialVisit.id, ProcGroup.DIAGNOSTIC, nav)
                        }
                    }
                    // 5 · Emergency procedures and ward: the section that matters most, so it is open and tinted.
                    item(key = "sec5") {
                        FoldCard("🚑", stringResource(R.string.sec_procedures), startOpen = true, badge = pendingOf(ProcGroup.PROCEDURE), badgeAlert = true, accent = true) {
                            ProcedureBlock(repo, initialVisit.id, ProcGroup.PROCEDURE, nav, showBp = true)
                        }
                    }
                    // 6 · Requested consultations (a folding card of its own)
                    item(key = "sec6") { ConsultSection(repo, initialVisit.id, nav) }
                    // 7 · Supplies and medical needs
                    item(key = "sec7") {
                        FoldCard("🧰", stringResource(R.string.sec_supplies), badge = pendingOf(ProcGroup.SUPPLY), badgeAlert = true) {
                            ProcedureBlock(repo, initialVisit.id, ProcGroup.SUPPLY, nav)
                        }
                    }
                }

                // 8 · Visits: the first visit, then follow-ups and reviews (clinic or verbal). Blood pressure readings,
                // neuro / vascular exams and the surgical outcome are filled inside each visit.
                item(key = "sec8") {
                    val follow = visits.filter { it.kind != "INITIAL" }
                    FoldCard("📅", stringResource(R.string.sec_followups), badge = if (follow.isNotEmpty()) follow.size.toString() else null) {
                        initialVisit?.let { RenderVisit(it, false) }
                        follow.forEach { v ->
                            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.weight(3f)) { RenderVisit(v, false) }
                                RxBox(listOf(v), Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                        FilledTonalButton(onClick = { showAddVisit = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_visit))
                        }
                    }
                }
            }
        }
    }

    if (showAddVisit) {
        ChoiceDialog(
            title = stringResource(R.string.add_visit),
            options = listOf(
                "CLINIC" to stringResource(R.string.visit_type_clinic),
                "PHONE" to stringResource(R.string.visit_phone)
            ),
            customHint = stringResource(R.string.visit_name_hint),
            onPick = { key ->
                showAddVisit = false
                scope.launch {
                    val v = if (key == "CLINIC" || key == "PHONE") {
                        Visit(patientId = id, kind = key)
                    } else {
                        Visit(patientId = id, kind = "CUSTOM", title = key)
                    }
                    val vid = repo.dao.insertVisit(v)
                    nav.push(Screen.VisitDetail(vid))
                }
            },
            onDismiss = { showAddVisit = false }
        )
    }
    if (showScopePick) {
        val opts = buildList {
            add("ALL" to stringResource(R.string.share_whole))
            visits.forEach { add("V" + it.id to labels[it.id].orEmpty()) }
        }
        ChoiceDialog(
            stringResource(R.string.share_pdf), opts,
            onPick = { k -> showScopePick = false; shareScope = k },
            onDismiss = { showScopePick = false }
        )
    }
    shareScope?.let { key ->
        ShareOptionsDialog(
            initialLang = ExportPrefs.lang(ctx, id),
            canSign = ExportPrefs.doctorName(ctx).isNotBlank(),
            onShare = { o ->
                shareScope = null
                scope.launch { sharePdf(ctx, repo, id, key, o) }
            },
            onDismiss = { shareScope = null }
        )
    }
    visitToDelete?.let { v ->
        // No question: the visit disappears now and is deleted for real once the Undo bar times out.
        LaunchedEffect(v.id) {
            visitToDelete = null
            Undo.run(repo, ctx, listOf("v" + v.id)) { repo.deleteVisit(v) }
        }
    }
}

/** A history field with a one-tap ready answer ("None" / "Normal") that shows only while the field is empty. */
@Composable
private fun HistoryField(label: String, value: String, quick: String, last: Boolean = false, onChange: (String) -> Unit) {
    Column {
        FormField(label, value, Modifier.fillMaxWidth(), last) { onChange(it) }
        if (value.isBlank()) {
            AssistChip(onClick = { onChange(quick) }, label = { Text(quick) }, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String, emphasized: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text,
            style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        HorizontalDivider(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun VisitBox(
    v: Visit,
    label: String,
    day: String,
    date: String,
    thumbs: List<Media>,
    repo: Repo,
    onClick: () -> Unit,
    onLong: () -> Unit,
    extra: String = ""
) {
    Card(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLong)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("$day · $date", style = MaterialTheme.typography.bodySmall)
            }
            if (v.notes.isNotBlank()) {
                Text(v.notes, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (extra.isNotBlank()) {
                Text(extra, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (v.keywords.isNotBlank()) {
                Text(
                    v.keywords.split("|").filter { it.isNotBlank() }.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (thumbs.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    thumbs.forEach { m ->
                        AsyncImage(
                            repo.file(m.path), null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
        }
    }
}
