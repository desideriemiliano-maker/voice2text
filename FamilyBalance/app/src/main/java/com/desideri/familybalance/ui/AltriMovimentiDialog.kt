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
import com.desideri.familybalance.logica.Classe
import com.desideri.familybalance.logica.RigaBilancio
import com.desideri.familybalance.data.dataPerRicorrente
import com.desideri.familybalance.logica.formattaData
import com.desideri.familybalance.logica.formattaImporto
import com.desideri.familybalance.logica.formattaMese
import kotlin.math.abs

/**
 * Di cosa sono fatti gli "altri movimenti" di un mese del bilancio (saldo finale − iniziale −
 * correnti − ricorrenti): entrate arrivate nel mese, spostamenti senza contropartita nell'app,
 * ricorrenti pagate in questo mese ma imputate a un altro (o viceversa), effetto del cambio. Le
 * operazioni si toccano per vederle e modificarle.
 */
@Composable
fun AltriMovimentiDialog(vm: SpeseViewModel, r: RigaBilancio, onChiudi: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val cambi by vm.cambi.collectAsStateWithLifecycle()
    var inModifica by remember { mutableStateOf<Operazione?>(null) }
    val m = r.mese
    fun eur(op: Operazione) = cambi.inEuro(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR", Calcoli.mese(op.data))
    fun classe(op: Operazione) = Calcoli.classifica(op, op.voceId?.let { dati.vociPerId[it] })

    val entrate = dati.operazioni.filter { Calcoli.mese(it.data) == m && classe(it) == Classe.ENTRATA }
    // Spostamenti che non hanno la riga collegata su un altro conto dell'app: il denaro esce (o entra) davvero.
    val senzaControparte = dati.operazioni.filter { Calcoli.mese(it.data) == m && it.trasferimento && it.collegataId == null }
    // Ricorrenti addebitate in questo mese ma imputate a un altro (escono dal saldo qui, contano altrove) e viceversa.
    val ricorrentiAltrove = dati.operazioni.filter {
        classe(it) == Classe.RICORRENTE && (Calcoli.mese(it.data) == m) != (Calcoli.mese(it.dataPerRicorrente) == m)
    }
    val cambio = r.effettoCambio + r.cambioSpostamenti
    val altro = (r.saldoFinale ?: 0.0) - (r.saldoIniziale ?: 0.0) - r.correnti - r.ricorrentiTotali
    val spiegato = entrate.sumOf { eur(it) } + senzaControparte.sumOf { eur(it) } +
        ricorrentiAltrove.sumOf { if (Calcoli.mese(it.data) == m) eur(it) else -eur(it) } + cambio
    // Per il mese corrente il saldo finale è previsto: le ricorrenti ancora previste non sono "altro".
    val resto = altro - spiegato

    @Composable
    fun RigaOp(op: Operazione, valore: Double) {
        Column(modifier = Modifier.fillMaxWidth().clickable { inModifica = op }.padding(vertical = 3.dp)) {
            Row {
                Text("${formattaData(op.data)} · ${dati.etichetta(op.contoValutaId)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TestoImporto(valore)
            }
            val descrizione = op.voceId?.let { dati.vociPerId[it]?.descrizione } ?: if (op.trasferimento) "Spostamento" else "Senza tipo"
            Text(descrizione + (op.note?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, maxLines = 2)
        }
    }

    @Composable
    fun Sezione(titolo: String, totale: Double, spiegazione: String) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Row {
            Text(titolo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TestoImporto(totale, grassetto = true)
        }
        Text(spiegazione, style = MaterialTheme.typography.bodySmall)
    }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text("Altri movimenti · ${formattaMese(m)}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Totale ${formattaImporto(altro)}: quanto il saldo finale si discosta da saldo iniziale + spese correnti + spese ricorrenti. " +
                        "Ecco da cosa è composto. Tocca un'operazione per aprirla.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (entrate.isNotEmpty()) {
                    Sezione("Entrate del mese", entrate.sumOf { eur(it) }, "Stipendio/interessi arrivati in questo mese: entrano nel saldo ma nel risparmio conta lo stipendio del mese prima.")
                    entrate.sortedBy { it.data }.forEach { RigaOp(it, eur(it)) }
                }
                if (senzaControparte.isNotEmpty()) {
                    Sezione("Spostamenti senza riga collegata", senzaControparte.sumOf { eur(it) }, "Spostamenti verso (o da) conti che non sono nell'app, o di cui manca la riga sull'altro conto.")
                    senzaControparte.sortedBy { it.data }.forEach { RigaOp(it, eur(it)) }
                }
                if (ricorrentiAltrove.isNotEmpty()) {
                    Sezione(
                        "Ricorrenti imputate a un altro mese",
                        ricorrentiAltrove.sumOf { if (Calcoli.mese(it.data) == m) eur(it) else -eur(it) },
                        "Pagate in un mese ma con la \"data per la spesa ricorrente\" in un altro: nel saldo contano quando sono addebitate."
                    )
                    ricorrentiAltrove.sortedBy { it.data }.forEach { RigaOp(it, if (Calcoli.mese(it.data) == m) eur(it) else -eur(it)) }
                }
                if (abs(cambio) >= 0.5) {
                    Sezione("Effetto cambio CHF/EUR", cambio, "Rivalutazione dei saldi in CHF al cambio del mese e differenza di cambio negli spostamenti CHF↔EUR.")
                }
                if (abs(resto) >= 0.5) {
                    Sezione(
                        "Non spiegato",
                        resto,
                        "Differenza residua: ad esempio spostamenti con le due righe in mesi diversi o con importi diversi, arrotondamenti del cambio."
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onChiudi) { Text("Chiudi") } }
    )
    inModifica?.let { op -> OperazioneDialog(vm, dati, op.contoValutaId, op, onChiudi = { inModifica = null }) }
}
