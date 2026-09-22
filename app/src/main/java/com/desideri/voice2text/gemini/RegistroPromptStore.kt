package com.desideri.voice2text.gemini

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private const val NOME_FILE_REGISTRO = "registro_prompt.json"
private val RETENZIONE_MS = TimeUnit.DAYS.toMillis(7)

/** Tipo di chiamata a Gemini registrata nel log. */
enum class TipoChiamataGemini(val etichetta: String) {
    TRASCRIZIONE("Trascrizione"),
    RISCRITTURA("Riscrittura")
}

data class VoceRegistro(
    val timestampMillis: Long,
    val tipo: TipoChiamataGemini,
    val prompt: String,
    val risultato: String,
    val errore: Boolean
)

/**
 * Registro locale (su file nella cache dell'app, nessun server) delle chiamate a Gemini: prompt
 * inviato e risultato ottenuto (o l'errore). Le voci piu' vecchie di 7 giorni vengono scartate
 * automaticamente a ogni lettura/scrittura, cosi' il file non cresce indefinitamente.
 */
class RegistroPromptStore(private val context: Context) {

    private val file: File get() = File(context.filesDir, NOME_FILE_REGISTRO)

    @Synchronized
    fun registra(tipo: TipoChiamataGemini, prompt: String, risultato: String, errore: Boolean) {
        val voci = scarta(leggiVoci()).toMutableList()
        voci.add(VoceRegistro(System.currentTimeMillis(), tipo, prompt, risultato, errore))
        scriviVoci(voci)
    }

    /** Voci non scadute, piu' recenti prima. La pulizia delle voci scadute viene anche persistita. */
    @Synchronized
    fun leggi(): List<VoceRegistro> {
        val voci = scarta(leggiVoci())
        scriviVoci(voci)
        return voci.sortedByDescending { it.timestampMillis }
    }

    @Synchronized
    fun svuota() {
        file.delete()
    }

    private fun scarta(voci: List<VoceRegistro>): List<VoceRegistro> {
        val sogliaMillis = System.currentTimeMillis() - RETENZIONE_MS
        return voci.filter { it.timestampMillis >= sogliaMillis }
    }

    private fun leggiVoci(): List<VoceRegistro> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { indice ->
                val obj = array.getJSONObject(indice)
                VoceRegistro(
                    timestampMillis = obj.getLong("timestamp"),
                    tipo = TipoChiamataGemini.valueOf(obj.getString("tipo")),
                    prompt = obj.getString("prompt"),
                    risultato = obj.getString("risultato"),
                    errore = obj.optBoolean("errore", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun scriviVoci(voci: List<VoceRegistro>) {
        val array = JSONArray()
        voci.forEach { voce ->
            array.put(
                JSONObject()
                    .put("timestamp", voce.timestampMillis)
                    .put("tipo", voce.tipo.name)
                    .put("prompt", voce.prompt)
                    .put("risultato", voce.risultato)
                    .put("errore", voce.errore)
            )
        }
        file.writeText(array.toString())
    }
}
