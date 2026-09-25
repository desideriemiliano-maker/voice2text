package com.desideri.familybalance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaData
import com.desideri.familybalance.logica.testoInCent
import kotlin.math.abs

/** Filtri della lista operazioni: periodo, intervallo di importo (valore assoluto), tipo e sottotipo. */
private data class Filtri(
    val da: Long? = null,
    val a: Long? = null,
    val importoMin: String = "",
    val importoMax: String = "",
    val tipo: String = "",
    val sottotipo: String = ""
) {
    val attivi: Boolean get() = this != Filtri()
}

private data class RigaOperazione(val operazione: Operazione, val saldoDopo: Long)

@Composable
fun OperazioniScreen(vm: SpeseViewModel, contoValutaId: Long, onIndietro: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val cv = dati.contiValutaPerId[contoValutaId]
    var filtri by remember { mutableStateOf(Filtri()) }
    var mostraFiltri by rememberSaveable { mutableStateOf(false) }
    var inModifica by remember { mutableStateOf<Operazione?>(null) }
    var nuova by remember { mutableStateOf(false) }

    if (cv == null) {
        Scaffold(topBar = { BarraIndietro("Conto", onIndietro) }) { padding ->
            Text("Conto non più presente.", modifier = Modifier.padding(padding).padding(16.dp))
        }
        return
    }

    // Saldo progressivo calcolato su tutte le operazioni del conto, in ordine di data.
    val righe = remember(dati.operazioni, cv) {
        var saldo = cv.saldoInizialeCent
        dati.operazioni.filter { it.contoValutaId == contoValutaId }
            .sortedWith(compareBy({ it.data }, { it.id }))
            .map { op ->
                saldo += op.importoCent
                RigaOperazione(op, saldo)
            }
            .asReversed()
    }
    val saldoAttuale = righe.firstOrNull()?.saldoDopo ?: cv.saldoInizialeCent
    val filtrate = remember(righe, filtri, dati.voci) { righe.filter { corrisponde(it.operazione, filtri, dati) } }

    Scaffold(
        topBar = {
            BarraIndietro(dati.etichetta(contoValutaId), onIndietro) {
                IconButton(onClick = { mostraFiltri = !mostraFiltri }) {
                    Icon(if (mostraFiltri) Icons.Filled.FilterListOff else Icons.Filled.FilterList, contentDescription = "Filtri")
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nuova = true }) { Icon(Icons.Filled.Add, contentDescription = "Nuova operazione") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 88.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Saldo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TestoImporto(saldoAttuale / 100.0, cv.valuta, grassetto = true)
                        }
                        Text("Saldo iniziale ${formattaCent(cv.saldoInizialeCent, cv.valuta)}", style = MaterialTheme.typography.bodySmall)
                        if (filtri.attivi) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                Text("Totale filtrato (${filtrate.size} operazioni)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                TestoImporto(filtrate.sumOf { it.operazione.importoCent } / 100.0, cv.valuta, grassetto = true)
                            }
                        }
                    }
                }
            }
            if (mostraFiltri) {
                item { PannelloFiltri(filtri, dati, onFiltri = { filtri = it }) }
            }
            if (filtrate.isEmpty()) {
                item { Text("Nessuna operazione.", modifier = Modifier.padding(16.dp)) }
            }
            items(filtrate, key = { it.operazione.id }) { riga ->
                RigaListaOperazione(riga, dati, cv.valuta, onClick = { inModifica = riga.operazione })
                HorizontalDivider()
            }
        }
    }

    if (nuova || inModifica != null) {
        OperazioneDialog(
            vm = vm,
            dati = dati,
            contoValutaId = contoValutaId,
            esistente = inModifica,
            onChiudi = {
                nuova = false
                inModifica = null
            }
        )
    }
}

private fun corrisponde(op: Operazione, f: Filtri, dati: DatiApp): Boolean {
    if (f.da != null && op.data < f.da) return false
    if (f.a != null && op.data > f.a) return false
    val importo = abs(op.importoCent)
    testoInCent(f.importoMin)?.let { if (importo < abs(it)) return false }
    testoInCent(f.importoMax)?.let { if (importo > abs(it)) return false }
    val voce = op.voceId?.let { dati.vociPerId[it] }
    val tipo = if (op.trasferimento) "Spostamento" else voce?.tipo ?: ""
    if (f.tipo.isNotBlank() && !tipo.contains(f.tipo.trim(), ignoreCase = true)) return false
    if (f.sottotipo.isNotBlank() && !(voce?.sottotipo ?: "").contains(f.sottotipo.trim(), ignoreCase = true)) return false
    return true
}

@Composable
private fun PannelloFiltri(filtri: Filtri, dati: DatiApp, onFiltri: (Filtri) -> Unit) {
    val tipi = remember(dati.voci) { (dati.voci.map { it.tipo } + "Spostamento").distinct().sortedBy { it.lowercase() } }
    val sottotipi = remember(dati.voci, filtri.tipo) {
        dati.voci.filter { filtri.tipo.isBlank() || it.tipo.equals(filtri.tipo.trim(), ignoreCase = true) }
            .mapNotNull { it.sottotipo }.distinct().sortedBy { it.lowercase() }
    }
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Filtri", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CampoData("Dal", filtri.da, { onFiltri(filtri.copy(da = it)) }, Modifier.weight(1f), consentiVuoto = true)
                CampoData("Al", filtri.a, { onFiltri(filtri.copy(a = it)) }, Modifier.weight(1f), consentiVuoto = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filtri.importoMin,
                    onValueChange = { onFiltri(filtri.copy(importoMin = it)) },
                    label = { Text("Importo da") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = filtri.importoMax,
                    onValueChange = { onFiltri(filtri.copy(importoMax = it)) },
                    label = { Text("Importo a") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            CampoAutocompletamento("Tipo", filtri.tipo, tipi, { onFiltri(filtri.copy(tipo = it, sottotipo = "")) })
            CampoAutocompletamento("Sottotipo", filtri.sottotipo, sottotipi, { onFiltri(filtri.copy(sottotipo = it)) })
            if (filtri.attivi) {
                TextButton(onClick = { onFiltri(Filtri()) }, modifier = Modifier.align(Alignment.End)) { Text("Azzera filtri") }
            }
        }
    }
}

@Composable
private fun RigaListaOperazione(riga: RigaOperazione, dati: DatiApp, valuta: String, onClick: () -> Unit) {
    val op = riga.operazione
    val descrizione = if (op.trasferimento) {
        val verso = if (op.importoCent < 0) "verso" else "da"
        "↔ Spostamento $verso ${dati.etichetta(op.contoValutaDestId)}"
    } else {
        op.voceId?.let { dati.vociPerId[it]?.descrizione } ?: "Senza voce"
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(descrizione, style = MaterialTheme.typography.bodyLarge)
            Text(
                formattaData(op.data) + (op.note?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            TestoImporto(op.importoCent / 100.0, valuta, grassetto = true)
            Text(
                "saldo ${formattaCent(riga.saldoDopo, valuta)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
