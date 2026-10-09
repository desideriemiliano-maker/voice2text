package com.desideri.familybalance.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.desideri.familybalance.logica.formattaImporto
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

/** Una fetta della torta: nome, valore (positivo, EUR) e colore. */
data class FettaTorta(val nome: String, val valore: Double, val colore: Color)

/**
 * Grafico a torta della ripartizione di un totale (es. le spese per voce), dalla fetta più grande;
 * sotto la legenda con importo e percentuale. Toccando una fetta o una riga della legenda la si
 * evidenzia. Le voci con valore nullo o negativo (rimborsi) non entrano nella torta.
 */
@Composable
fun GraficoTortaDialog(titolo: String, nota: String, fette: List<FettaTorta>, onChiudi: () -> Unit) {
    val ordinate = remember(fette) { fette.filter { it.valore > 0.005 }.sortedByDescending { it.valore } }
    val totale = ordinate.sumOf { it.valore }
    var scelta by remember(fette) { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onChiudi,
        title = { Text(titolo) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(nota, style = MaterialTheme.typography.bodySmall)
                if (ordinate.isEmpty()) Text("Nessuna spesa da ripartire.", modifier = Modifier.padding(vertical = 12.dp))
                if (ordinate.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(240.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Canvas(
                            modifier = Modifier.size(220.dp).pointerInput(ordinate) {
                                detectTapGestures { p ->
                                    val c = Offset(size.width / 2f, size.height / 2f)
                                    val d = p - c
                                    if (sqrt(d.x * d.x + d.y * d.y) > min(size.width, size.height) / 2f) {
                                        scelta = null
                                        return@detectTapGestures
                                    }
                                    // Angolo dalle ore 12 in senso orario, come le fette.
                                    val gradi = ((Math.toDegrees(atan2(d.y, d.x).toDouble()) + 90 + 360) % 360)
                                    var inizio = 0.0
                                    ordinate.forEachIndexed { i, f ->
                                        val ampiezza = f.valore / totale * 360
                                        if (gradi >= inizio && gradi < inizio + ampiezza) {
                                            scelta = if (scelta == i) null else i
                                            return@detectTapGestures
                                        }
                                        inizio += ampiezza
                                    }
                                }
                            }
                        ) {
                            val lato = min(size.width, size.height)
                            val margine = lato * 0.06f
                            var inizio = -90f
                            ordinate.forEachIndexed { i, f ->
                                val ampiezza = (f.valore / totale * 360).toFloat()
                                // La fetta scelta esce un po' dal cerchio; le altre si attenuano.
                                val esce = if (scelta == i) margine else 0f
                                val meta = Math.toRadians((inizio + ampiezza / 2).toDouble())
                                val spost = Offset((kotlin.math.cos(meta) * esce).toFloat(), (kotlin.math.sin(meta) * esce).toFloat())
                                drawArc(
                                    color = if (scelta == null || scelta == i) f.colore else f.colore.copy(alpha = 0.35f),
                                    startAngle = inizio,
                                    sweepAngle = ampiezza,
                                    useCenter = true,
                                    topLeft = Offset(margine, margine) + spost,
                                    size = Size(lato - 2 * margine, lato - 2 * margine)
                                )
                                inizio += ampiezza
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text("Totale", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(formattaImporto(totale), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    ordinate.forEachIndexed { i, f ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                                .background(if (scelta == i) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                                .clickable { scelta = if (scelta == i) null else i }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(modifier = Modifier.size(12.dp).background(f.colore, CircleShape))
                            Text(f.nome, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            Text(
                                "${formattaImporto(f.valore)} · ${"%.1f".format(f.valore / totale * 100).replace('.', ',')}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (scelta == i) FontWeight.Bold else null
                            )
                        }
                    }
                }
                val escluse = fette.count { it.valore < -0.005 }
                if (escluse > 0) {
                    Text("$escluse voci con rimborsi superiori alle spese non sono nella torta.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = onChiudi) { Text("Chiudi") } }
    )
}
