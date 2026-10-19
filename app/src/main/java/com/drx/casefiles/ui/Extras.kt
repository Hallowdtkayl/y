@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.drx.casefiles.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.drx.casefiles.R
import com.drx.casefiles.data.PdfQuality
import com.drx.casefiles.data.PdfOptions
import com.drx.casefiles.data.ExportPrefs
import com.drx.casefiles.data.Lang
import com.drx.casefiles.data.RefRange
import com.drx.casefiles.data.Repo
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun VideoPlayer(file: File, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val player = remember(file) {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { c -> PlayerView(c).apply { this.player = player } }, modifier = modifier)
}

@Composable
private fun RefDialog(initial: RefRange, onSave: (RefRange) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var unit by remember { mutableStateOf(initial.unit) }
    var low by remember { mutableStateOf(initial.low.toString()) }
    var high by remember { mutableStateOf(initial.high.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.test_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.test_name)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(unit, { unit = it }, singleLine = true, label = { Text(stringResource(R.string.ref_unit)) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(low, { low = it }, singleLine = true, label = { Text(stringResource(R.string.ref_low)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(high, { high = it }, singleLine = true, label = { Text(stringResource(R.string.ref_high)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val l = low.replace(',', '.').toDoubleOrNull()
                val h = high.replace(',', '.').toDoubleOrNull()
                if (name.isNotBlank() && l != null && h != null) onSave(initial.copy(name = name.trim(), unit = unit.trim(), low = l, high = h))
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}

/** Editable table of normal laboratory values. */
@Composable
fun RefScreen(repo: Repo, nav: Nav) {
    val refs by remember { repo.dao.refRanges() }.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf<RefRange?>(null) }
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ref_title)) },
                navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = { IconButton(onClick = { adding = true }) { Icon(Icons.Default.Add, null) } }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
            item { Text(stringResource(R.string.ref_note), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
            items(refs, key = { it.id }) { r ->
                Row(Modifier.fillMaxWidth().clickable { edit = r }.padding(vertical = 10.dp)) {
                    Text(r.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text("${r.low} – ${r.high} ${r.unit}")
                }
                HorizontalDivider()
            }
        }
    }
    edit?.let { r ->
        RefDialog(
            r,
            onSave = { n -> edit = null; scope.launch { repo.dao.updateRef(n) } },
            onDelete = { edit = null; scope.launch { repo.dao.deleteRef(r) } },
            onDismiss = { edit = null }
        )
    }
    if (adding) {
        RefDialog(
            RefRange(name = "", low = 0.0, high = 0.0),
            onSave = { n -> adding = false; scope.launch { repo.dao.insertRef(n) } },
            onDelete = null,
            onDismiss = { adding = false }
        )
    }
}

/** Pick or type a test name (suggestions from the reference table), then enter its value. */
@Composable
fun LabValueDialog(refs: List<RefRange>, onConfirm: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    val sugg = if (name.isNotBlank()) {
        refs.filter { it.name.contains(name, true) && !it.name.equals(name, true) }.take(6)
    } else {
        emptyList()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_lab_value)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.test_name)) }, modifier = Modifier.fillMaxWidth())
                if (sugg.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sugg.forEach { r -> FilterChip(false, { name = r.name; unit = r.unit }, { Text(r.name) }) }
                    }
                }
                OutlinedTextField(value, { value = it }, singleLine = true, label = { Text(stringResource(R.string.value_hint)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(unit, { unit = it }, singleLine = true, label = { Text(stringResource(R.string.ref_unit)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank() && value.isNotBlank()) onConfirm(name.trim(), value.trim(), unit.trim()) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
fun SettingsDialog(
    pinSet: Boolean,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onPinSet: () -> Unit,
    onPinClear: () -> Unit,
    onRefRanges: () -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    var docName by remember { mutableStateOf(ExportPrefs.doctorName(ctx)) }
    var docTitle by remember { mutableStateOf(ExportPrefs.doctorTitle(ctx)) }
    var quality by remember { mutableStateOf(ExportPrefs.quality(ctx)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SettingsGroup(stringResource(R.string.set_g_language)) {
                    Row(Modifier.fillMaxWidth().clickable { Lang.set(ctx, true) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(Lang.ar, { Lang.set(ctx, true) })
                        Text("العربية")
                    }
                    Row(Modifier.fillMaxWidth().clickable { Lang.set(ctx, false) }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(!Lang.ar, { Lang.set(ctx, false) })
                        Text("English")
                    }
                }
                SettingsGroup(stringResource(R.string.set_g_data)) {
                    TextButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup)) }
                    TextButton(onClick = onRestore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.restore)) }
                    TextButton(onClick = onRefRanges, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.set_ref_ranges)) }
                }
                SettingsGroup(stringResource(R.string.set_g_security)) {
                    if (pinSet) {
                        TextButton(onClick = onPinClear, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pin_clear)) }
                    } else {
                        TextButton(onClick = onPinSet, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pin_set)) }
                    }
                }
                // Report signature: printed in the header, the footer and at the end of every exported PDF.
                SettingsGroup(stringResource(R.string.set_g_reports)) {
                    OutlinedTextField(
                        docName, { docName = it; ExportPrefs.setDoctor(ctx, it, docTitle) },
                        singleLine = true, label = { Text(stringResource(R.string.set_doctor_name)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                    OutlinedTextField(
                        docTitle, { docTitle = it; ExportPrefs.setDoctor(ctx, docName, it) },
                        singleLine = true, label = { Text(stringResource(R.string.set_doctor_title)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                    Text(stringResource(R.string.set_img_quality), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                    PdfQuality.values().forEach { q ->
                        Row(
                            Modifier.fillMaxWidth().clickable { quality = q; ExportPrefs.setQuality(ctx, q) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(quality == q, { quality = q; ExportPrefs.setQuality(ctx, q) })
                            Text(stringResource(q.label))
                        }
                    }
                    Text(stringResource(R.string.q_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun LockScreen(onTry: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            pin, { pin = it; wrong = false }, singleLine = true,
            label = { Text(stringResource(R.string.pin_hint)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        )
        if (wrong) Text(stringResource(R.string.wrong_pin), color = MaterialTheme.colorScheme.error)
        Button(onClick = { if (!onTry(pin)) wrong = true }, modifier = Modifier.padding(top = 12.dp)) {
            Text(stringResource(R.string.unlock))
        }
    }
}

@Composable
fun ShareOptionsDialog(
    initialLang: String,
    canSign: Boolean,
    onShare: (PdfOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var lang by remember { mutableStateOf(if (initialLang == "AR") "AR" else "EN") }
    var hide by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf(true) }
    var sign by remember { mutableStateOf(canSign) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_pdf)) },
        text = {
            Column {
                Text(stringResource(R.string.pdf_language), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    FilterChip(lang == "AR", { lang = "AR" }, { Text(stringResource(R.string.lang_ar)) })
                    FilterChip(lang == "EN", { lang = "EN" }, { Text(stringResource(R.string.lang_en)) })
                }
                Text(stringResource(R.string.pdf_lang_hint), style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(images, { images = it })
                    Text(stringResource(R.string.include_images))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(hide, { hide = it })
                    Text(stringResource(R.string.hide_identity))
                }
                if (canSign) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(sign, { sign = it })
                        Text(stringResource(R.string.pdf_sign_check))
                    }
                } else {
                    Text(stringResource(R.string.pdf_sign_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onShare(PdfOptions(hide, images, lang, sign && canSign)) }) { Text(stringResource(R.string.share_pdf)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
