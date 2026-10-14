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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Conto
import com.desideri.familybalance.data.ContoValuta
import com.desideri.familybalance.data.Valute
import com.desideri.familybalance.logica.centInTesto
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.testoInCent

/** Anagrafica conti: nome, valute (EUR/CHF) e saldo iniziale per ciascuna valuta. */
@Composable
fun AnagraficaContiScreen(vm: SpeseViewModel, onIndietro: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    var inModifica by remember { mutableStateOf<Conto?>(null) }

    Scaffold(
        topBar = { BarraIndietro("Anagrafica conti", onIndietro) },
        floatingActionButton = {
            FloatingActionButton(onClick = { inModifica = Conto(nome = "") }) { Icon(Icons.Filled.Add, contentDescription = "Nuovo conto") }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (dati.conti.isEmpty()) item { Text("Nessun conto. Aggiungine uno con il pulsante +.") }
            items(dati.conti, key = { it.id }) { conto ->
                val valute = dati.contiValuta.filter { it.contoId == conto.id }.sortedBy { it.valuta }
                Card(modifier = Modifier.fillMaxWidth().clickable { inModifica = conto }) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PallinoColore(conto.colore, modifier = Modifier.padding(end = 8.dp), dimensione = 14.dp)
                            Text(conto.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        if (conto.virtuale) Text("Virtuale: escluso dal saldo totale e dal bilancio", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                        valute.forEach { cv ->
                            Text("${cv.valuta} · saldo iniziale ${formattaCent(cv.saldoInizialeCent, cv.valuta)}", style = MaterialTheme.typography.bodyMedium)
                            cv.descrizione?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }

    inModifica?.let { conto ->
        ContoDialog(
            conto = conto,
            valuteEsistenti = dati.contiValuta.filter { it.contoId == conto.id && conto.id != 0L },
            onSalva = { nome, virtuale, colore, saldi, descrizioni ->
                vm.salvaConto(conto.copy(nome = nome, virtuale = virtuale, colore = colore), saldi, descrizioni)
                inModifica = null
            },
            onElimina = {
                vm.eliminaConto(conto)
                inModifica = null
            },
            onChiudi = { inModifica = null }
        )
    }
}

@Composable
private fun ContoDialog(
    conto: Conto,
    valuteEsistenti: List<ContoValuta>,
    onSalva: (String, Boolean, Int?, Map<String, Long>, Map<String, String>) -> Unit,
    onElimina: () -> Unit,
    onChiudi: () -> Unit
) {
    var nome by remember { mutableStateOf(conto.nome) }
    var virtuale by remember { mutableStateOf(conto.virtuale) }
    var colore by remember { mutableStateOf(conto.colore) }
    val attive = remember {
        mutableStateMapOf<String, Boolean>().apply {
            Valute.TUTTE.forEach { v -> put(v, valuteEsistenti.any { it.valuta == v } || (conto.id == 0L && v == Valute.EUR)) }
        }
    }
    val saldi = remember {
        mutableStateMapOf<String, String>().apply {
            Valute.TUTTE.forEach { v -> put(v, valuteEsistenti.firstOrNull { it.valuta == v }?.let { centInTesto(it.saldoInizialeCent) } ?: "0") }
        }
    }
    val descrizioni = remember {
        mutableStateMapOf<String, String>().apply {
            Valute.TUTTE.forEach { v -> put(v, valuteEsistenti.firstOrNull { it.valuta == v }?.descrizione.orEmpty()) }
        }
    }
    var errore by remember { mutableStateOf<String?>(null) }
    var confermaElimina by remember { mutableStateOf(false) }

    fun salva() {
        if (nome.isBlank()) return run { errore = "Inserisci il nome del conto" }
        val risultato = LinkedHashMap<String, Long>()
        for (v in Valute.TUTTE) {
            if (attive[v] != true) continue
            risultato[v] = testoInCent(saldi[v] ?: "0") ?: return run { errore = "Saldo iniziale $v non valido" }
        }
        if (risultato.isEmpty()) return run { errore = "Seleziona almeno una valuta" }
        onSalva(nome.trim(), virtuale, colore, risultato, risultato.keys.associateWith { descrizioni[it].orEmpty() })
    }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(if (conto.id == 0L) "Nuovo conto" else "Modifica conto") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = nome, onValueChange = { nome = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SelettoreColore(colore) { colore = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = virtuale, onCheckedChange = { virtuale = it })
                    Column {
                        Text("Conto virtuale")
                        Text("Escluso dal saldo totale e dal bilancio (es. fondo accantonato).", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Valute.TUTTE.forEach { v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = attive[v] == true, onCheckedChange = { attive[v] = it })
                        Text(v, modifier = Modifier.padding(end = 8.dp))
                        OutlinedTextField(
                            value = saldi[v] ?: "",
                            onValueChange = { saldi[v] = it },
                            label = { Text("Saldo iniziale") },
                            enabled = attive[v] == true,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Testo libero sotto il saldo iniziale, solo per le valute abilitate.
                    if (attive[v] == true) {
                        OutlinedTextField(
                            value = descrizioni[v].orEmpty(),
                            onValueChange = { descrizioni[v] = it },
                            label = { Text("Descrizione $v (facoltativa)") },
                            // Testo su più righe: Invio va a capo.
                            singleLine = false,
                            minLines = 3,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = ::salva) { Text("Salva") } },
        dismissButton = {
            Row {
                if (conto.id != 0L) {
                    TextButton(onClick = { confermaElimina = true }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onChiudi) { Text("Annulla") }
            }
        }
    )

    if (confermaElimina) {
        DialogConferma(
            titolo = "Elimina conto",
            testo = "Verranno eliminate anche tutte le operazioni di \"${conto.nome}\". Continuare?",
            conferma = "Elimina",
            onConferma = onElimina,
            onAnnulla = { confermaElimina = false }
        )
    }
}
