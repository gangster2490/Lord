package de.tiktokshop.buchhaltung.ui.scan

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import de.tiktokshop.buchhaltung.ui.common.rememberApp

/**
 * Einstiegspunkt für den Mehrfach-Transaktions-Scan: Nutzer wählt einen Screenshot/Beleg,
 * die App erkennt automatisch alle enthaltenen Einnahmen/Ausgaben und navigiert danach zum
 * Review-Screen. Es wird NIE automatisch gespeichert.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(viewModel: ScanViewModel, onRecognized: () -> Unit, onCancel: () -> Unit) {
    val app = rememberApp()
    val state by viewModel.state.collectAsState()

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraUri?.let(viewModel::onImageSelected)
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::onImageSelected) }

    LaunchedEffect(state.hasResult) {
        if (state.hasResult) onRecognized()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Beleg / Screenshot scannen") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Lade einen TikTok-Shop- oder Google-Play-Screenshot hoch. Die App erkennt " +
                    "automatisch alle enthaltenen Transaktionen und zeigt sie zur Bestätigung an.",
            )

            state.imageUri?.let { uri ->
                AsyncImage(model = uri, contentDescription = "Scan-Vorschau", modifier = Modifier.height(220.dp))
            }

            if (state.isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator()
                    Text("Erkenne Transaktionen…")
                }
            }

            if (!state.isLoading && state.warning != null) {
                Text(state.warning.orEmpty())
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val uri = app.receiptStorage.createCameraCaptureUri()
                    pendingCameraUri = uri
                    cameraLauncher.launch(uri)
                }) { Text("Kamera") }

                Button(onClick = {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Galerie / Screenshot") }
            }

            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Abbrechen")
            }
        }
    }
}
