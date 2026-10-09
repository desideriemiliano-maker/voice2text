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

/**
 * Da dove viene la colonna «Altro» del report del bilancio per il periodo [mesi]: la variazione del
 * saldo dei conti non spiegata da entrate e spese. È la somma di:
 * - gli spostamenti del periodo (a saldo zero solo se entrambe le righe sono nell'app, nella stessa
 *   valuta e nello stesso periodo);
 * - le spese ricorrenti pagate nel periodo ma imputate a un altro mese (escono dal saldo qui, ma
 *   contano come spesa altrove) e quelle imputate al periodo ma pagate in un altro mese;
 * - l'effetto cambio sui saldi CHF ([effettoCambio]).
 */
@Composable
fun AltroBilancioDialog(vm: SpeseViewModel, titolo: String, mesi: Set<YearMonth>, effettoCambio: Double, altro: Double, onChiudi: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val cambi by vm.cambi.collectAsStateWithLifecycle()
    var inModifica by remember { mutableStateOf<Operazione?>(null) }
    fun classe(op: Operazione) = Calcoli.classifica(op, op.voceId?.let { dati.vociPerId[it] })
    fun eur(op: Operazione, mese: YearMonth) = cambi.inEuro(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR", mese)

    // Ogni gruppo: operazioni con il loro effetto (EUR con segno) su «Altro».
    val spostamenti = dati.operazioni.filter { it.trasferimento && Calcoli.mese(it.data) in mesi }
        .map { it to eur(it, Calcoli.mese(it.data)) }
    val ricorrenti = dati.operazioni.filter { classe(it) == Classe.RICORRENTE }
    val imputateAltrove = ricorrenti.filter { Calcoli.mese(it.data) in mesi && Calcoli.mese(it.dataPerRicorrente) !in mesi }
        .map { it to eur(it, Calcoli.mese(it.data)) }
    val imputateQui = ricorrenti.filter { Calcoli.mese(it.dataPerRicorrente) in mesi && Calcoli.mese(it.data) !in mesi }
        .map { it to -eur(it, Calcoli.mese(it.dataPerRicorrente)) }
    val spiegato = effettoCambio + listOf(spostamenti, imputateAltrove, imputateQui).sumOf { g -> g.sumOf { it.second } }

    @Composable
    fun Gruppo(nome: String, spiegazione: String, righe: List<Pair<Operazione, Double>>, dettaglio: (Operazione) -> String) {
        if (righe.isEmpty()) return
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Row {
            Text(nome, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TestoImporto(righe.sumOf { it.second }, grassetto = true)
        }
        Text(spiegazione, style = MaterialTheme.typography.labelSmall)
        righe.sortedBy { it.first.data }.forEach { (op, valore) ->
            Column(modifier = Modifier.fillMaxWidth().clickable { inModifica = op }.padding(vertical = 3.dp)) {
                Row {
                    Text("${formattaData(op.data)} · ${dati.etichetta(op.contoValutaId)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TestoImporto(valore)
                }
                Text(dettaglio(op) + (op.note?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, maxLines = 2)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("Altro · $titolo") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "«Altro» è la variazione del saldo dei conti che non viene da entrate e spese del periodo. Si compone così:",
                    style = MaterialTheme.typography.bodySmall
                )
                Row {
                    Text("Effetto cambio sui saldi CHF", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TestoImporto(effettoCambio, grassetto = true)
                }
                Gruppo(
                    "Spostamenti",
                    "Si annullano solo con la contro-operazione nell'app, nella stessa valuta e nello stesso periodo: " +
                        "una riga senza controparte o un cambio EUR/CHF lasciano un resto.",
                    spostamenti
                ) { op ->
                    "Spostamento " + (if (op.importoCent < 0) "verso " else "da ") +
                        (op.contoValutaDestId?.let { dati.etichetta(it) } ?: "conto non indicato") +
                        (if (op.collegataId == null) " · senza contro-operazione collegata" else "")
                }
                Gruppo(
                    "Ricorrenti imputate a un altro mese",
                    "Pagate nel periodo (il saldo cambia qui) ma contate come spesa del mese a cui sono imputate.",
                    imputateAltrove
                ) { op -> (op.voceId?.let { dati.vociPerId[it]?.descrizione } ?: "") + " · imputata a ${formattaMese(Calcoli.mese(op.dataPerRicorrente))}" }
                Gruppo(
                    "Ricorrenti pagate in un altro mese",
                    "Contate come spesa del periodo ma pagate (e uscite dal saldo) in un altro mese.",
                    imputateQui
                ) { op -> (op.voceId?.let { dati.vociPerId[it]?.descrizione } ?: "") + " · imputata a ${formattaMese(Calcoli.mese(op.dataPerRicorrente))}" }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row {
                    Text("Totale «Altro»", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TestoImporto(altro, grassetto = true)
                }
                if (kotlin.math.abs(altro - spiegato) >= 0.01) {
                    Row {
                        Text("Arrotondamenti / cambio", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TestoImporto(altro - spiegato)
                    }
                }
                Text("Importi in EUR (CHF al cambio del mese). Tocca un'operazione per aprirla.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onChiudi) { Text("Chiudi") } }
    )
    inModifica?.let { op -> OperazioneDialog(vm, dati, op.contoValutaId, op, onChiudi = { inModifica = null }) }
}
