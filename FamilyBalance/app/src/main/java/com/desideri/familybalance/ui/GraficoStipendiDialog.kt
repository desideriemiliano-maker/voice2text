package com.desideri.familybalance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import android.content.pm.ActivityInfo
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.desideri.familybalance.data.Stipendio
import com.desideri.familybalance.data.VoceStipendio
import com.desideri.familybalance.data.numeroNelMese
import com.desideri.familybalance.logica.PuntoGrafico
import com.desideri.familybalance.logica.TipoGrafico
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaMese
import com.desideri.familybalance.logica.formattaMeseBreve
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import java.time.YearMonth
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Grafico degli stipendi di una tipologia, con voci e periodo selezionabili: andamento nel tempo
 * (linea, area, istogramma…) o radar con una dimensione per voce, animato mese per mese.
 * [valoriPerStipendio]: per stipendio, i valori (centesimi) di tutte le voci, anche calcolate.
 */
@Composable
fun GraficoStipendiDialog(
    titolo: String,
    voci: List<VoceStipendio>,
    stipendi: List<Stipendio>,
    valoriPerStipendio: Map<Long, Map<Long, Long>>,
    valuta: String,
    annoIniziale: Int,
    /** Sosta del radar animato su ogni mese raggiunto (ms, Impostazioni). */
    pausaRadarMs: Int = 1000,
    onChiudi: () -> Unit
) {
    val mesiDisponibili = stipendi.map { YearMonth.parse(it.mese) }.sorted()
    // Periodo predefinito: l'anno mostrato nella tabella, se ha stipendi, altrimenti tutto.
    val nellAnno = mesiDisponibili.filter { it.year == annoIniziale }
    var da by remember { mutableStateOf((nellAnno.ifEmpty { mesiDisponibili }).firstOrNull() ?: YearMonth.now()) }
    var a by remember { mutableStateOf((nellAnno.ifEmpty { mesiDisponibili }).lastOrNull() ?: YearMonth.now()) }
    var scelte by remember { mutableStateOf(voci.filter { v -> stipendi.any { valoriPerStipendio[it.id]?.get(v.id) != null } }.take(6)) }
    var radar by rememberSaveable { mutableStateOf(false) }
    var tipo by rememberSaveable { mutableStateOf(TipoGrafico.LINEA) }
    var tendenza by rememberSaveable { mutableStateOf(false) }

    val vociScelte = voci.filter { it in scelte }
    val righe = stipendi.filter { YearMonth.parse(it.mese) in da..a }.sortedWith(compareBy<Stipendio> { it.mese }.thenBy { it.id })
    val serie = vociScelte.mapIndexed { i, v -> SerieGrafico(v.descrizione, coloreSerie(i, null)) }

    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().paddingBarreDialog().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(titolo, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                MenuMultiplo("Voci", voci, scelte, { it.descrizione }, onCambia = { scelte = it }, modifier = Modifier.fillMaxWidth(), conTutti = true)
                Row {
                    SceltaMese("Dal", da, { da = it; if (a < it) a = it }, Modifier.weight(1f))
                    SceltaMese("Al", a, { a = it; if (da > it) da = it }, Modifier.weight(1f))
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    FilterChip(selected = radar, onClick = { radar = true }, label = { Text("Radar") })
                    TipoGrafico.entries.forEach { t -> FilterChip(selected = !radar && tipo == t, onClick = { radar = false; tipo = t }, label = { Text(t.etichetta) }) }
                }
                if (!radar) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tendenza", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    Switch(checked = tendenza, onCheckedChange = { tendenza = it })
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                when {
                    vociScelte.isEmpty() || righe.isEmpty() ->
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text("Scegli almeno una voce e un periodo con stipendi.", style = MaterialTheme.typography.bodyMedium)
                        }
                    radar -> RadarStipendi(vociScelte, righe, valoriPerStipendio, valuta, pausaRadarMs, Modifier.fillMaxWidth().weight(1f))
                    else -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp)
                        ) {
                            serie.forEach { s ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(12.dp).background(s.colore, CircleShape))
                                    Spacer(Modifier.width(4.dp))
                                    Text(s.nome, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        val punti = righe.map { s ->
                            PuntoGrafico(
                                formattaMeseBreve(YearMonth.parse(s.mese)) + (numeroNelMese(s, stipendi)?.let { " ($it)" } ?: ""),
                                vociScelte.map { v -> valoriPerStipendio[s.id]?.get(v.id)?.let { it / 100.0 } }
                            )
                        }
                        GraficoSerie(punti, serie, tipo, tendenza, valuta, Modifier.fillMaxWidth().weight(1f))
                    }
                }
            }
        }
    }
}

private const val VELOCITA_MINIMA_RADAR = 0.25f
private const val VELOCITA_MASSIMA_RADAR = 8f
private val COLORE_RADAR = Color(0xFF3B82F6)

