package de.tiktokshop.buchhaltung.ui.backup

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.tiktokshop.buchhaltung.ui.common.rememberApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen() {
    val app = rememberApp()
    val context = LocalContext.current
    val viewModel: BackupViewModel = viewModel(
        factory = viewModelFactory { initializer { BackupViewModel(app.backupManager) } },
    )
    val state by viewModel.state.collectAsState()

    val openDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::selectBackupFileForRestore)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Backup") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = viewModel::createBackup, modifier = Modifier.fillMaxWidth()) {
                Text("Backup erstellen")
            }
            state.lastBackupFile?.let { file ->
                Text("Backup gespeichert: ${file.name}")
                Button(
                    onClick = {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Backup teilen"))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Backup teilen") }
            }

            Button(
                onClick = { openDocumentLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Backup wiederherstellen…")
            }

            if (state.restoreDone) {
                Text("Wiederherstellung abgeschlossen.")
            }
            state.errorMessage?.let { message -> Text("Fehler: $message") }
        }
    }

    val preview = state.restorePreview
    if (preview != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Backup wiederherstellen?") },
            text = {
                Text(
                    "Enthält ${preview.incomeCount} Einnahmen und ${preview.expenseCount} Ausgaben " +
                        "vom ${preview.createdAt}. Bestehende Einträge mit gleicher ID werden ersetzt.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text("Wiederherstellen") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("Abbrechen") } },
        )
    }
}
