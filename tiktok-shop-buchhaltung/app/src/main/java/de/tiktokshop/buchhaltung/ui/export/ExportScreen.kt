package de.tiktokshop.buchhaltung.ui.export

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.tiktokshop.buchhaltung.ui.common.rememberApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen() {
    val app = rememberApp()
    val context = LocalContext.current
    val viewModel: ExportViewModel = viewModel(
        factory = viewModelFactory { initializer { ExportViewModel(app.repository, app.exportManager) } },
    )
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.lastExportUri) {
        val uri = state.lastExportUri ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export teilen"))
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Export") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Zeitraum: ${state.from} – ${state.to}")

            Button(onClick = viewModel::exportCsv, modifier = Modifier.fillMaxWidth()) {
                Text("CSV exportieren")
            }
            Button(onClick = viewModel::exportTikTokCsv, modifier = Modifier.fillMaxWidth()) {
                Text("TikTok-CSV exportieren")
            }
            Button(onClick = viewModel::exportZip, modifier = Modifier.fillMaxWidth()) {
                Text("ZIP mit Belegen exportieren")
            }
        }
    }
}