/**
 * Radar animato (come in WorkoutAnalyzer): un asse per voce, normalizzato sul minimo e il massimo
 * della voce nel periodo (il minimo al centro, il massimo sul bordo). L'animazione parte dal primo
 * mese e il poligono si trasforma da un mese al successivo; il mese indicato e i valori accanto
 * agli assi passano al successivo a metà transizione. Controlli: decelera, play/pausa, accelera,
 * ricomincia, slider per scegliere il mese e schermo pieno in orizzontale.
 */
@Composable
private fun RadarStipendi(
    voci: List<VoceStipendio>,
    righe: List<Stipendio>,
    valoriPerStipendio: Map<Long, Map<Long, Long>>,
    valuta: String,
    pausaMs: Int,
    modifier: Modifier
) = RadarAnimato(
    assi = voci.map { it.descrizione },
    etichette = righe.map { s -> formattaMese(YearMonth.parse(s.mese)) + (numeroNelMese(s, righe)?.let { " ($it)" } ?: "") },
    valori = righe.map { s -> voci.map { v -> valoriPerStipendio[s.id]?.get(v.id) } },
    formatta = { formattaCent(it, valuta) },
    pausaMs = pausaMs,
    modifier = modifier
)

/**
 * Radar animato generico: un asse per ciascuno degli [assi], un poligono per ogni riga ([etichette]
 * e [valori], centesimi; null = mancante, al centro), con [formatta] per i valori accanto agli assi.
 */
