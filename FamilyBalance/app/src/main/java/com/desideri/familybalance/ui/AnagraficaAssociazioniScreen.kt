package com.desideri.familybalance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Associazione

/**
 * Anagrafica associazioni per l'import degli estratti conto: chiave (testo cercato nella
 * descrizione del movimento) -> tipo/sottotipo. "Incolla elenco" aggiunge più associazioni in un
 * colpo, una per riga nel formato "Chiave (Tipo)" o "Chiave (Tipo / Sottotipo)".
 */
@Composable
fun AnagraficaAssociazioniScreen(vm: SpeseViewModel, onIndietro: () -> Unit) {
    val associazioni by vm.associazioni.collectAsStateWithLifecycle()
    val dati by vm.dati.collectAsStateWithLifecycle()
    var filtro by rememberSaveable { mutableStateOf("") }
    var inModifica by remember { mutableStateOf<Associazione?>(null) }
    var incolla by remember { mutableStateOf(false) }

    val tipi = remember(dati.voci) { (dati.voci.map { it.tipo } + Associazione.TIPO_SPOSTAMENTO).distinct().sortedBy { it.lowercase() } }
    val filtrate = remember(associazioni, filtro) {
        val f = filtro.trim()
        if (f.isEmpty()) associazioni else associazioni.filter {
            it.chiave.contains(f, true) || it.tipo.contains(f, true) || (it.sottotipo?.contains(f, true) == true)
        }
    }

    Scaffold(
        topBar = {
            BarraIndietro("Anagrafica associazioni", onIndietro) {
                IconButton(onClick = { incolla = true }) { Icon(Icons.Filled.ContentPaste, contentDescription = "Incolla elenco") }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { inModifica = Associazione(chiave = "", tipo = "") }) {
                Icon(Icons.Filled.Add, contentDescription = "Nuova associazione")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = filtro,
                onValueChange = { filtro = it },
                label = { Text("Cerca chiave o tipo") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Text("${filtrate.size} associazioni", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
            if (associazioni.isEmpty()) {
                Text(
                    "Nessuna associazione. Aggiungile con + oppure incolla un elenco (icona in alto), una per riga: \"Chiave (Tipo)\".",
                    modifier = Modifier.padding(16.dp)
                )
            }
            LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 88.dp)) {
                items(filtrate, key = { it.id }) { a ->
                    Column(modifier = Modifier.fillMaxWidth().clickable { inModifica = a }.padding(vertical = 8.dp, horizontal = 4.dp)) {
                        Text(a.chiave, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text("→ ${a.destinazione}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    inModifica?.let { a ->
        AssociazioneDialog(
            associazione = a,
            tipi = tipi,
            sottotipiDi = { tipo -> dati.voci.filter { it.tipo.equals(tipo.trim(), true) }.mapNotNull { it.sottotipo }.distinct().sortedBy { it.lowercase() } },
            onSalva = {
                vm.salvaAssociazione(it)
                inModifica = null
            },
            onElimina = {
                vm.eliminaAssociazione(a)
                inModifica = null
            },
            onChiudi = { inModifica = null }
        )
    }

    if (incolla) {
        IncollaElencoDialog(
            onImporta = {
                vm.importaElencoAssociazioni(it)
                incolla = false
            },
            onAnnulla = { incolla = false }
        )
    }
}

@Composable
private fun AssociazioneDialog(
    associazione: Associazione,
    tipi: List<String>,
    sottotipiDi: (String) -> List<String>,
    onSalva: (Associazione) -> Unit,
    onElimina: () -> Unit,
    onChiudi: () -> Unit
) {
    var chiave by remember { mutableStateOf(associazione.chiave) }
    var tipo by remember { mutableStateOf(associazione.tipo) }
    var sottotipo by remember { mutableStateOf(associazione.sottotipo ?: "") }
    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(if (associazione.id == 0L) "Nuova associazione" else "Modifica associazione") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = chiave, onValueChange = { chiave = it }, label = { Text("Chiave (testo nella descrizione)") }, modifier = Modifier.fillMaxWidth())
                CampoAutocompletamento("Tipo", tipo, tipi, { tipo = it; sottotipo = "" })
                if (!tipo.trim().equals(Associazione.TIPO_SPOSTAMENTO, true)) {
                    CampoAutocompletamento("Sottotipo (opzionale)", sottotipo, sottotipiDi(tipo), { sottotipo = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSalva(associazione.copy(chiave = chiave, tipo = tipo, sottotipo = sottotipo.ifBlank { null })) },
                enabled = chiave.isNotBlank() && tipo.isNotBlank()
            ) { Text("Salva") }
        },
        dismissButton = {
            Row {
                if (associazione.id != 0L) {
                    TextButton(onClick = onElimina) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onChiudi) { Text("Annulla") }
            }
        }
    )
}

@Composable
private fun IncollaElencoDialog(onImporta: (String) -> Unit, onAnnulla: () -> Unit) {
    var testo by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onAnnulla,
        title = { Text("Incolla elenco associazioni") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Una associazione per riga: \"Chiave (Tipo)\" oppure \"Chiave (Tipo / Sottotipo)\". I doppioni vengono saltati.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = testo,
                    onValueChange = { testo = it },
                    label = { Text("Elenco") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onImporta(testo) }, enabled = testo.isNotBlank()) { Text("Aggiungi") } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla") } }
    )
}
