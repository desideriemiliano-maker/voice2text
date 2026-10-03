package com.desideri.familybalance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.data.dataPerRicorrente
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.Classe
import com.desideri.familybalance.logica.formattaData
import com.desideri.familybalance.logica.formattaMese
import java.time.YearMonth

/** Le voci del bilancio di cui si possono vedere le operazioni. */
enum class ComponenteBilancio(val titolo: String) {
    ENTRATE_MESE_PRIMA("Stipendio / interessi del mese prima"),
    CORRENTI("Spese correnti"),
    RICORRENTI("Spese ricorrenti"),
    RISPARMIO("Risparmio"),
    SPESE("Totale spese"),
    RESIDUO("Residuo")
}

/** Apre il dettaglio delle operazioni di una voce del bilancio di un mese (fornito da BilancioScreen). */
val LocalDettaglioBilancio = compositionLocalOf<(YearMonth, ComponenteBilancio) -> Unit> { { _, _ -> } }

/**
 * Le operazioni che compongono una voce del bilancio di [mese]: entrate del mese prima, spese
 * correnti, ricorrenti (nel mese a cui sono imputate) o le loro combinazioni. In EUR come nel
 * Bilancio; toccandole si aprono.
 */
@Composable
fun DettaglioBilancioDialog(vm: SpeseViewModel, mese: YearMonth, componente: ComponenteBilancio, onChiudi: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val cambi by vm.cambi.collectAsStateWithLifecycle()
    var inModifica by remember { mutableStateOf<Operazione?>(null) }
    fun classe(op: Operazione) = Calcoli.classifica(op, op.voceId?.let { dati.vociPerId[it] })
    val entrate = { dati.operazioni.filter { classe(it) == Classe.ENTRATA && Calcoli.mese(it.data) == mese.minusMonths(1) } }
    val correnti = { dati.operazioni.filter { classe(it) == Classe.CORRENTE && Calcoli.mese(it.data) == mese } }
    val ricorrenti = { dati.operazioni.filter { classe(it) == Classe.RICORRENTE && Calcoli.mese(it.dataPerRicorrente) == mese } }
    val operazioni = when (componente) {
        ComponenteBilancio.ENTRATE_MESE_PRIMA -> entrate()
        ComponenteBilancio.CORRENTI -> correnti()
        ComponenteBilancio.RICORRENTI -> ricorrenti()
        ComponenteBilancio.RISPARMIO -> entrate() + correnti()
        ComponenteBilancio.SPESE -> correnti() + ricorrenti()
        ComponenteBilancio.RESIDUO -> entrate() + correnti() + ricorrenti()
    }.sortedBy { it.data }
    fun eur(op: Operazione): Double {
        val m = if (classe(op) == Classe.RICORRENTE) Calcoli.mese(op.dataPerRicorrente) else Calcoli.mese(op.data)
        return cambi.inEuro(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR", m)
    }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("${componente.titolo} · ${formattaMese(mese)}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row {
                    Text("${operazioni.size} operazioni", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TestoImporto(operazioni.sumOf { eur(it) }, grassetto = true)
                }
                if (operazioni.isEmpty()) Text("Nessuna operazione.", style = MaterialTheme.typography.bodySmall)
                operazioni.forEach { op ->
                    HorizontalDivider()
                    Column(modifier = Modifier.fillMaxWidth().clickable { inModifica = op }.padding(vertical = 3.dp)) {
                        Row {
                            Text("${formattaData(op.data)} · ${dati.etichetta(op.contoValutaId)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TestoImporto(eur(op))
                        }
                        val descrizione = op.voceId?.let { dati.vociPerId[it]?.descrizione } ?: "Senza tipo"
                        Text(descrizione + (op.note?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                        op.dataRicorrente?.let { Text("Imputata al ${formattaData(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                    }
                }
                Text("Importi in EUR (CHF al cambio del mese). Tocca un'operazione per aprirla.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onChiudi) { Text("Chiudi") } }
    )
    inModifica?.let { op -> OperazioneDialog(vm, dati, op.contoValutaId, op, onChiudi = { inModifica = null }) }
}