@Composable
internal fun RadarAnimato(
    assi: List<String>,
    etichette: List<String>,
    valori: List<List<Long?>>,
    formatta: (Long) -> String,
    pausaMs: Int,
    modifier: Modifier
) {
    val frazioniPerRiga = remember(assi, valori) {
        val minMax = assi.indices.map { k ->
            valori.mapNotNull { it.getOrNull(k) }.takeIf { it.isNotEmpty() }?.let { it.min() to it.max() }
        }
        valori.map { riga ->
            assi.indices.map { k ->
                val valore = riga.getOrNull(k)
                val mm = minMax[k]
                if (valore == null || mm == null) 0f
                else if (mm.first == mm.second) 1f
                else ((valore - mm.first).toFloat() / (mm.second - mm.first)).coerceIn(0f, 1f)
            }
        }
    }
    val transizioni = (etichette.size - 1).coerceAtLeast(1)
    val progresso = remember { Animatable(0f) }
    var inPausa by remember { mutableStateOf(false) }
    var animazione by remember { mutableStateOf<Job?>(null) }
    var velocita by remember { mutableStateOf(1f) }
    var schermoIntero by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Un secondo per mese a velocità 1, con una sosta di [pausaMs] su ogni mese raggiunto.
    fun avvia(daZero: Boolean = false) {
        if (etichette.size < 2) return
        animazione?.cancel()
        animazione = scope.launch {
            if (daZero) progresso.snapTo(0f)
            val passoMs = 1000 / velocita
            while (true) {
                val corrente = progresso.value * transizioni
                if (abs(corrente - corrente.roundToInt()) < 0.001f && pausaMs > 0) delay(pausaMs.toLong())
                val prossimo = floor(corrente + 0.001f).toInt() + 1
                if (prossimo > transizioni) break
                progresso.animateTo(
                    prossimo.toFloat() / transizioni,
                    tween(((prossimo - corrente) * passoMs).toInt().coerceAtLeast(1), easing = LinearEasing)
                )
            }
            inPausa = true
        }
    }
    // Voci o periodo cambiati: si riparte dal primo mese.
    LaunchedEffect(frazioniPerRiga) {
        inPausa = false
        avvia(daZero = true)
    }

    val continuo = progresso.value * transizioni
    val base = continuo.toInt().coerceIn(0, (etichette.size - 2).coerceAtLeast(0))
    val frazione = (continuo - base).coerceIn(0f, 1f)
    val vicino = continuo.roundToInt().coerceIn(0, etichette.lastIndex)
    val frazioniCorrenti = if (etichette.size < 2) frazioniPerRiga.first()
    else frazioniPerRiga[base].indices.map { k -> frazioniPerRiga[base][k] + (frazioniPerRiga[base + 1][k] - frazioniPerRiga[base][k]) * frazione }
    val valoriCorrenti = valori[vicino].map { it?.let(formatta) }

    val coloreEtichette = MaterialTheme.colorScheme.onSurfaceVariant
    val coloreLinee = MaterialTheme.colorScheme.outlineVariant
    val misuraTesto = rememberTextMeasurer()

    @Composable
    fun Contenuto(modificatore: Modifier) {
        Column(modificatore) {
            Canvas(Modifier.fillMaxWidth().weight(1f)) {
                val centro = Offset(size.width / 2, size.height / 2)
                val raggio = minOf(size.width, size.height) / 2 * 0.62f
                val numeroAssi = assi.size
                fun angolo(k: Int) = -PI / 2 + 2 * PI * k / numeroAssi
                fun vertice(k: Int, r: Float) = Offset(
                    centro.x + (raggio * r * cos(angolo(k))).toFloat(),
                    centro.y + (raggio * r * sin(angolo(k))).toFloat()
                )
                // Griglia: anelli al 25/50/75/100% e gli assi dal centro.
                listOf(0.25f, 0.5f, 0.75f, 1f).forEach { r ->
                    val anello = Path()
                    (0 until numeroAssi).forEach { k -> val q = vertice(k, r); if (k == 0) anello.moveTo(q.x, q.y) else anello.lineTo(q.x, q.y) }
                    anello.close()
                    drawPath(anello, coloreLinee, style = Stroke(1.5f))
                }
                (0 until numeroAssi).forEach { k -> drawLine(coloreLinee, centro, vertice(k, 1f), strokeWidth = 1.5f) }
                // Poligono dei valori, interpolato tra un mese e il successivo.
                if (numeroAssi >= 2) {
                    val dati = Path()
                    (0 until numeroAssi).forEach { k ->
                        val q = vertice(k, frazioniCorrenti.getOrElse(k) { 0f })
                        if (k == 0) dati.moveTo(q.x, q.y) else dati.lineTo(q.x, q.y)
                    }
                    dati.close()
                    drawPath(dati, COLORE_RADAR.copy(alpha = 0.35f))
                    drawPath(dati, COLORE_RADAR, style = Stroke(3f))
                }
                (0 until numeroAssi).forEach { k -> drawCircle(COLORE_RADAR, radius = 5f, center = vertice(k, frazioniCorrenti.getOrElse(k) { 0f })) }
                // Nome della voce appena fuori dall'anello esterno, con sotto il valore del mese indicato.
                (0 until numeroAssi).forEach { k ->
                    val nome = misuraTesto.measure(assi[k], style = TextStyle(fontSize = 11.sp, color = coloreEtichette))
                    val valore = valoriCorrenti[k]?.let {
                        misuraTesto.measure(it, style = TextStyle(fontSize = 11.sp, color = COLORE_RADAR, fontWeight = FontWeight.Bold))
                    }
                    val q = vertice(k, 1.2f)
                    val altezza = nome.size.height + (valore?.size?.height ?: 0)
                    val y = (q.y - altezza / 2f).coerceIn(0f, (size.height - altezza).coerceAtLeast(0f))
                    fun x(w: Int) = (q.x - w / 2f).coerceIn(0f, (size.width - w).coerceAtLeast(0f))
                    drawText(nome, topLeft = Offset(x(nome.size.width), y))
                    valore?.let { drawText(it, topLeft = Offset(x(it.size.width), y + nome.size.height)) }
                }
            }
            Text(
                etichette[vicino],
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
            if (etichette.size >= 2) {
                Slider(
                    value = progresso.value,
                    onValueChange = { v ->
                        animazione?.cancel()
                        inPausa = true
                        scope.launch { progresso.snapTo(v) }
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { velocita = (velocita / 2f).coerceAtLeast(VELOCITA_MINIMA_RADAR); if (!inPausa) avvia() }) {
                        Icon(Icons.Filled.FastRewind, contentDescription = "Decelera")
                    }
                    IconButton(onClick = {
                        if (inPausa) {
                            if (progresso.value >= 1f) avvia(daZero = true) else avvia()
                            inPausa = false
                        } else {
                            animazione?.cancel()
                            inPausa = true
                        }
                    }) {
                        Icon(if (inPausa) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = if (inPausa) "Riproduci" else "Pausa")
                    }
                    IconButton(onClick = { velocita = (velocita * 2f).coerceAtMost(VELOCITA_MASSIMA_RADAR); if (!inPausa) avvia() }) {
                        Icon(Icons.Filled.FastForward, contentDescription = "Accelera")
                    }
                    IconButton(onClick = { avvia(daZero = true); inPausa = false }) {
                        Icon(Icons.Filled.Replay, contentDescription = "Ricomincia")
                    }
                    Text("Velocità x" + velocita.toString().removeSuffix(".0").replace('.', ','), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { schermoIntero = !schermoIntero }) {
                        Icon(
                            if (schermoIntero) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                            contentDescription = if (schermoIntero) "Esci dallo schermo pieno" else "Schermo pieno"
                        )
                    }
                }
            }
            Text(
                "Ogni asse va dal valore più basso (al centro) al più alto (sul bordo) della voce nel periodo.",
                style = MaterialTheme.typography.bodySmall, color = coloreEtichette
            )
        }
    }

    Contenuto(modifier)
    if (schermoIntero) {
        Dialog(
            onDismissRequest = { schermoIntero = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            val vista = LocalView.current
            val activity = LocalContext.current.trovaActivity()
            DisposableEffect(Unit) {
                // Orizzontale e barre di sistema nascoste finché il radar è a schermo pieno.
                val orientamento = activity?.requestedOrientation
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                val finestra = (vista.parent as? DialogWindowProvider)?.window
                val controllo = finestra?.let { WindowCompat.getInsetsController(it, vista) }
                controllo?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controllo?.hide(WindowInsetsCompat.Type.systemBars())
                onDispose {
                    controllo?.show(WindowInsetsCompat.Type.systemBars())
                    if (activity != null && orientamento != null) activity.requestedOrientation = orientamento
                }
            }
            Surface(modifier = Modifier.fillMaxSize()) { Contenuto(Modifier.fillMaxSize().padding(8.dp)) }
        }
    }
}
