package com.desideri.familybalance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.data.Stipendio
import com.desideri.familybalance.data.numeroNelMese
import com.desideri.familybalance.data.valoriStipendio
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaData
import com.desideri.familybalance.logica.formattaMese
import java.time.LocalDate
import java.time.YearMonth

/** Elemento del riscontro: uno stipendio registrato (a sinistra) o un accredito del conto (a destra). */
private sealed class ElementoRiscontro {
    abstract val chiave: String
    data class DiStipendio(val stipendio: Stipendio) : ElementoRiscontro() { override val chiave = "s${stipendio.id}" }
    data class DiOperazione(val operazione: Operazione) : ElementoRiscontro() { override val chiave = "o${operazione.id}" }
}

/**
 * Riscontro stipendi (menu ⋮): per lo stipendio scelto dall'anagrafica, gli stipendi registrati
 * (a sinistra, con il valore della voce da confrontare, es. il netto) e gli accrediti sul conto di
 * destinazione (a destra), mese per mese. Come nel riscontro spostamenti, uno stipendio e un
 * accredito si associano tenendo premuto uno dei due e trascinandolo sull'altro; la linea che li
 * unisce è rossa se gli importi sono diversi e toccandola si scollegano.
 */
@Composable
fun RiscontroStipendiDialog(vm: SpeseViewModel, onChiudi: () -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val stipendi by vm.stipendi.collectAsStateWithLifecycle()
    val tipi = stipendi.tipi.filterNot { it.obsoleto }
    var tipoId by rememberSaveable { mutableStateOf<Long?>(null) }
    val tipo = tipi.firstOrNull { it.id == tipoId } ?: tipi.firstOrNull()
    val voci = tipo?.let { stipendi.vociDi(it.id) }.orEmpty()
    // Voce da confrontare con gli accrediti: quella dell'anagrafica stipendi (null = non indicata).
    val voceId = tipo?.voceRiscontroId?.takeIf { id -> voci.any { it.id == id } }
    // Voci di spesa (entrate) usate nel riscontro: gli accrediti mostrati sono solo le loro operazioni.
    val vociSpesaRiscontro = dati.voci.filter { it.entrata && it.riscontroStipendi }.map { it.id }.toSet()
    val oggi = YearMonth.now()
    var da by remember { mutableStateOf<YearMonth?>(oggi.minusMonths(11)) }
    var a by remember { mutableStateOf<YearMonth?>(null) }
    var soloDaAssociare by rememberSaveable { mutableStateOf(false) }
    var mostraInfo by remember { mutableStateOf(false) }

    val valuta = tipo?.valuta ?: "EUR"
    fun nelPeriodo(m: YearMonth) = (da == null || m >= da!!) && (a == null || m <= a!!)
    val tuttiDelTipo = tipo?.let { stipendi.stipendiDi(it.id) }.orEmpty()
    val opPerId = remember(dati.operazioni) { dati.operazioni.associateBy { it.id } }
    // Accrediti associati (a qualunque stipendio): id operazione -> stipendio.
    val associati = stipendi.stipendi.filter { it.operazioneId != null && it.operazioneId in opPerId }.associateBy { it.operazioneId!! }
    fun associato(s: Stipendio) = s.operazioneId?.takeIf { it in opPerId }
    val righeStipendi = tuttiDelTipo.filter { nelPeriodo(YearMonth.parse(it.mese)) && (!soloDaAssociare || associato(it) == null) }
    val accrediti = tipo?.contoValutaId?.let { cv ->
        dati.operazioni.filter { op ->
            op.contoValutaId == cv && ((op.importoCent > 0 && op.voceId in vociSpesaRiscontro) || op.id in associati) &&
                nelPeriodo(YearMonth.from(LocalDate.ofEpochDay(op.data))) && (!soloDaAssociare || op.id !in associati)
        }
    }.orEmpty()
    val valori = righeStipendi.associate { s ->
        s.id to voceId?.let { v ->
            valoriStipendio(voci, stipendi.importiPerStipendio[s.id].orEmpty(), stipendi.percentualiPerStipendio[s.id].orEmpty(), stipendi.sostituzioniPerStipendio[s.id].orEmpty())[v]
        }
    }
    val mesi = (righeStipendi.map { YearMonth.parse(it.mese) } + accrediti.map { YearMonth.from(LocalDate.ofEpochDay(it.data)) })
        .distinct().sortedDescending()
    val visibili = (righeStipendi.map { "s${it.id}" } + accrediti.map { "o${it.id}" }).toHashSet()

    // Posizioni (coordinate della finestra) delle carte e del contenuto scorrevole, per le linee.
    val posizioni = remember { mutableStateMapOf<String, Rect>() }
    var origineContenuto by remember { mutableStateOf(Offset.Zero) }
    var areaVisibile by remember { mutableStateOf(Rect.Zero) }
    val scorrimento = rememberScrollState()
    var trascinata by remember { mutableStateOf<ElementoRiscontro?>(null) }
    var dito by remember { mutableStateOf(Offset.Zero) }
    val elementi = righeStipendi.map { ElementoRiscontro.DiStipendio(it) } + accrediti.map { ElementoRiscontro.DiOperazione(it) }
    fun libero(e: ElementoRiscontro) = when (e) {
        is ElementoRiscontro.DiStipendio -> associato(e.stipendio) == null
        is ElementoRiscontro.DiOperazione -> e.operazione.id !in associati
    }
    // Bersaglio: una carta libera dell'altra colonna sotto il dito.
    val bersaglio = trascinata?.let { t ->
        elementi.firstOrNull { e -> e::class != t::class && libero(e) && posizioni[e.chiave]?.contains(dito) == true }
    }
    var daAssociare by remember { mutableStateOf<Pair<Stipendio, Operazione>?>(null) }
    var daScollegare by remember { mutableStateOf<Pair<Stipendio, Operazione>?>(null) }

    fun coerenti(s: Stipendio, op: Operazione) = valori[s.id] == op.importoCent
    /** Linee tra stipendi e accrediti associati, entrambi visibili, in coordinate del contenuto. */
    fun linee(): List<Triple<Stipendio, Operazione, Pair<Offset, Offset>>> = righeStipendi.mapNotNull { s ->
        val op = associato(s)?.let { opPerId[it] } ?: return@mapNotNull null
        if ("o${op.id}" !in visibili) return@mapNotNull null
        val r1 = posizioni["s${s.id}"]?.translate(-origineContenuto) ?: return@mapNotNull null
        val r2 = posizioni["o${op.id}"]?.translate(-origineContenuto) ?: return@mapNotNull null
        Triple(s, op, r1.centerRight to r2.centerLeft)
    }

    val colorePrimario = MaterialTheme.colorScheme.primary
    val coloreErrore = MaterialTheme.colorScheme.error
    val coloreTrascinamento = MaterialTheme.colorScheme.tertiary
    ScorrimentoAutomatico(trascinata != null, { dito }, { areaVisibile }, scorrimento)

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().paddingBarreDialog().padding(vertical = 12.dp)) {
                Column(modifier = Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Riscontro stipendi", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { mostraInfo = true }) { Icon(Icons.Filled.Info, contentDescription = "Informazioni") }
                        IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                    }
                    if (tipo == null) {
                        Text("Nessuno stipendio in anagrafica (menu ⋮ › Anagrafica stipendi).")
                        return@Column
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        tipi.forEach { t -> FilterChip(selected = t.id == tipo.id, onClick = { tipoId = t.id }, label = { Text(t.nome) }) }
                    }
                    val nomiSpese = dati.voci.filter { it.id in vociSpesaRiscontro }.joinToString(", ") { it.descrizione }
                    Text(
                        "Confronto: " + (voci.firstOrNull { it.id == voceId }?.descrizione ?: "—") +
                            (if (nomiSpese.isNotEmpty()) " · accrediti: $nomiSpese" else ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (voceId == null) Text(
                        "Indica la voce da confrontare nell'anagrafica stipendi (Voce per il riscontro stipendi).",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                    )
                    if (vociSpesaRiscontro.isEmpty()) Text(
                        "Nessuna entrata usata nel riscontro stipendi: indicala nell'anagrafica spese.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                    )
                    Row {
                        SceltaMese("Dal", da ?: tuttiDelTipo.minOfOrNull { YearMonth.parse(it.mese) } ?: oggi, { da = it; if (a != null && a!! < it) a = it }, Modifier.weight(1f))
                        SceltaMese("Al", a ?: oggi, { a = it; if (da != null && da!! > it) da = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(selected = da == oggi.minusMonths(11) && a == null, onClick = { da = oggi.minusMonths(11); a = null }, label = { Text("12 mesi") })
                        FilterChip(selected = da == null && a == null, onClick = { da = null; a = null }, label = { Text("Tutto") })
                        FilterChip(selected = soloDaAssociare, onClick = { soloDaAssociare = !soloDaAssociare }, label = { Text("Da associare") })
                    }
                    if (tipo.contoValutaId == null) {
                        Text(
                            "Lo stipendio non ha un conto di accredito: indicalo nell'anagrafica stipendi.",
                            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                // Intestazione delle colonne.
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp)) {
                    Text("Mese", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.width(52.dp))
                    Text("Stipendi registrati", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).padding(horizontal = 6.dp))
                    Text(
                        "Accrediti" + (tipo?.contoValutaId?.let { " · ${dati.etichetta(it)}" } ?: ""),
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 2,
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
                    )
                }
                HorizontalDivider()
                if (mesi.isEmpty()) Text("Nessuno stipendio né accredito nel periodo.", modifier = Modifier.padding(16.dp))
                Box(modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { areaVisibile = Rect(it.positionInRoot(), it.size.toSize()) }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scorrimento, enabled = trascinata == null)
                            .onGloballyPositioned { origineContenuto = it.positionInRoot() }
                            // Tocco su una linea: proposta di scollegare.
                            .pointerInput(righeStipendi, accrediti) {
                                detectTapGestures { punto ->
                                    val soglia = 16.dp.toPx()
                                    linee()
                                        .map { (s, op, seg) -> Triple(s, op, distanzaDaSegmentoRiscontro(punto, seg.first, seg.second)) }
                                        .filter { it.third <= soglia }
                                        .minByOrNull { it.third }
                                        ?.let { (s, op, _) -> daScollegare = s to op }
                                }
                            }
                            .drawWithContent {
                                drawContent()
                                for ((s, op, segmento) in linee()) {
                                    drawLine(
                                        color = if (coerenti(s, op)) colorePrimario else coloreErrore,
                                        start = segmento.first, end = segmento.second, strokeWidth = 2.dp.toPx()
                                    )
                                }
                                trascinata?.let { t ->
                                    val r = posizioni[t.chiave]?.translate(-origineContenuto) ?: return@let
                                    val punto = dito - origineContenuto
                                    drawLine(
                                        color = coloreTrascinamento, start = r.center, end = punto, strokeWidth = 3.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                                    )
                                    drawCircle(coloreTrascinamento, radius = 8.dp.toPx(), center = punto)
                                }
                            }
                    ) {
                        mesi.forEach { m ->
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.Top) {
                                Column(modifier = Modifier.width(52.dp).padding(top = 4.dp)) {
                                    Text(formattaMese(m).substringBefore(' ').take(3), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Text(m.year.toString(), style = MaterialTheme.typography.labelSmall)
                                }
                                val colonne = listOf(
                                    righeStipendi.filter { YearMonth.parse(it.mese) == m }.sortedBy { it.id }.map { ElementoRiscontro.DiStipendio(it) },
                                    accrediti.filter { YearMonth.from(LocalDate.ofEpochDay(it.data)) == m }.sortedBy { it.data }.map { ElementoRiscontro.DiOperazione(it) }
                                )
                                colonne.forEach { lista ->
                                    Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        lista.forEach { e ->
                                            val (titolo, sotto) = when (e) {
                                                is ElementoRiscontro.DiStipendio ->
                                                    (valori[e.stipendio.id]?.let { formattaCent(it, valuta) } ?: "—") to
                                                        ("Stipendio" + (numeroNelMese(e.stipendio, tuttiDelTipo)?.let { " ($it)" } ?: "") +
                                                            (associato(e.stipendio)?.let { id -> opPerId[id]?.takeIf { "o$id" !in visibili }?.let { " ↗ ${formattaData(it.data)}" } } ?: ""))
                                                is ElementoRiscontro.DiOperazione ->
                                                    formattaCent(e.operazione.importoCent, dati.contiValutaPerId[e.operazione.contoValutaId]?.valuta ?: "EUR") to
                                                        (formattaData(e.operazione.data) + (e.operazione.note?.let { " · $it" } ?: ""))
                                            }
                                            CartaRiscontro(
                                                titolo = titolo, sottotitolo = sotto,
                                                associata = !libero(e),
                                                evidenziata = bersaglio?.chiave == e.chiave,
                                                inTrascinamento = trascinata?.chiave == e.chiave,
                                                onPosizione = { posizioni[e.chiave] = it },
                                                onInizioTrascinamento = { locale ->
                                                    val r = posizioni[e.chiave] ?: return@CartaRiscontro
                                                    trascinata = e
                                                    dito = r.topLeft + locale
                                                },
                                                onTrascinamento = { dito += it },
                                                onFineTrascinamento = {
                                                    val t = trascinata
                                                    val b = bersaglio
                                                    trascinata = null
                                                    if (t != null && b != null) {
                                                        val s = (t as? ElementoRiscontro.DiStipendio ?: b as? ElementoRiscontro.DiStipendio)?.stipendio
                                                        val op = (t as? ElementoRiscontro.DiOperazione ?: b as? ElementoRiscontro.DiOperazione)?.operazione
                                                        if (s != null && op != null) daAssociare = s to op
                                                    }
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
    }

    fun descrizioneStipendio(s: Stipendio) = "Stipendio di ${formattaMese(YearMonth.parse(s.mese))}" +
        (numeroNelMese(s, tuttiDelTipo)?.let { " ($it)" } ?: "") + " · " + (valori[s.id]?.let { formattaCent(it, valuta) } ?: "—")
    fun descrizioneAccredito(op: Operazione) = "Accredito del ${formattaData(op.data)} · " +
        formattaCent(op.importoCent, dati.contiValutaPerId[op.contoValutaId]?.valuta ?: "EUR")

    daAssociare?.let { (s, op) ->
        AlertDialog(
            onDismissRequest = { daAssociare = null },
            title = { Text("Associa stipendio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(descrizioneStipendio(s), fontWeight = FontWeight.Bold)
                    Text(descrizioneAccredito(op), fontWeight = FontWeight.Bold)
                    op.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                    if (!coerenti(s, op)) Text("Attenzione: gli importi sono diversi.", color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = { vm.associaStipendio(s, op.id); daAssociare = null }) { Text("Associa") } },
            dismissButton = { TextButton(onClick = { daAssociare = null }) { Text("Annulla") } }
        )
    }
    daScollegare?.let { (s, op) ->
        AlertDialog(
            onDismissRequest = { daScollegare = null },
            title = { Text("Elimina associazione") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(descrizioneStipendio(s), fontWeight = FontWeight.Bold)
                    Text(descrizioneAccredito(op), fontWeight = FontWeight.Bold)
                    Text("Stipendio e accredito restano, ma non più associati.")
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.associaStipendio(s, null); daScollegare = null }) { Text("Scollega", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { daScollegare = null }) { Text("Annulla") } }
        )
    }
    if (mostraInfo) {
        AlertDialog(
            onDismissRequest = { mostraInfo = false },
            icon = { Icon(Icons.Filled.Info, contentDescription = null) },
            title = { Text("Riscontro stipendi") },
            text = {
                Text(
                    "A sinistra gli stipendi registrati con il valore della voce indicata nell'anagrafica stipendi (es. il netto), a destra le entrate " +
                        "del conto di accredito con le voci di spesa usate nel riscontro stipendi (anagrafica spese), mese per mese. Tieni premuta una riga non associata e trascinala su quella corrispondente " +
                        "dell'altra colonna per associarle; la linea è rossa se gli importi sono diversi. Tocca una linea per scollegarle. " +
                        "↗ indica un accredito associato fuori dal periodo mostrato."
                )
            },
            confirmButton = { TextButton(onClick = { mostraInfo = false }) { Text("OK") } }
        )
    }
}

/** Una carta del riscontro: importo e descrizione; le non associate sono evidenziate. */
@Composable
private fun CartaRiscontro(
    titolo: String,
    sottotitolo: String,
    associata: Boolean,
    evidenziata: Boolean,
    inTrascinamento: Boolean,
    onPosizione: (Rect) -> Unit,
    onInizioTrascinamento: (Offset) -> Unit,
    onTrascinamento: (Offset) -> Unit,
    onFineTrascinamento: () -> Unit,
    onAnnullaTrascinamento: () -> Unit
) {
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
            associata -> colori.surfaceVariant
            else -> colori.tertiaryContainer
        },
        border = when {
            evidenziata || inTrascinamento -> BorderStroke(2.dp, colori.tertiary)
            !associata -> BorderStroke(1.dp, colori.tertiary)
            else -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onPosizione(Rect(it.positionInRoot(), it.size.toSize())) }
            .pointerInput(associata) {
                // Solo le carte non associate si trascinano (pressione lunga, poi trascina).
                if (!associata) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { inizio(it) },
                        onDrag = { change, delta -> change.consume(); trascina(delta) },
                        onDragEnd = { fine() },
                        onDragCancel = { annulla() }
                    )
                }
            }
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)) {
            Text(titolo, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sottotitolo, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Distanza del punto [p] dal segmento [a]-[b]. */
private fun distanzaDaSegmentoRiscontro(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val lunghezza2 = ab.x * ab.x + ab.y * ab.y
    if (lunghezza2 == 0f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / lunghezza2).coerceIn(0f, 1f)
    return (p - Offset(a.x + ab.x * t, a.y + ab.y * t)).getDistance()
}
