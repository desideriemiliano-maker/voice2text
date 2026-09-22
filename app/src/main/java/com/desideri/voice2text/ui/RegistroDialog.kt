package com.desideri.voice2text.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.desideri.voice2text.gemini.RegistroPromptStore
import com.desideri.voice2text.gemini.VoceRegistro
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FORMATO_DATA_ORA = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALIAN)

/**
 * Dialog "Registro": elenco dei prompt inviati a Gemini (trascrizione e riscrittura) e dei
 * risultati ottenuti, conservati solo per 7 giorni (vedi RegistroPromptStore). Ogni voce e'
 * chiusa di default e si espande al tocco per non affollare la lista con testi lunghi.
 */
@Composable
fun RegistroDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { RegistroPromptStore(context) }
    var voci by remember { mutableStateOf(store.leggi()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp).heightIn(max = 480.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Registro", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Chiudi")
                    }
                }
                Text(
                    "Prompt inviati a Gemini e risultati ottenuti, conservati per 7 giorni.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                if (voci.isEmpty()) {
                    Text("Nessuna chiamata registrata.", style = MaterialTheme.typography.bodySmall)
                } else {
                    var voceEspansa by remember { mutableStateOf<Long?>(null) }
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(voci, key = { it.timestampMillis }) { voce ->
                            VoceRegistroItem(
                                voce = voce,
                                espansa = voce.timestampMillis == voceEspansa,
                                onClick = {
                                    voceEspansa = if (voce.timestampMillis == voceEspansa) null else voce.timestampMillis
                                }
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) {
                    if (voci.isNotEmpty()) {
                        TextButton(onClick = {
                            store.svuota()
                            voci = emptyList()
                        }) { Text("Svuota") }
                    }
                    TextButton(onClick = onDismiss) { Text("Chiudi") }
                }
            }
        }
    }
}

@Composable
private fun VoceRegistroItem(voce: VoceRegistro, espansa: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
                    Text("Prompt:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(voce.prompt, style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (voce.errore) "Errore:" else "Risultato:",
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
