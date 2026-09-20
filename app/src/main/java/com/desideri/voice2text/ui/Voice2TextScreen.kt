package com.desideri.voice2text.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.core.content.ContextCompat
import com.desideri.voice2text.gemini.StileRiscrittura
import kotlinx.coroutines.delay

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

    val recordPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concesso ->
        if (concesso) viewModel.avviaRegistrazione() else viewModel.segnalaPermessoMicrofonoNegato()
    }

    fun avviaRegistrazioneRichiedendoPermesso() {
        val giaConcesso = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (giaConcesso) viewModel.avviaRegistrazione() else recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    val savePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concesso ->
        if (concesso) viewModel.salvaAudio() else viewModel.segnalaPermessoSalvataggioNegato()
    }

    // Da Android 10 (Q) in poi il salvataggio passa da MediaStore: nessun permesso richiesto.
    fun salvaAudioRichiedendoPermesso() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            viewModel.salvaAudio()
            return
        }
        val giaConcesso = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        if (giaConcesso) viewModel.salvaAudio() else savePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    // Timer mostrato durante la registrazione: puramente cosmetico, vive solo in UI (lo stato
    // vero, avviato/fermato, resta nel ViewModel/MediaRecorder).
    var secondiRegistrazione by remember { mutableStateOf(0) }
    LaunchedEffect(uiState.isRecording) {
        if (uiState.isRecording) {
            secondiRegistrazione = 0
            while (true) {
                delay(1000)
                secondiRegistrazione++
            }
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
            if (uiState.isRecording) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.FiberManualRecord,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Registrazione in corso... ${formattaTempo(secondiRegistrazione)}")
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.fermaRegistrazioneETrascrivi() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ferma e trascrivi")
                    }
                    OutlinedButton(
                        onClick = { viewModel.annullaRegistrazione() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Annulla")
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { filePicker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Scegli file audio")
                    }
                    Button(
                        onClick = { avviaRegistrazioneRichiedendoPermesso() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Registra audio")
                    }
                }
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
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { salvaAudioRichiedendoPermesso() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Salva audio localmente")
                }
                uiState.messaggioSalvataggio?.let { messaggio ->
                    Spacer(Modifier.height(8.dp))
                    Text(messaggio, style = MaterialTheme.typography.bodySmall)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Riassunto:", style = MaterialTheme.typography.titleMedium)
                    PulsanteAscolto(
                        inLettura = uiState.testoInLettura == TestoInLettura.RIASSUNTO,
                        contentDescription = "Ascolta riassunto",
                        onClick = { viewModel.leggiORiassunto() }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(risultato.riassunto)

                Spacer(Modifier.height(24.dp))
                Text("Stime dalla voce (indicative, non certe):", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                RigaStima("Genere", risultato.genere)
                RigaStima("Eta'", risultato.eta)
                RigaStimaConMotivazione("Umore", risultato.umore, risultato.umoreMotivazione)
                RigaStimaConMotivazione("Tono di voce", risultato.tonoVoce, risultato.tonoVoceMotivazione)

                Spacer(Modifier.height(24.dp))
                Text("Riscrivi con uno stile diverso:", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StileRiscrittura.entries.forEach { stile ->
                        OutlinedButton(
                            onClick = { viewModel.riscrivi(stile) },
                            enabled = !uiState.isRiscrivendo,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stile.etichetta)
                        }
                    }
                }

                if (uiState.isRiscrivendo) {
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator()
                }

                uiState.testoRiscritto?.let { testoRiscritto ->
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Versione ${uiState.stileRiscritto?.etichetta.orEmpty()}:",
                            style = MaterialTheme.typography.titleSmall
                        )
                        PulsanteAscolto(
                            inLettura = uiState.testoInLettura == TestoInLettura.RISCRITTO,
                            contentDescription = "Ascolta versione riscritta",
                            onClick = { viewModel.leggiOTestoRiscritto() }
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Text(testoRiscritto)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { clipboard.setText(AnnotatedString(testoRiscritto)) }) {
                        Text("Copia negli appunti")
                    }
                }

                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { viewModel.puliciRisultati() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pulisci risultati")
                }
            }
        }
    }

    if (mostraVersioni) {
        VersioniDialog(onDismiss = { mostraVersioni = false })
    }
}

/** Pulsante a icona che alterna play/stop per la lettura ad alta voce di un testo. */
@Composable
private fun PulsanteAscolto(inLettura: Boolean, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        if (inLettura) {
            Icon(Icons.Filled.Stop, contentDescription = "Ferma lettura")
        } else {
            Icon(Icons.Filled.PlayArrow, contentDescription = contentDescription)
        }
    }
}

private fun formattaTempo(secondi: Int): String {
    val minuti = secondi / 60
    val resto = secondi % 60
    return "%02d:%02d".format(minuti, resto)
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

@Composable
private fun RigaStimaConMotivazione(etichetta: String, valore: String, motivazione: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(etichetta, style = MaterialTheme.typography.bodyLarge)
            Text(valore, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            motivazione,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
