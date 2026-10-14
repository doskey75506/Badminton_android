package com.badminton.club

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DataSyncPage(state: AppState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var busyJob by remember { mutableStateOf<Job?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) {
            cancelToMainPage(state)
            return@rememberLauncherForActivityResult
        }
        busyJob = scope.launch {
            try {
                busy = "export"
                val bytes = withContext(Dispatchers.IO) { state.storage.exportBytes() }
                withContext(Dispatchers.IO) {
                    val out = ctx.contentResolver.openOutputStream(uri)
                        ?: throw IOException("cannot open the selected location")
                    out.use { it.write(bytes) }
                }
                state.flash = T("export_done", "name" to displayName(ctx, uri))
                state.page = 0
            } catch (e: CancellationException) {
            } catch (e: Exception) {
                state.warning = T("export_fail", "e" to (e.message ?: e.toString()))
            } finally {
                busy = null
                state.bump()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            cancelToMainPage(state)
            return@rememberLauncherForActivityResult
        }
        busyJob = scope.launch {
            try {
                busy = "import"
                val bytes = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("cannot read the selected file")
                }
                withContext(Dispatchers.IO) { state.storage.importBytes(bytes) }
                state.clubId = null
                state.flash = T("import_done")
                state.page = 0
            } catch (e: CancellationException) {
            } catch (e: Exception) {
                state.warning = T("import_fail", "e" to (e.message ?: e.toString()))
            } finally {
                busy = null
                state.bump()
            }
        }
    }

    if (busy != null) {
        val label = if (busy == "export") T("exporting") else T("importing")
        AlertDialog(
            onDismissRequest = {},
            title = { Text(label) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(T("sync_running_hint"))
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    busyJob?.cancel()
                    busy = null
                    cancelToMainPage(state)
                }) {
                    Text(T("cancel"))
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                T("p_sync"),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        item {
            Text(T("sync_intro"), color = MaterialTheme.colorScheme.outline)
        }
        item {
            SyncSection(
                title = T("export_confirm_title"),
                lines = listOf(T("sync_export_desc")),
                buttonLabel = T("export"),
                onClick = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                    exportLauncher.launch("badminton-backup-$stamp.zip")
                },
            )
        }
        item {
            SyncSection(
                title = T("import_confirm_title"),
                lines = listOf(T("sync_import_desc")),
                buttonLabel = T("import"),
                onClick = { importLauncher.launch(arrayOf("*/*")) },
            )
        }
    }
}

@Composable
private fun SyncSection(title: String, lines: List<String>, buttonLabel: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp),
        )
        lines.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedButton(onClick = onClick) {
            Text(buttonLabel)
        }
    }
}

private fun cancelToMainPage(state: AppState) {
    state.flash = T("op_cancelled")
    state.page = 0
    state.bump()
}

private fun displayName(ctx: Context, uri: Uri): String =
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) {
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i >= 0) c.getString(i) else null
        } else null
    } ?: uri.lastPathSegment ?: uri.toString()
