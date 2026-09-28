package com.desideri.familybalance.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxHeight
import com.desideri.familybalance.estratto.RegistroPromptStore
import com.desideri.familybalance.estratto.VoceRegistro
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FORMATO_DATA_ORA = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN)
private val FORMATO_NOME_FILE = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ITALIAN)

/** Testo del file salvato: per ogni voce tipo, data, richiesta e risposta (o errore). */
private fun testoRegistro(voci: List<VoceRegistro>): String = voci.joinToString("\n\n") { v ->
    buildString {
        appendLine("=".repeat(60))
        appendLine("${v.tipo.etichetta} · ${FORMATO_DATA_ORA.format(Date(v.timestampMillis))}" + if (v.errore) " · ERRORE" else "")
        appendLine("=".repeat(60))
        appendLine("Richiesta:")
        appendLine(v.prompt)
        appendLine()
        appendLine(if (v.errore) "Errore:" else "Risposta:")
        append(v.risultato)
    }
}

/**
 * Dialog "Registro Gemini" (menu ⋮): richieste inviate a Gemini per gli estratti conto e risposte
 * ottenute, conservate per 30 giorni (vedi RegistroPromptStore). Ogni voce e' chiusa di default e
 * si espande al tocco; con le caselle se ne selezionano una o più da salvare in un file di testo. Porting del RegistroDialog di Voice2Text, a tutta larghezza perche' le
 * risposte (JSON dei movimenti) sono lunghe.
 */
@Composable
fun RegistroDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { RegistroPromptStore(context) }
    var voci by remember { mutableStateOf(store.leggi()) }
    val selezionate = remember { mutableStateListOf<Long>() }
    // Salvataggio delle voci selezionate in un file di testo scelto dall'utente.
    val salvataggio = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val daSalvare = voci.filter { it.timestampMillis in selezionate }
        val esito = runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(testoRegistro(daSalvare)) }
                ?: error("file non scrivibile")
        }
        Toast.makeText(
            context,
            esito.fold({ "Salvate ${daSalvare.size} voci del registro" }, { "Salvataggio non riuscito: ${it.message}" }),
            Toast.LENGTH_SHORT
        ).show()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Registro Gemini", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Chiudi")
                    }
                }
                Text(
                    "Richieste inviate a Gemini e risposte ottenute, conservate per 30 giorni.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                if (voci.isEmpty()) {
                    Text("Nessuna chiamata registrata.", style = MaterialTheme.typography.bodySmall)
                } else {
                    var voceEspansa by remember { mutableStateOf<Long?>(null) }
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(voci, key = { it.timestampMillis }) { voce ->
                            VoceRegistroItem(
                                voce = voce,
                                espansa = voce.timestampMillis == voceEspansa,
                                selezionata = voce.timestampMillis in selezionate,
                                onSeleziona = { sel ->
                                    if (sel) selezionate.add(voce.timestampMillis) else selezionate.remove(voce.timestampMillis)
                                },
                                onClick = {
                                    voceEspansa = if (voce.timestampMillis == voceEspansa) null else voce.timestampMillis
                                }
                            )
                        }
                    }
                }

                if (voci.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            if (selezionate.size == voci.size) selezionate.clear()
                            else {
                                selezionate.clear()
                                selezionate.addAll(voci.map { it.timestampMillis })
                            }
                        }) { Text(if (selezionate.size == voci.size) "Deseleziona tutte" else "Seleziona tutte") }
                        TextButton(
                            onClick = { salvataggio.launch("registro_gemini_" + FORMATO_NOME_FILE.format(Date()) + ".txt") },
                            enabled = selezionate.isNotEmpty()
                        ) { Text("Salva (${selezionate.size})") }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (voci.isNotEmpty()) {
                        TextButton(onClick = {
                            store.svuota()
                            voci = emptyList()
                            selezionate.clear()
                        }) { Text("Svuota") }
                    }
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }
            }
        }
    }
}

@Composable
private fun VoceRegistroItem(
    voce: VoceRegistro,
    espansa: Boolean,
    selezionata: Boolean,
    onSeleziona: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = selezionata, onCheckedChange = onSeleziona)
            if (voce.errore) {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = "Errore",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(
                voce.tipo.etichetta,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                FORMATO_DATA_ORA.format(Date(voce.timestampMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(if (espansa) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        }

        if (espansa) {
            SelectionContainer {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text("Richiesta:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(voce.prompt, style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (voce.errore) "Errore:" else "Risposta:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(voce.risultato, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
