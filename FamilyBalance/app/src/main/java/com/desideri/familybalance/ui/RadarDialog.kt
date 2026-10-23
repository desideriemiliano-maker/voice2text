package com.desideri.familybalance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaMese
import java.time.YearMonth
import kotlin.math.roundToLong

/**
 * Radar animato a schermo intero di valori mensili in EUR: un asse per ciascuno degli [assi] e un
 * poligono per ognuno dei [mesi] ([valori] per mese e asse; null = nessun valore).
 */
@Composable
fun RadarMensileDialog(
    titolo: String,
    nota: String,
    assi: List<String>,
    mesi: List<YearMonth>,
    valori: List<List<Double?>>,
    pausaMs: Int,
    onChiudi: () -> Unit
) {
    Dialog(onDismissRequest = onChiudi, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(modifier = Modifier.fillMaxSize(), tonalElevation = 4.dp) {
            Column(modifier = Modifier.fillMaxSize().paddingBarreDialog().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(titolo, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onChiudi) { Icon(Icons.Filled.Close, contentDescription = "Chiudi") }
                }
                Text(nota, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
                if (assi.isEmpty() || mesi.isEmpty()) {
                    Text("Nessun valore nel periodo.", modifier = Modifier.padding(16.dp))
                } else {
                    RadarAnimato(
                        assi = assi,
                        etichette = mesi.map { formattaMese(it) },
                        valori = valori.map { riga -> riga.map { v -> v?.let { (it * 100).roundToLong() } } },
                        formatta = { formattaCent(it) },
                        pausaMs = pausaMs,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
            }
        }
    }
}
