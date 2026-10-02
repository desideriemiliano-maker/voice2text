package com.desideri.familybalance.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.ShowChart
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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.formattaData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContiScreen(vm: SpeseViewModel, onApriConto: (Long) -> Unit, onAnagraficaConti: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val saldi by vm.saldi.collectAsStateWithLifecycle()
    val impostazioni by vm.impostazioni.collectAsStateWithLifecycle()
    val ricaricaInCorso by vm.ricaricaInCorso.collectAsStateWithLifecycle()
    val cambi by vm.cambi.collectAsStateWithLifecycle()
    var mostraGrafico by remember { mutableStateOf(false) }
    // Riscontro dell'estratto conto direttamente sul conto/valuta: menu aperto e conto del file in scelta.
    var menuRiscontro by remember { mutableStateOf<Long?>(null) }
    var contoRiscontro by rememberSaveable { mutableStateOf<Long?>(null) }
    var saldiMensili by remember { mutableStateOf<Long?>(null) }
    var duplicati by remember { mutableStateOf<Long?>(null) }
    val sceltaFileAi = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cv = contoRiscontro
        if (uri != null && cv != null) vm.riscontraEstratto(uri, cv)
    }
    val sceltaFileExcel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cv = contoRiscontro
        if (uri != null && cv != null) vm.riscontraEstrattoExcel(uri, cv)
    }

    if (dati.caricati && dati.contiValuta.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Nessun conto configurato.", style = MaterialTheme.typography.titleMedium)
            Text(
                "Crea i conti dall'anagrafica (menu ⋮ › Anagrafica conti).",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Button(onClick = onAnagraficaConti) { Text("Anagrafica conti") }
        }
        return
    }

    val totaleEuro = dati.contiValuta.sumOf { Calcoli.inEuro(saldi[it.id] ?: 0L, it.valuta, impostazioni.cambioChfEur) }
    val ultimaPerConto = dati.operazioni.groupBy { it.contoValutaId }.mapValues { (_, ops) -> ops.maxOf { it.data } }

    // Trascinando verso il basso si rileggono i dati dal database.
    PullToRefreshBox(isRefreshing = ricaricaInCorso, onRefresh = vm::ricaricaDati, modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Saldo totale (in EUR)", style = MaterialTheme.typography.labelLarge)
                        TestoImporto(totaleEuro, grassetto = true)
                        if (dati.contiValuta.any { it.valuta != "EUR" }) {
                            Text(
                                "CHF convertiti a ${impostazioni.cambioChfEur} (Impostazioni)",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    IconButton(onClick = { mostraGrafico = true }, enabled = dati.operazioni.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = "Grafico del saldo")
                    }
                    }
                }
            }
            items(dati.contiValutaOrdinati, key = { it.id }) { cv ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onApriConto(cv.id) }) {
                    Row(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dati.etichetta(cv.id), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            ultimaPerConto[cv.id]?.let {
                                Text("Ultima operazione: ${formattaData(it)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TestoImporto((saldi[cv.id] ?: 0L) / 100.0, cv.valuta, grassetto = true)
                        Box {
                            IconButton(onClick = { menuRiscontro = cv.id }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Menu del conto")
                            }
                            DropdownMenu(expanded = menuRiscontro == cv.id, onDismissRequest = { menuRiscontro = null }) {
                                DropdownMenuItem(
                                    text = { Text("Riscontro con AI") },
                                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                                    onClick = {
                                        menuRiscontro = null
                                        contoRiscontro = cv.id
                                        sceltaFileAi.launch(arrayOf("*/*"))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Riscontro da Excel") },
                                    leadingIcon = { Icon(Icons.Filled.TableChart, contentDescription = null) },
                                    onClick = {
                                        menuRiscontro = null
                                        contoRiscontro = cv.id
                                        sceltaFileExcel.launch(
                                            arrayOf(
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                "application/vnd.ms-excel",
                                                "application/octet-stream"
                                            )
                                        )
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Saldi mensili") },
                                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null) },
                                    onClick = {
                                        menuRiscontro = null
                                        saldiMensili = cv.id
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Rileva duplicati") },
                                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        menuRiscontro = null
                                        duplicati = cv.id
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    duplicati?.let { DuplicatiDialog(vm, it, onChiudi = { duplicati = null }) }
    saldiMensili?.let { SaldiMensiliDialog(dati, it, onChiudi = { saldiMensili = null }) }

    if (mostraGrafico) {
        // Per ogni conto/valuta e per il totale: saldo a fine giorno, entrate e uscite (senza gli
        // spostamenti tra conti), in EUR al cambio del mese.
        val conti = remember(dati.operazioni, dati.contiValuta, cambi) {
            val colonne = dati.contiValutaOrdinati
            val saldiCent = colonne.associate { it.id to it.saldoInizialeCent }.toMutableMap()
            val saldi = colonne.associate { it.id to ArrayList<Pair<Long, Double>>() }
            val entrate = colonne.associate { it.id to ArrayList<Pair<Long, Double>>() }
            val uscite = colonne.associate { it.id to ArrayList<Pair<Long, Double>>() }
            val totale = ArrayList<Pair<Long, Double>>()
            dati.operazioni.groupBy { it.data }.toSortedMap().forEach { (giorno, ops) ->
                val mese = Calcoli.mese(giorno)
                ops.forEach { op ->
                    val cv = dati.contiValutaPerId[op.contoValutaId] ?: return@forEach
                    saldiCent[cv.id] = (saldiCent[cv.id] ?: 0L) + op.importoCent
                    if (!op.trasferimento) {
                        val eur = cambi.inEuro(op.importoCent, cv.valuta, mese)
                        if (eur > 0) entrate[cv.id]?.add(giorno to eur) else uscite[cv.id]?.add(giorno to -eur)
                    }
                }
                var somma = 0.0
                colonne.forEach { cv ->
                    val eur = cambi.inEuro(saldiCent.getValue(cv.id), cv.valuta, mese)
                    somma += eur
                    saldi.getValue(cv.id).add(giorno to eur)
                }
                totale += giorno to somma
            }
            colonne.map { cv -> ContoGrafico(dati.etichetta(cv.id), saldi.getValue(cv.id), entrate.getValue(cv.id), uscite.getValue(cv.id)) } +
                ContoGrafico("Totale", totale, colonne.flatMap { entrate.getValue(it.id) }, colonne.flatMap { uscite.getValue(it.id) })
        }
        GraficoContiDialog(
            titolo = "Andamento dei conti",
            nota = "In EUR (CHF al cambio del mese). Saldo a fine periodo; entrate e uscite sommate nel periodo, esclusi gli spostamenti tra conti.",
            conti = conti,
            onChiudi = { mostraGrafico = false }
        )
    }
}
