package com.desideri.spesefamiglia.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.spesefamiglia.SpeseViewModel
import com.desideri.spesefamiglia.data.Conto
import com.desideri.spesefamiglia.data.ContoValuta
import com.desideri.spesefamiglia.data.Valute
import com.desideri.spesefamiglia.logica.centInTesto
import com.desideri.spesefamiglia.logica.formattaCent
import com.desideri.spesefamiglia.logica.testoInCent

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
                        Text(conto.nome, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        valute.forEach { cv ->
                            Text("${cv.valuta} · saldo iniziale ${formattaCent(cv.saldoInizialeCent, cv.valuta)}", style = MaterialTheme.typography.bodyMedium)
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
            onSalva = { nome, saldi ->
                vm.salvaConto(conto.copy(nome = nome), saldi)
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
    onSalva: (String, Map<String, Long>) -> Unit,
    onElimina: () -> Unit,
    onChiudi: () -> Unit
) {
    var nome by remember { mutableStateOf(conto.nome) }
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
        onSalva(nome.trim(), risultato)
    }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(if (conto.id == 0L) "Nuovo conto" else "Modifica conto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = nome, onValueChange = { nome = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
