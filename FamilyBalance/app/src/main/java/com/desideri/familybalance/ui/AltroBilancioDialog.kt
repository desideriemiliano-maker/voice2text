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
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.formattaData
import java.time.YearMonth

/**
 * Dettaglio delle colonne «Spostamenti» e «Altro» del report del bilancio per il periodo [mesi]:
 * - gli spostamenti del periodo, uno per uno (sommati fanno zero solo se entrambe le righe sono
 *   nell'app, nella stessa valuta e nello stesso periodo: è il loro riscontro);
 * - «Altro» ([altro], null per il primo periodo): l'effetto cambio sui saldi CHF ([effettoCambio])
 *   e l'eventuale resto (arrotondamenti del cambio).
 * Le ricorrenti non ci sono: nel bilancio anche il saldo le conta nel mese a cui sono imputate.
 */
@Composable
fun AltroBilancioDialog(vm: SpeseViewModel, titolo: String, mesi: Set<YearMonth>, effettoCambio: Double, altro: Double?, onChiudi: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val cambi by vm.cambi.collectAsStateWithLifecycle()
    var inModifica by remember { mutableStateOf<Operazione?>(null) }
    fun eur(op: Operazione, mese: YearMonth) = cambi.inEuro(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR", mese)

    // Ogni gruppo: operazioni con il loro effetto (EUR con segno) su «Altro».
    val spostamenti = dati.operazioni.filter { it.trasferimento && Calcoli.mese(it.data) in mesi }
        .map { it to eur(it, Calcoli.mese(it.data)) }

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
        title = { Text("Spostamenti e altro · $titolo") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Gli spostamenti tra i tuoi conti sommati fanno zero; un totale diverso indica righe senza contro-operazione, " +
                        "verso conti fuori dall'app o con cambio EUR/CHF. «Altro» è il resto della variazione del saldo.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (spostamenti.isEmpty()) Text("Nessuno spostamento nel periodo.", style = MaterialTheme.typography.bodySmall)
                Gruppo(
                    "Spostamenti",
                    "Ogni riga con il suo segno (uscita in rosso, entrata in verde). Si annullano solo con la contro-operazione " +
                        "nell'app, nella stessa valuta e nello stesso periodo: una riga senza controparte o un cambio EUR/CHF lasciano un resto.",
                    spostamenti
                ) { op ->
                    "Spostamento " + (if (op.importoCent < 0) "verso " else "da ") +
                        (op.contoValutaDestId?.let { dati.etichetta(it) } ?: "conto non indicato") +
                        (if (op.collegataId == null) " · senza contro-operazione collegata" else "")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row {
                    Text("Effetto cambio sui saldi CHF", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TestoImporto(effettoCambio)
                }
                if (altro != null && kotlin.math.abs(altro - effettoCambio) >= 0.01) {
                    Row {
                        Text("Arrotondamenti / altro", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TestoImporto(altro - effettoCambio)
                    }
                }
                Row {
                    Text("Totale «Altro»", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (altro != null) TestoImporto(altro, grassetto = true) else Text("—")
                }
                Text("Importi in EUR (CHF al cambio del mese). Tocca un'operazione per aprirla.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onChiudi) { Text("Chiudi") } }
    )
    inModifica?.let { op -> OperazioneDialog(vm, dati, op.contoValutaId, op, onChiudi = { inModifica = null }) }
}
