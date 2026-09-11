package com.desideri.voice2text.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Voice2TextScreen(viewModel: TranscriptionViewModel, sharedIntent: Intent?) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val uiState = viewModel.uiState
    var menuEspanso by remember { mutableStateOf(false) }
    var mostraVersioni by remember { mutableStateOf(false) }

    // Un audio condiviso da un'altra app (es. WhatsApp -> Condividi) arriva come ACTION_SEND
    // con l'Uri in EXTRA_STREAM. Si riattiva a ogni nuovo intent (vedi onNewIntent in MainActivity).
    LaunchedEffect(sharedIntent) {
        val intent = sharedIntent ?: return@LaunchedEffect
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true) {
            val uri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (uri != null) {
                viewModel.selezionaAudio(uri)
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.selezionaAudio(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice2Text") },
                actions = {
                    IconButton(onClick = { menuEspanso = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Altre opzioni")
                    }
                    DropdownMenu(expanded = menuEspanso, onDismissRequest = { menuEspanso = false }) {
                        DropdownMenuItem(
                            text = { Text("Versioni") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = { menuEspanso = false; mostraVersioni = true }
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = { filePicker.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scegli file audio")
            }

            Spacer(Modifier.height(16.dp))

            if (uiState.fileName != null) {
                Text("File selezionato: ${uiState.fileName}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.trascrivi() },
                        enabled = !uiState.isTranscribing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Trascrivi")
                    }
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        enabled = !uiState.isTranscribing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Annulla")
                    }
                }
            }

            if (uiState.isTranscribing) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Trascrizione in corso...")
            }

            uiState.error?.let { errore ->
                Spacer(Modifier.height(16.dp))
                Text(errore, color = MaterialTheme.colorScheme.error)
            }

            uiState.result?.let { risultato ->
                Spacer(Modifier.height(24.dp))
                Text("Trascrizione:", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(risultato.testo)
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { clipboard.setText(AnnotatedString(risultato.testo)) }) {
                    Text("Copia negli appunti")
                }

                Spacer(Modifier.height(24.dp))
                Text("Stime dalla voce (indicative, non certe):", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                RigaStima("Genere", risultato.genere)
                RigaStima("Eta'", risultato.eta)
                RigaStima("Umore", risultato.umore)
                RigaStima("Tono di voce", risultato.tonoVoce)
            }
        }
    }

    if (mostraVersioni) {
        VersioniDialog(onDismiss = { mostraVersioni = false })
    }
}

@Composable
private fun RigaStima(etichetta: String, valore: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(etichetta, style = MaterialTheme.typography.bodyLarge)
        Text(valore, style = MaterialTheme.typography.bodyLarge)
    }
    Spacer(Modifier.height(4.dp))
}
