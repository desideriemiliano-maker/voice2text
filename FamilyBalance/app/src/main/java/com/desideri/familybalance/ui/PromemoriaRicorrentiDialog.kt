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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.sicurezza.LocalAppBloccata
import com.desideri.familybalance.logica.RigaRicorrente
import com.desideri.familybalance.logica.formattaData
import com.desideri.familybalance.logica.formattaImporto
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

/** Tempo minimo in background perché il ritorno nell'app conti come nuova apertura (es. non la scelta di un file). */
private const val MILLIS_NUOVA_APERTURA = 60_000L

/**
 * Promemoria delle spese ricorrenti: alla prima apertura del giorno, se ci sono spese ricorrenti
 * ancora da pagare previste nei prossimi giorni (Impostazioni) o già scadute nel mese, un popup le
 * elenca. "Ok" lo conferma per oggi; "Ricordamelo" lo ripropone alla prossima apertura dell'app
 * (avvio o ritorno dopo almeno un minuto in background).
 */
@Composable
fun PromemoriaRicorrenti(vm: SpeseViewModel) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val impostazioni by vm.impostazioni.collectAsStateWithLifecycle()
    val cambi by vm.cambi.collectAsStateWithLifecycle()
    val personalizzazioni by vm.personalizzazioni.collectAsStateWithLifecycle()
    // Rimandato fino alla prossima apertura.
    var rimandato by rememberSaveable { mutableStateOf(false) }
    var confermato by remember { mutableStateOf(vm.promemoriaConfermatoOggi()) }
    var inBackgroundDal by remember { mutableLongStateOf(0L) }
    var aperta by remember { mutableStateOf<RigaRicorrente?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val osservatore = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_STOP -> inBackgroundDal = System.currentTimeMillis()
                Lifecycle.Event.ON_START -> {
                    // Nuovo giorno o nuova apertura: il promemoria può ricomparire.
                    confermato = vm.promemoriaConfermatoOggi()
                    if (inBackgroundDal > 0 && System.currentTimeMillis() - inBackgroundDal >= MILLIS_NUOVA_APERTURA) rimandato = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(osservatore)
        onDispose { lifecycleOwner.lifecycle.removeObserver(osservatore) }
    }

    val giorni = impostazioni.giorniPromemoriaRicorrenti
    val oggi = LocalDate.now()
    val righe = remember(dati, cambi, personalizzazioni, giorni, oggi) {
        if (giorni <= 0 || dati.voci.isEmpty()) return@remember emptyList<RigaRicorrente>()
        val fine = oggi.plusDays(giorni.toLong())
        val mesi = generateSequence(YearMonth.from(oggi)) { it.plusMonths(1) }.takeWhile { it <= YearMonth.from(fine) }.toList()
        Calcoli.ricorrenti(mesi, dati.voci, dati.contiValuta, dati.operazioni, cambi, YearMonth.now(), personalizzazioni, oggi.toEpochDay())
            .flatMap { m -> m.righe }
            .filter { r ->
                val data = r.dataStimata ?: return@filter false
                // Da pagare entro la fine del promemoria; le scadute solo del mese corrente.
                r.previsto != null && !r.annullata && data <= fine.toEpochDay() &&
                    (data >= oggi.toEpochDay() || Calcoli.mese(data) == YearMonth.from(oggi))
            }
            .sortedBy { it.dataStimata }
    }

    // Sopra il blocco biometrico non deve comparire: si aspetta lo sblocco.
    if (!LocalAppBloccata.current && !confermato && !rimandato && righe.isNotEmpty() && aperta == null) {
        val (scadute, prossime) = righe.partition { it.dataStimata!! < oggi.toEpochDay() }
        AlertDialog(
            onDismissRequest = { rimandato = true },
            title = { Text("Spese ricorrenti in arrivo") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Da pagare nei prossimi $giorni giorni" + (if (scadute.isNotEmpty()) " e già scadute questo mese" else "") +
                            ". Tocca una spesa per il dettaglio.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (scadute.isNotEmpty()) {
                        Text("Scadute", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
                        scadute.forEach { RigaPromemoria(it) { aperta = it } }
                    }
                    if (prossime.isNotEmpty()) {
                        Text("Prossime", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                        prossime.forEach { RigaPromemoria(it) { aperta = it } }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row {
                        Text("Totale", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(formattaImporto(righe.sumOf { abs(it.previsto ?: 0.0) }), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.confermaPromemoria()
                    confermato = true
                }) { Text("Ok, visto") }
            },
            dismissButton = { TextButton(onClick = { rimandato = true }) { Text("Ricordamelo dopo") } }
        )
    }

    aperta?.let { r ->
        val mese = r.dataStimata?.let { Calcoli.mese(it) } ?: YearMonth.now()
        DettaglioRicorrenteDialog(vm, dati, r.copy(voce = dati.vociPerId[r.voce.id] ?: r.voce), mese, onChiudi = { aperta = null })
    }
}

@Composable
private fun RigaPromemoria(r: RigaRicorrente, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(r.voce.descrizione, style = MaterialTheme.typography.bodyMedium)
            Text(
                (if (r.dataPrevista != null) "prevista il " else "stimata il ") + formattaData(r.dataStimata!!) +
                    (if (r.pagato != 0.0) " · già pagati ${formattaImporto(abs(r.pagato))}" else ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(formattaImporto(abs(r.previsto ?: 0.0)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}
