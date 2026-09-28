package com.desideri.familybalance.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.Checkbox
import com.desideri.familybalance.logica.Riscontro
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import kotlin.math.abs
import com.desideri.familybalance.logica.Spostamenti
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.RadioButton
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.logica.ProblemaSpostamento
import com.desideri.familybalance.logica.TipoProblema
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaData

/** Opzione del filtro per conto/valuta ([id] null: tutti). */
private data class FiltroConto(val id: Long?, val testo: String)

/**
 * Riscontro degli spostamenti (menu ⋮): per ogni spostamento la riga speculare sull'altro conto.
 * Mostra le coppie da collegare (collegabili con un tocco), gli spostamenti senza riga
 * corrispondente e i collegamenti incoerenti; toccando una riga la si modifica.
 */
@Composable
fun RiscontroScreen(vm: SpeseViewModel, onIndietro: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val problemi by vm.riscontro.collectAsStateWithLifecycle()
    var filtro by rememberSaveable { mutableStateOf<Long?>(null) }
    var espansi by remember { mutableStateOf(TipoProblema.entries.toSet()) }
    var inModifica by remember { mutableStateOf<Operazione?>(null) }
    var daScollegare by remember { mutableStateOf<Operazione?>(null) }
    // Spostamento senza riga corrispondente aperto nella schermata Risolvi.
    var daRisolvere by remember { mutableStateOf<Long?>(null) }

    val opzioniFiltro = listOf(FiltroConto(null, "Tutti i conti")) +
        dati.contiValutaOrdinati.map { FiltroConto(it.id, dati.etichetta(it.id)) }
    val filtrati = problemi?.filter { p -> filtro?.let { p.coinvolge(it) } ?: true }.orEmpty()
    val perTipo = filtrati.groupBy { it.tipo }

    Scaffold(topBar = { BarraIndietro("Riscontro spostamenti", onIndietro) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            CampoScelta(
                etichetta = "Conto",
                selezionato = opzioniFiltro.firstOrNull { it.id == filtro },
                opzioni = opzioniFiltro,
                testo = { it.testo },
                onScelta = { filtro = it.id },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            )
            if (problemi == null) {
                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            } else if (filtrati.isEmpty()) {
                Text(
                    "Nessun problema: tutti gli spostamenti hanno la riga corrispondente collegata.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (tipo in TipoProblema.entries) {
                    val lista = perTipo[tipo].orEmpty()
                    if (lista.isEmpty()) continue
                    val aperto = tipo in espansi
                    item(key = "t" + tipo.name) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { espansi = if (aperto) espansi - tipo else espansi + tipo }
                            ) {
                                Text(
                                    "${tipo.titolo} (${lista.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (tipo == TipoProblema.DA_COLLEGARE) {
                                    TextButton(onClick = { vm.collegaSpostamenti(lista.mapNotNull { p -> p.altra?.let { p.operazione to it } }) }) {
                                        Text("Collega tutte")
                                    }
                                }
                                Icon(if (aperto) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                            }
                            if (aperto) Text(tipo.spiegazione, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (aperto) {
                        items(lista, key = { "p" + tipo.name + it.operazione.id + "_" + (it.altra?.id ?: 0) }) { p ->
                            CardProblema(
                                p, dati,
                                onModifica = { inModifica = it },
                                onCollega = { vm.collegaSpostamenti(listOf(p.operazione to p.altra!!)) },
                                onScollega = { daScollegare = p.operazione },
                                onRisolvi = { daRisolvere = p.operazione.id }
                            )
                        }
                    }
                }
            }
        }
    }

    inModifica?.let { op ->
        OperazioneDialog(vm, dati, op.contoValutaId, op, onChiudi = { inModifica = null })
    }
    daRisolvere?.let { id ->
        RisolviSpostamentoDialog(vm, dati, id, onChiudi = { daRisolvere = null })
    }
    daScollegare?.let { op ->
        DialogConferma(
            titolo = "Scollega",
            testo = "Togliere il collegamento di questo spostamento? Le righe restano, ma non più collegate.",
            conferma = "Scollega",
            onConferma = {
                vm.scollegaSpostamento(op)
                daScollegare = null
            },
            onAnnulla = { daScollegare = null }
        )
    }
}

private fun importo(op: Operazione, dati: DatiApp): String =
    formattaCent(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR")

@Composable
private fun CardProblema(
    p: ProblemaSpostamento,
    dati: DatiApp,
    onModifica: (Operazione) -> Unit,
    onCollega: () -> Unit,
    onScollega: () -> Unit,
    onRisolvi: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Gli spostamenti senza riga corrispondente si aprono nella schermata Risolvi.
            val apri: (Operazione) -> Unit = if (p.tipo == TipoProblema.SENZA_CONTROPARTE) ({ _ -> onRisolvi() }) else onModifica
            RigaOperazione(p.operazione, dati, apri)
            if (p.simili.isNotEmpty()) {
                Text(
                    "Possibile doppione: ${p.simili.size} " + (if (p.simili.size == 1) "operazione" else "operazioni") +
                        " con lo stesso importo entro ${Riscontro.GIORNI_SIMILI} giorni",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            p.altra?.let { RigaOperazione(it, dati, onModifica) }
            p.dettaglio?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (p.tipo) {
                    TipoProblema.DA_COLLEGARE -> OutlinedButton(onClick = onCollega) { Text("Collega") }
                    TipoProblema.SENZA_CONTROPARTE -> OutlinedButton(onClick = onRisolvi) { Text("Risolvi") }
                    TipoProblema.COLLEGAMENTO_ERRATO -> {
                        OutlinedButton(onClick = { onModifica(p.operazione) }) { Text("Modifica") }
                        OutlinedButton(onClick = onScollega) { Text("Scollega") }
                    }
                }
            }
        }
    }
}

/** Una riga: conto, data, importo, descrizione e stato del collegamento; toccandola si modifica. */
@Composable
private fun RigaOperazione(op: Operazione, dati: DatiApp, onModifica: (Operazione) -> Unit) {
    val descrizione = when {
        op.trasferimento && op.contoValutaDestId != null ->
            "Spostamento " + (if (op.importoCent < 0) "verso " else "da ") + dati.etichetta(op.contoValutaDestId)
        op.trasferimento -> "Spostamento senza conto indicato"
        else -> op.voceId?.let { dati.vociPerId[it]?.descrizione } ?: "Senza tipo"
    }
    Column(modifier = Modifier.fillMaxWidth().clickable { onModifica(op) }.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${dati.etichetta(op.contoValutaId)} · ${formattaData(op.data)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(importo(op, dati), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            descrizione + if (op.collegataId != null) " · collegata" else "",
            style = MaterialTheme.typography.bodySmall
        )
        op.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
    }
}



