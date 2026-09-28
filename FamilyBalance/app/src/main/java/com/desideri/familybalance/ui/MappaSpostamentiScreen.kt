package com.desideri.familybalance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.DatiApp
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val FORMATO_GIORNO = DateTimeFormatter.ofPattern("dd/MM")
private val FORMATO_ANNO = DateTimeFormatter.ofPattern("yyyy")

private fun importo(op: Operazione, dati: DatiApp): String =
    formattaCent(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR")

/** Due righe collegate sono coerenti se hanno segno opposto e, nella stessa valuta, lo stesso importo. */
private fun coerenti(a: Operazione, b: Operazione, dati: DatiApp): Boolean {
    if ((a.importoCent > 0) == (b.importoCent > 0)) return false
    val stessaValuta = dati.contiValutaPerId[a.contoValutaId]?.valuta == dati.contiValutaPerId[b.contoValutaId]?.valuta
    return !stessaValuta || a.importoCent == -b.importoCent
}

/**
 * Spostamenti a colonne (menu ⋮): tutti gli spostamenti del periodo in verticale, una riga per
 * data e una colonna per conto/valuta. Le righe collegate sono unite da una linea (rossa se
 * incoerenti); una riga non collegata si collega tenendola premuta e trascinandola su quella
 * corrispondente di un altro conto. Toccando una riga la si modifica o elimina.
 */
@Composable
fun MappaSpostamentiScreen(vm: SpeseViewModel, onIndietro: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val oggi = LocalDate.now().toEpochDay()
    var da by rememberSaveable { mutableStateOf<Long?>(oggi - 90) }
    var a by rememberSaveable { mutableStateOf<Long?>(null) }
    var soloDaCollegare by rememberSaveable { mutableStateOf(false) }

    val perId = remember(dati.operazioni) { dati.operazioni.associateBy { it.id } }
    val trasferimenti = remember(dati.operazioni, da, a, soloDaCollegare) {
        dati.operazioni.filter { op ->
            op.trasferimento && (da == null || op.data >= da!!) && (a == null || op.data <= a!!) &&
                (!soloDaCollegare || op.collegataId == null)
        }
    }
    val visibili = remember(trasferimenti) { trasferimenti.map { it.id }.toHashSet() }
    val colonne = dati.contiValutaOrdinati.filter { cv -> trasferimenti.any { it.contoValutaId == cv.id } }
    val perData = remember(trasferimenti) { trasferimenti.groupBy { it.data }.toSortedMap(compareByDescending { it }) }

    // Posizioni (coordinate della finestra) delle righe e del contenuto scorrevole, per le linee.
    val posizioni = remember { mutableStateMapOf<Long, Rect>() }
    var origineContenuto by remember { mutableStateOf(Offset.Zero) }
    var areaVisibile by remember { mutableStateOf(Rect.Zero) }
    val scorrimento = rememberScrollState()

    // Trascinamento in corso: riga trascinata e punto del dito (coordinate della finestra).
    var trascinata by remember { mutableStateOf<Operazione?>(null) }
    var dito by remember { mutableStateOf(Offset.Zero) }
    val bersaglio = trascinata?.let { t ->
        posizioni.entries.firstOrNull { (id, r) -> id != t.id && id in visibili && r.contains(dito) }?.key?.let { perId[it] }
    }
    var daCollegare by remember { mutableStateOf<Pair<Operazione, Operazione>?>(null) }
    var inModifica by remember { mutableStateOf<Operazione?>(null) }

    val colorePrimario = MaterialTheme.colorScheme.primary
    val coloreErrore = MaterialTheme.colorScheme.error
    val coloreTrascinamento = MaterialTheme.colorScheme.tertiary

    Scaffold(topBar = { BarraIndietro("Spostamenti a colonne", onIndietro) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Filtri: periodo e solo righe da collegare.
            Column(modifier = Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoData("Dal", da, { da = it }, Modifier.weight(1f), consentiVuoto = true)
                    CampoData("Al", a, { a = it }, Modifier.weight(1f), consentiVuoto = true)
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    listOf(30L to "1 mese", 90L to "3 mesi", 180L to "6 mesi").forEach { (giorni, testo) ->
                        FilterChip(selected = da == oggi - giorni && a == null, onClick = {
                            da = oggi - giorni
                            a = null
                        }, label = { Text(testo) })
                    }
                    FilterChip(selected = soloDaCollegare, onClick = { soloDaCollegare = !soloDaCollegare }, label = { Text("Da collegare") })
                }
                Text(
                    "Tieni premuta una riga non collegata e trascinala su quella corrispondente per collegarle; tocca una riga per modificarla.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // Intestazione delle colonne.
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
                Text("Data", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                colonne.forEach { cv ->
                    Text(
                        dati.etichetta(cv.id),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HorizontalDivider()
            if (trasferimenti.isEmpty()) {
                Text("Nessuno spostamento nel periodo.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned {
                areaVisibile = Rect(it.positionInRoot(), it.size.toSize())
            }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scorrimento, enabled = trascinata == null)
                        .onGloballyPositioned { origineContenuto = it.positionInRoot() }
                        .drawWithContent {
                            drawContent()
                            fun locale(r: Rect) = r.translate(-origineContenuto)
                            // Linee tra le righe collegate, entrambe visibili (una volta per coppia).
                            for (op in trasferimenti) {
                                val idAltra = op.collegataId ?: continue
                                if (idAltra !in visibili || op.id > idAltra) continue
                                val r1 = posizioni[op.id]?.let(::locale) ?: continue
                                val r2 = posizioni[idAltra]?.let(::locale) ?: continue
                                val altra = perId[idAltra] ?: continue
                                val (p1, p2) = if (r1.center.x <= r2.center.x) r1.centerRight to r2.centerLeft else r1.centerLeft to r2.centerRight
                                drawLine(
                                    color = if (coerenti(op, altra, dati)) colorePrimario else coloreErrore,
                                    start = p1,
                                    end = p2,
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                            // Linea tratteggiata dalla riga trascinata al dito.
                            trascinata?.let { t ->
                                val r = posizioni[t.id]?.let(::locale) ?: return@let
                                val punto = dito - origineContenuto
                                drawLine(
                                    color = coloreTrascinamento,
                                    start = r.center,
                                    end = punto,
                                    strokeWidth = 3.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                                )
                                drawCircle(coloreTrascinamento, radius = 8.dp.toPx(), center = punto)
                            }
                        }
                ) {
                    perData.forEach { (giorno, ops) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            val data = LocalDate.ofEpochDay(giorno)
                            Column(modifier = Modifier.width(44.dp).padding(top = 4.dp)) {
                                Text(data.format(FORMATO_GIORNO), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text(data.format(FORMATO_ANNO), style = MaterialTheme.typography.labelSmall)
                            }
                            colonne.forEach { cv ->
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ops.filter { it.contoValutaId == cv.id }.forEach { op ->
                                        CartaSpostamento(
                                            op = op,
                                            dati = dati,
                                            altraFuoriPeriodo = op.collegataId != null && op.collegataId !in visibili,
                                            evidenziata = bersaglio?.id == op.id,
                                            inTrascinamento = trascinata?.id == op.id,
                                            onPosizione = { posizioni[op.id] = it },
                                            onTocco = { inModifica = op },
                                            onInizioTrascinamento = { puntoLocale ->
                                                val r = posizioni[op.id] ?: return@CartaSpostamento
                                                trascinata = op
                                                dito = r.topLeft + puntoLocale
                                            },
                                            onTrascinamento = { delta ->
                                                dito += delta
                                                // Scorrimento automatico vicino ai bordi dell'area visibile.
                                                when {
                                                    dito.y < areaVisibile.top + 48f -> scorrimento.dispatchRawDelta(-24f)
                                                    dito.y > areaVisibile.bottom - 48f -> scorrimento.dispatchRawDelta(24f)
                                                }
                                            },
                                            onFineTrascinamento = {
                                                val t = trascinata
                                                val b = bersaglio
                                                trascinata = null
                                                if (t != null && b != null) daCollegare = t to b
                                            },
                                            onAnnullaTrascinamento = { trascinata = null }
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }

    daCollegare?.let { (x, y) ->
        val problema = when {
            x.contoValutaId == y.contoValutaId -> "Le due righe sono sullo stesso conto."
            x.collegataId != null || y.collegataId != null -> "Una delle due righe è già collegata: prima scollegala (toccala e modificala)."
            (x.importoCent > 0) == (y.importoCent > 0) -> "Le due righe hanno lo stesso segno: uno spostamento esce da un conto ed entra nell'altro."
            else -> null
        }
        val stessaValuta = dati.contiValutaPerId[x.contoValutaId]?.valuta == dati.contiValutaPerId[y.contoValutaId]?.valuta
        AlertDialog(
            onDismissRequest = { daCollegare = null },
            title = { Text("Collega spostamento") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(x, y).forEach { op ->
                        Text(
                            "${dati.etichetta(op.contoValutaId)} · ${formattaData(op.data)} · ${importo(op, dati)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        op.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                    }
                    when {
                        problema != null -> Text(problema, color = MaterialTheme.colorScheme.error)
                        stessaValuta && x.importoCent != -y.importoCent ->
                            Text("Attenzione: stessa valuta ma importi diversi.", color = MaterialTheme.colorScheme.error)
                        else -> Text("Le due righe verranno collegate come un unico spostamento; importi e date restano i loro.")
                    }
                }
            },
            confirmButton = {
                if (problema == null) {
                    TextButton(onClick = {
                        vm.collegaSpostamenti(listOf(x to y))
                        daCollegare = null
                    }) { Text("Collega") }
                }
            },
            dismissButton = { TextButton(onClick = { daCollegare = null }) { Text(if (problema == null) "Annulla" else "Chiudi") } }
        )
    }

    inModifica?.let { op ->
        OperazioneDialog(vm, dati, op.contoValutaId, op, onChiudi = { inModifica = null })
    }
}

/** Una riga di spostamento nella sua colonna: importo e nota; le non collegate sono evidenziate. */
@Composable
private fun CartaSpostamento(
    op: Operazione,
    dati: DatiApp,
    altraFuoriPeriodo: Boolean,
    evidenziata: Boolean,
    inTrascinamento: Boolean,
    onPosizione: (Rect) -> Unit,
    onTocco: () -> Unit,
    onInizioTrascinamento: (Offset) -> Unit,
    onTrascinamento: (Offset) -> Unit,
    onFineTrascinamento: () -> Unit,
    onAnnullaTrascinamento: () -> Unit
) {
    val collegata = op.collegataId != null
    val colori = MaterialTheme.colorScheme
    // Il gesto resta attivo tra una ricomposizione e l'altra: si chiamano sempre le callback aggiornate.
    val inizio by rememberUpdatedState(onInizioTrascinamento)
    val trascina by rememberUpdatedState(onTrascinamento)
    val fine by rememberUpdatedState(onFineTrascinamento)
    val annulla by rememberUpdatedState(onAnnullaTrascinamento)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = when {
            evidenziata -> colori.primaryContainer
            collegata -> colori.surfaceVariant
            else -> colori.tertiaryContainer
        },
        border = when {
            evidenziata || inTrascinamento -> BorderStroke(2.dp, colori.tertiary)
            !collegata -> BorderStroke(1.dp, colori.tertiary)
            else -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onPosizione(Rect(it.positionInRoot(), it.size.toSize())) }
            .pointerInput(op.id, collegata) {
                // Solo le righe non collegate si trascinano (pressione lunga, poi trascina).
                if (!collegata) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { inizio(it) },
                        onDrag = { change, delta ->
                            change.consume()
                            trascina(delta)
                        },
                        onDragEnd = { fine() },
                        onDragCancel = { annulla() }
                    )
                }
            }
            .clickable(onClick = onTocco)
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)) {
            Text(
                importo(op, dati) + if (altraFuoriPeriodo) " ↗" else "",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (op.importoCent < 0) colori.error else colori.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            op.note?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
