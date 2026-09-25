package com.desideri.spesefamiglia.importazione

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Celle di un foglio: riga (1-based) -> colonna (1-based) -> valore (String o Double). */
typealias Foglio = Map<Int, Map<Int, Any>>

fun Foglio.cella(riga: Int, colonna: Int): Any? = this[riga]?.get(colonna)

fun Foglio.testo(riga: Int, colonna: Int): String? = when (val v = cella(riga, colonna)) {
    is String -> v.trim().ifEmpty { null }
    is Double -> if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    else -> null
}

fun Foglio.numero(riga: Int, colonna: Int): Double? = when (val v = cella(riga, colonna)) {
    is Double -> v
    is String -> v.trim().replace(',', '.').toDoubleOrNull()
    else -> null
}

/**
 * Lettore minimale di file .xlsx (Office Open XML): uno zip di file XML. Legge solo i valori delle
 * celle (per le formule il valore calcolato salvato da Excel), senza stili né formule: quanto basta
 * per importare i fogli dell'Excel delle spese senza una libreria pesante come Apache POI.
 */
class LettoreXlsx(input: InputStream) {

    private val file: Map<String, ByteArray> = ZipInputStream(input).use { zip ->
        val contenuti = HashMap<String, ByteArray>()
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory) contenuti[entry.name.removePrefix("/")] = zip.readBytes()
        }
        contenuti
    }

    private val stringheCondivise: List<String> by lazy { leggiStringheCondivise() }

    /** Nome foglio -> percorso del suo XML nello zip. */
    private val percorsiFogli: Map<String, String> by lazy { leggiPercorsiFogli() }

    val nomiFogli: List<String> get() = percorsiFogli.keys.toList()

    /** Il foglio con quel nome (confronto senza distinzione maiuscole/spazi); null se non esiste. */
    fun foglio(nome: String): Foglio? {
        val chiave = percorsiFogli.keys.firstOrNull { it.trim().equals(nome.trim(), ignoreCase = true) } ?: return null
        val bytes = file[percorsiFogli.getValue(chiave)] ?: return null
        return leggiFoglio(bytes)
    }

    private fun parser(bytes: ByteArray): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(ByteArrayInputStream(bytes), "UTF-8")
    }

    private fun leggiPercorsiFogli(): Map<String, String> {
        val relazioni = HashMap<String, String>()
        file["xl/_rels/workbook.xml.rels"]?.let { bytes ->
            val p = parser(bytes)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                    val id = p.getAttributeValue(null, "Id") ?: continue
                    val target = p.getAttributeValue(null, "Target") ?: continue
                    relazioni[id] = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
                }
            }
        }
        val fogli = LinkedHashMap<String, String>()
        file["xl/workbook.xml"]?.let { bytes ->
            val p = parser(bytes)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                    val nome = p.getAttributeValue(null, "name") ?: continue
                    val rid = p.getAttributeValue(null, "r:id") ?: continue
                    relazioni[rid]?.let { fogli[nome] = it }
                }
            }
        }
        return fogli
    }

    private fun leggiStringheCondivise(): List<String> {
        val bytes = file["xl/sharedStrings.xml"] ?: return emptyList()
        val risultato = ArrayList<String>()
        val p = parser(bytes)
        val corrente = StringBuilder()
        var dentroT = false
        var dentroFonetica = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "si" -> corrente.setLength(0)
                    "t" -> dentroT = true
                    "rPh" -> dentroFonetica = true
                }
                XmlPullParser.TEXT -> if (dentroT && !dentroFonetica) corrente.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "si" -> risultato.add(corrente.toString())
                    "t" -> dentroT = false
                    "rPh" -> dentroFonetica = false
                }
            }
        }
        return risultato
    }

    private fun leggiFoglio(bytes: ByteArray): Foglio {
        val righe = HashMap<Int, HashMap<Int, Any>>()
        val p = parser(bytes)
        var rif: String? = null
        var tipo: String? = null
        var valore: StringBuilder? = null
        var dentroValore = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "c" -> {
                        rif = p.getAttributeValue(null, "r")
                        tipo = p.getAttributeValue(null, "t")
                        valore = null
                    }
                    "v", "t" -> {
                        dentroValore = true
                        if (valore == null) valore = StringBuilder()
                    }
                }
                XmlPullParser.TEXT -> if (dentroValore) valore?.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v", "t" -> dentroValore = false
                    "c" -> {
                        val r = rif
                        val testo = valore?.toString()
                        if (r != null && testo != null) {
                            val (riga, colonna) = coordinate(r)
                            val interpretato: Any? = when (tipo) {
                                "s" -> testo.trim().toIntOrNull()?.let { stringheCondivise.getOrNull(it) }
                                "str", "inlineStr" -> testo
                                "b" -> if (testo == "1") "TRUE" else "FALSE"
                                "e" -> null
                                else -> testo.toDoubleOrNull() ?: testo
                            }
                            if (interpretato != null && riga > 0 && colonna > 0) {
                                righe.getOrPut(riga) { HashMap() }[colonna] = interpretato
                            }
                        }
                        rif = null
                        tipo = null
                        valore = null
                    }
                }
            }
        }
        return righe
    }

    companion object {
        /** "AB12" -> (12, 28). */
        fun coordinate(riferimento: String): Pair<Int, Int> {
            var colonna = 0
            var i = 0
            while (i < riferimento.length && riferimento[i].isLetter()) {
                colonna = colonna * 26 + (riferimento[i].uppercaseChar() - 'A' + 1)
                i++
            }
            val riga = riferimento.substring(i).toIntOrNull() ?: 0
            return riga to colonna
        }
    }
}
