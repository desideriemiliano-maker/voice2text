package com.desideri.familybalance.ui.tema

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Verde = Color(0xFF2E7D32)

/** Colori per importi positivi/negativi, leggibili sia in tema chiaro sia scuro. */
object ColoriImporti {
    val positivo = Color(0xFF2E7D32)
    val negativo = Color(0xFFC62828)
    val positivoScuro = Color(0xFF81C784)
    val negativoScuro = Color(0xFFEF9A9A)
}

@Composable
fun coloreImporto(valore: Double): Color {
    val scuro = isSystemInDarkTheme()
    return when {
        valore > 0.004 -> if (scuro) ColoriImporti.positivoScuro else ColoriImporti.positivo
        valore < -0.004 -> if (scuro) ColoriImporti.negativoScuro else ColoriImporti.negativo
        else -> MaterialTheme.colorScheme.onSurface
    }
}

@Composable
fun TemaFamilyBalance(content: @Composable () -> Unit) {
    val scuro = isSystemInDarkTheme()
    val schema = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (scuro) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        scuro -> darkColorScheme(primary = Color(0xFF81C784))
        else -> lightColorScheme(primary = Verde)
    }
    MaterialTheme(colorScheme = schema, content = content)
}
