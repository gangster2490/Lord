package de.tiktokshop.buchhaltung.ui.expense

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import de.tiktokshop.buchhaltung.data.model.ExpenseCategory
import de.tiktokshop.buchhaltung.ui.common.rememberApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseCaptureScreen(onSaved: () -> Unit, onCancel: () -> Unit) {
    val app = rememberApp()
    val viewModel: ExpenseCaptureViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ExpenseCaptureViewModel(app.repository, app.receiptStorage, app.textRecognizer) }
        },
    )
    val state by viewModel.state.collectAsState()

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pendingCameraUri?.let(viewModel::onImageSelected)
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> uri?.let(viewModel::onImageSelected) }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Ausgabe erfassen") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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

            state.imageUri?.let { uri ->
                AsyncImage(model = uri, contentDescription = "Beleg-Vorschau", modifier = Modifier.height(200.dp))
            }

            if (state.isRecognizing) {
                CircularProgressIndicator()
                Text("OCR läuft…")
            }

            if (state.needsReview) {
                Text("Bitte prüfen: Werte konnten nicht sicher erkannt werden.")
            }

            OutlinedTextField(
                value = state.date.toString(),
                onValueChange = {},
                readOnly = true,
                label = { Text("Belegdatum") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.merchant,
                onValueChange = viewModel::updateMerchant,
                label = { Text("Händler") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.grossAmountText,
                onValueChange = viewModel::updateAmountText,
                label = { Text("Gesamtbetrag (€)") },
                modifier = Modifier.fillMaxWidth(),
            )

            CategoryDropdown(selected = state.category, onSelect = viewModel::updateCategory)

            Column {
                Text("Geschäftlicher Anteil: ${state.businessUsePercent}%")
                Slider(
                    value = state.businessUsePercent.toFloat(),
                    onValueChange = { viewModel.updateBusinessUsePercent(it.toInt()) },
                    valueRange = 0f..100f,
                    steps = 19,
                )
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::updateNote,
                label = { Text("Notiz") },
                modifier = Modifier.fillMaxWidth(),
            )

            state.duplicateWarning?.let { warning -> Text(warning) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::confirmAndSave) { Text("Speichern") }
                Button(onClick = onCancel) { Text("Abbrechen") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(selected: ExpenseCategory, onSelect: (ExpenseCategory) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Kategorie") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ExpenseCategory.entries.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.label) },
                    onClick = {
                        onSelect(category)
                        expanded = false
                    },
                )
            }
        }
    }
}
