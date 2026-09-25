package com.desideri.familybalance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desideri.familybalance.SpeseViewModel
import com.desideri.familybalance.logica.centInTesto
import com.desideri.familybalance.logica.testoInCent

@Composable
fun ImpostazioniScreen(vm: SpeseViewModel, onIndietro: () -> Unit) {
    val impostazioni by vm.impostazioni.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf(centInTesto(impostazioni.targetRisparmioCent)) }
    var cambio by remember { mutableStateOf(impostazioni.cambioChfEur.toString().replace('.', ',')) }
    var errore by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { BarraIndietro("Impostazioni", onIndietro) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("Target di risparmio mensile (EUR)") },
                supportingText = { Text("Risparmio atteso ogni mese (entrate − spese correnti): usato nel Bilancio per il delta e le previsioni.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = cambio,
                onValueChange = { cambio = it },
                label = { Text("Cambio: 1 CHF = … EUR") },
                supportingText = { Text("Per sommare i conti in CHF a quelli in EUR nel saldo totale e nel bilancio (1 = nessuna conversione, come nell'Excel).") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            errore?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val targetCent = testoInCent(target)
                    val tasso = cambio.trim().replace(',', '.').toDoubleOrNull()
                    errore = when {
                        targetCent == null -> "Target non valido"
                        tasso == null || tasso <= 0 -> "Cambio non valido"
                        else -> {
                            vm.salvaImpostazioni(impostazioni.copy(targetRisparmioCent = targetCent, cambioChfEur = tasso))
                            onIndietro()
                            null
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End)
            ) { Text("Salva") }
        }
    }
}
