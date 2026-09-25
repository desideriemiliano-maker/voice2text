package com.desideri.spesefamiglia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.spesefamiglia.SpeseViewModel
import com.desideri.spesefamiglia.logica.MeseRicorrenti
import com.desideri.spesefamiglia.logica.formattaImporto
import com.desideri.spesefamiglia.logica.formattaMese
import java.time.YearMonth

/**
 * Spese ricorrenti mese per mese (ex foglio "Bollette"): per i mesi passati quanto pagato, per il
 * mese corrente e i futuri le scadenze previste non ancora pagate.
 */
@Composable
fun RicorrentiScreen(vm: SpeseViewModel) {
    val mesi by vm.ricorrenti.collectAsStateWithLifecycle()
    val oggi = YearMonth.now()
    val stato = rememberLazyListState()
    LaunchedEffect(mesi.isNotEmpty()) {
        val indice = mesi.indexOfFirst { it.mese == oggi }
        if (indice >= 0) stato.scrollToItem(indice)
    }

    if (mesi.all { it.righe.isEmpty() }) {
        Text(
            "Nessuna spesa ricorrente. Segna le voci come ricorrenti in Anagrafica spese (menu ⋮).",
            modifier = Modifier.padding(24.dp)
        )
        return
    }

    LazyColumn(state = stato, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(mesi, key = { it.mese.toString() }) { mese -> CardMese(mese, oggi) }
    }
}

@Composable
private fun CardMese(mese: MeseRicorrenti, oggi: YearMonth) {
    val corrente = mese.mese == oggi
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (corrente) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) else CardDefaults.cardColors()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formattaMese(mese.mese) + if (corrente) " (corrente)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TestoImporto(mese.totalePagato + mese.totalePrevisto, grassetto = true)
            }
            if (mese.totalePrevisto != 0.0 && mese.totalePagato != 0.0) {
                Text(
                    "Pagato ${formattaImporto(mese.totalePagato)} · previsto ${formattaImporto(mese.totalePrevisto)}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (mese.righe.isEmpty()) {
                Text("Nessuna spesa ricorrente", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            mese.righe.forEachIndexed { indice, riga ->
                if (indice == 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        (if (riga.previsto != null) "⏳ " else "✓ ") + (riga.voce.sottotipo ?: riga.voce.tipo),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (riga.previsto != null) {
                        Text(
                            "previsto " + formattaImporto(riga.previsto),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        TestoImporto(riga.pagato)
                    }
                }
            }
        }
    }
}
