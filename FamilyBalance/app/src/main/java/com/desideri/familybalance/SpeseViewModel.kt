package com.desideri.familybalance

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.desideri.familybalance.backup.BackupDrive
import com.desideri.familybalance.backup.ContoBackup
import com.desideri.familybalance.backup.InfoBackup
import com.desideri.familybalance.backup.LetturaBackup
import com.desideri.familybalance.backup.RipristinoParziale
import com.desideri.familybalance.data.AppDatabase
import com.desideri.familybalance.data.Cambio
import com.desideri.familybalance.data.Associazione
import com.desideri.familybalance.data.Conto
import com.desideri.familybalance.data.ContoValuta
import com.desideri.familybalance.data.Impostazioni
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.data.PrevisioneRicorrente
import com.desideri.familybalance.data.Preferenze
import com.desideri.familybalance.data.Valute
import com.desideri.familybalance.data.Voce
import com.desideri.familybalance.importazione.AnalisiImport
import com.desideri.familybalance.importazione.ImportatoreExcel
import com.desideri.familybalance.logica.Spostamenti
import com.desideri.familybalance.estratto.AggiornamentoData
import com.desideri.familybalance.estratto.EstrattoGemini
import com.desideri.familybalance.estratto.ImportEstratto
import com.desideri.familybalance.estratto.Presenza
import com.desideri.familybalance.estratto.RegistroPromptStore
import com.desideri.familybalance.estratto.RigaEstratto
import com.desideri.familybalance.estratto.SceltaEstratto
import com.desideri.familybalance.estratto.TipoData
import com.desideri.familybalance.logica.Associazioni
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.Cambi
import com.desideri.familybalance.logica.meseInTesto
import com.desideri.familybalance.logica.testoInMese
import com.desideri.familybalance.logica.MeseRicorrenti
import com.desideri.familybalance.logica.RigaBilancio
import com.desideri.familybalance.logica.formattaCent
import com.desideri.familybalance.logica.formattaMese
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

data class DatiApp(
    val conti: List<Conto> = emptyList(),
    val contiValuta: List<ContoValuta> = emptyList(),
    val voci: List<Voce> = emptyList(),
    val operazioni: List<Operazione> = emptyList(),
    val caricati: Boolean = false
) {
    private val contiPerId by lazy { conti.associateBy { it.id } }
    val contiValutaPerId by lazy { contiValuta.associateBy { it.id } }
    val vociPerId by lazy { voci.associateBy { it.id } }

    /** Voci proponibili come tipo/sottotipo (le obsolete no). */
    val vociAttive: List<Voce> by lazy { voci.filterNot { it.obsoleta } }

    /** Conti/valuta ordinati per nome conto e valuta, come mostrati nelle liste. */
    val contiValutaOrdinati: List<ContoValuta> by lazy {
        contiValuta.sortedWith(compareBy({ contiPerId[it.contoId]?.nome?.lowercase() ?: "" }, { it.valuta }))
    }

    fun etichetta(contoValutaId: Long?): String {
        val cv = contoValutaId?.let { contiValutaPerId[it] } ?: return "Conto eliminato"
        return "${contiPerId[cv.contoId]?.nome ?: "?"} ${cv.valuta}"
    }
}

/** Estratto conto letto per il riscontro con le operazioni del conto/valuta [contoValutaId]. */
data class StatoRiscontroEstratto(
    val contoValutaId: Long,
    val contoId: Long,
    val movimenti: List<com.desideri.familybalance.estratto.MovimentoEstratto>,
    val avvisi: List<String>
)

data class StatoBackup(
    val inCorso: Boolean = false,
    /** Backup presenti su Drive, dal più recente. */
    val backup: List<InfoBackup> = emptyList(),
    val infoCaricata: Boolean = false,
    val errore: String? = null
)

/** Distanza massima in giorni per segnalare un movimento come possibile doppione. */
private const val GIORNI_DOPPIONE = 7L

class SpeseViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.get(application)
    private val dao = db.dao()
    private val preferenze = Preferenze(application)

    private val _impostazioni = MutableStateFlow(preferenze.carica())
    val impostazioni: StateFlow<Impostazioni> = _impostazioni.asStateFlow()

    private val _messaggi = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messaggi: SharedFlow<String> = _messaggi

    private val _importazioneInCorso = MutableStateFlow(false)
    val importazioneInCorso: StateFlow<Boolean> = _importazioneInCorso.asStateFlow()

    /** Incrementato per rileggere tutto dal database (trascinamento verso il basso nella sezione Conti). */
    private val ricarica = MutableStateFlow(0)
    private val _ricaricaInCorso = MutableStateFlow(false)
    val ricaricaInCorso: StateFlow<Boolean> = _ricaricaInCorso.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val dati: StateFlow<DatiApp> = ricarica.flatMapLatest {
        combine(dao.contiFlow(), dao.contiValutaFlow(), dao.vociFlow(), dao.operazioniFlow()) { conti, cv, voci, ops ->
            DatiApp(conti, cv, voci, ops, caricati = true)
        }
    }.onEach { _ricaricaInCorso.value = false }.stateIn(viewModelScope, SharingStarted.Eagerly, DatiApp())

    /** Rilegge dal database operazioni, conti e voci e dalle preferenze le impostazioni. */
    fun ricaricaDati() {
        _ricaricaInCorso.value = true
        _impostazioni.value = preferenze.carica()
        ricarica.value++
    }

    /** Saldo in centesimi (valuta propria) di ogni conto/valuta. */
    val saldi: StateFlow<Map<Long, Long>> = dati.map { Calcoli.saldiCent(it.contiValuta, it.operazioni) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Cambi CHF/EUR mensili inseriti a mano. */
    val cambiInseriti: StateFlow<List<Cambio>> = dao.cambiFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Cambio CHF/EUR di ogni mese: inserito, ricavato dagli spostamenti o quello attuale. */
    val cambi: StateFlow<Cambi> = combine(dati, _impostazioni, cambiInseriti) { d, imp, inseriti ->
        Cambi(
            inseriti.mapNotNull { c -> testoInMese(c.mese)?.let { it to c.chfEur } }.toMap(),
            Cambi.daSpostamenti(d.operazioni) { d.contiValutaPerId[it]?.valuta },
            imp.cambioChfEur,
            YearMonth.now()
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, Cambi.fisso(_impostazioni.value.cambioChfEur))

    /** Personalizzazioni delle scadenze ricorrenti (importo, data, spostamento). */
    val personalizzazioni: StateFlow<List<PrevisioneRicorrente>> =
        dao.previsioniFlow().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bilancio: StateFlow<List<RigaBilancio>> = combine(dati, _impostazioni, cambi, personalizzazioni) { d, imp, cambi, pers ->
        Calcoli.bilancio(d.contiValuta, d.voci, d.operazioni, cambi, imp.targetRisparmioCent / 100.0, YearMonth.now(), personalizzazioni = pers)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Periodo mostrato nella sezione Ricorrenti (di default da 12 mesi fa a 12 mesi avanti). */
    private val _periodoRicorrenti = MutableStateFlow(YearMonth.now().minusMonths(12) to YearMonth.now().plusMonths(12))
    val periodoRicorrenti: StateFlow<Pair<YearMonth, YearMonth>> = _periodoRicorrenti.asStateFlow()

    fun impostaPeriodoRicorrenti(da: YearMonth, a: YearMonth) {
        if (da <= a) _periodoRicorrenti.value = da to a
    }

    /** Spese ricorrenti mese per mese nel periodo scelto. */
    // Qui i CHF si convertono sempre al cambio delle Impostazioni (anche i mesi passati).
    val ricorrenti: StateFlow<List<MeseRicorrenti>> = combine(dati, _impostazioni, personalizzazioni, _periodoRicorrenti) { d, imp, pers, periodo ->
        val oggi = YearMonth.now()
        val mesi = generateSequence(periodo.first) { it.plusMonths(1) }.takeWhile { it <= periodo.second }.toList()
        Calcoli.ricorrenti(mesi, d.voci, d.contiValuta, d.operazioni, Cambi.fisso(imp.cambioChfEur), oggi, pers)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Scadenze ricorrenti ---

    /**
     * Imposta importo ([importoCent], positivo) e data prevista ([data]) della scadenza di [voce]
     * del mese [meseScadenza]; null = calcolati. Una data in un altro mese sposta la scadenza lì
     * (solo questa volta). Senza nessuna personalizzazione la riga viene tolta.
     */
    fun salvaScadenza(voce: Voce, meseScadenza: YearMonth, importoCent: Long?, data: Long?) = viewModelScope.launch {
        val chiave = meseScadenza.toString()
        val attuale = personalizzazioni.value.firstOrNull { it.voceId == voce.id && it.mese == chiave }
        val spostataA = data?.let { Calcoli.mese(it) }?.takeIf { it != meseScadenza }?.toString()
            ?: attuale?.spostataA.takeIf { data == null }
        if (importoCent == null && data == null && spostataA == null) {
            dao.eliminaPrevisione(voce.id, chiave)
        } else {
            dao.salvaPrevisione(
                PrevisioneRicorrente(id = attuale?.id ?: 0, voceId = voce.id, mese = chiave, importoCent = importoCent?.let { kotlin.math.abs(it) }, data = data, spostataA = spostataA)
            )
        }
        messaggio("Scadenza aggiornata")
    }

    /**
     * Sposta la scadenza di [voce] del mese [meseScadenza] a [nuovoMese]: con [mantieniRicorrenza]
     * solo questa volta (le successive restano come in anagrafica), altrimenti la ricorrenza riparte
     * da [nuovoMese] (cambia il mese di partenza in anagrafica e le personalizzazioni successive
     * vengono tolte; importo e data di questa passano alla nuova scadenza).
     */
    fun spostaScadenza(voce: Voce, meseScadenza: YearMonth, nuovoMese: YearMonth, mantieniRicorrenza: Boolean) = viewModelScope.launch {
        val chiave = meseScadenza.toString()
        val attuale = personalizzazioni.value.firstOrNull { it.voceId == voce.id && it.mese == chiave }
        val dataNelMese = attuale?.data?.takeIf { Calcoli.mese(it) == nuovoMese }
        db.withTransaction {
            if (mantieniRicorrenza) {
                val spostata = nuovoMese.toString().takeIf { nuovoMese != meseScadenza }
                if (spostata == null && attuale?.importoCent == null && dataNelMese == null) {
                    dao.eliminaPrevisione(voce.id, chiave)
                } else {
                    dao.salvaPrevisione(
                        PrevisioneRicorrente(id = attuale?.id ?: 0, voceId = voce.id, mese = chiave, importoCent = attuale?.importoCent, data = dataNelMese, spostataA = spostata)
                    )
                }
            } else {
                dao.aggiornaVoce(voce.copy(meseInizio = nuovoMese.toString()))
                personalizzazioni.value.filter { it.voceId == voce.id && (testoInMese(it.mese) ?: nuovoMese) >= meseScadenza }
                    .forEach { dao.eliminaPrevisione(voce.id, it.mese) }
                if (attuale?.importoCent != null || dataNelMese != null) {
                    dao.salvaPrevisione(PrevisioneRicorrente(voceId = voce.id, mese = nuovoMese.toString(), importoCent = attuale?.importoCent, data = dataNelMese))
                }
            }
        }
        messaggio(if (mantieniRicorrenza) "Scadenza spostata a ${formattaMese(nuovoMese)}" else "Ricorrenza ripartita da ${formattaMese(nuovoMese)}")
    }

    private fun messaggio(testo: String) {
        _messaggi.tryEmit(testo)
    }

    // --- Impostazioni ---

    fun salvaImpostazioni(nuove: Impostazioni) {
        preferenze.salva(nuove)
        _impostazioni.value = nuove
    }

    // --- Operazioni ---

    /**
     * Salva un'operazione. Per uno spostamento la contro-operazione sul conto di destinazione è:
     * - la riga esistente [controparteId] scelta dall'utente: se è già quella collegata viene
     *   aggiornata con l'importo [importoDestinazioneCent], altrimenti viene collegata così com'è (il
     *   suo importo fa fede) e l'eventuale vecchia contro-operazione collegata viene eliminata;
     * - senza scelta: la contro-operazione collegata, o la riga speculare di uno spostamento importato
     *   (vedi [Spostamenti.trovaControparte]) sul vecchio conto di destinazione, spostata sul nuovo;
     *   se non ce n'è una viene creata con importo [importoDestinazioneCent] (o l'opposto dell'importo,
     *   se nella stessa valuta).
     */
    fun salvaOperazione(op: Operazione, importoDestinazioneCent: Long?, controparteId: Long? = null) = viewModelScope.launch {
        db.withTransaction {
            val precedente = if (op.id != 0L) dao.operazione(op.id) else null
            val collegata = precedente?.collegataId?.let { dao.operazione(it) }
            val dest = op.contoValutaDestId
            if (op.trasferimento && dest != null) {
                val importoControparte = importoDestinazioneCent ?: -op.importoCent
                val controparte = Operazione(
                    contoValutaId = dest,
                    data = op.data,
                    importoCent = importoControparte,
                    trasferimento = true,
                    contoValutaDestId = op.contoValutaId,
                    note = op.note
                )
                val scelta = controparteId?.let { dao.operazione(it) }?.takeIf { it.contoValutaId == dest && it.id != op.id }
                val id = if (precedente == null) dao.inserisciOperazione(op.copy(id = 0, voceId = null, collegataId = null)) else op.id

                /** La riga [x] diventa la contro-operazione: data propria, se quella dello spostamento non è cambiata. */
                suspend fun aggiornaControparte(x: Operazione): Long {
                    val data = if (op.data == precedente?.data) x.data else op.data
                    dao.aggiornaOperazione(
                        controparte.copy(id = x.id, data = data, collegataId = id, note = x.note ?: op.note, ordine = x.ordine)
                    )
                    return x.id
                }

                val destPrecedente = precedente?.takeIf { it.trasferimento }?.contoValutaDestId
                val idControparte = when {
                    scelta != null && scelta.id == collegata?.id -> aggiornaControparte(scelta)
                    scelta != null -> {
                        collegata?.let { dao.eliminaOperazioni(listOf(it.id)) }
                        dao.aggiornaOperazione(
                            scelta.copy(trasferimento = true, voceId = null, contoValutaDestId = op.contoValutaId, collegataId = id)
                        )
                        scelta.id
                    }
                    collegata != null -> aggiornaControparte(collegata)
                    precedente != null && destPrecedente != null && destPrecedente != dest -> {
                        val valute = dao.contiValuta().associate { it.id to it.valuta }
                        Spostamenti.trovaControparte(precedente, destPrecedente, dao.operazioni()) { valute[it] }
                            ?.let { aggiornaControparte(it) }
                            ?: dao.inserisciOperazione(controparte.copy(collegataId = id))
                    }
                    else -> dao.inserisciOperazione(controparte.copy(collegataId = id))
                }
                dao.aggiornaOperazione(op.copy(id = id, voceId = null, collegataId = idControparte))
            } else {
                val semplice = op.copy(trasferimento = false, contoValutaDestId = null, collegataId = null)
                if (precedente == null) {
                    dao.inserisciOperazione(semplice.copy(id = 0))
                } else {
                    collegata?.let { dao.eliminaOperazioni(listOf(it.id)) }
                    dao.aggiornaOperazione(semplice)
                }
            }
        }
    }

    /** Elimina l'operazione e, se è uno spostamento inserito dall'app, anche la contro-operazione. */
    fun eliminaOperazione(op: Operazione) = viewModelScope.launch {
        dao.eliminaOperazioni(listOfNotNull(op.id, op.collegataId))
    }

    /**
     * Sposta le operazioni selezionate su un altro conto/valuta (es. movimenti CHF finiti per errore
     * in LGT EUR). Importo, data e tipo restano invariati; gli spostamenti con destinazione proprio
     * quel conto/valuta vengono saltati perché diventerebbero un trasferimento verso sé stesso.
     */
    fun spostaOperazioniConto(operazioni: List<Operazione>, contoValutaId: Long) = viewModelScope.launch {
        var spostate = 0
        db.withTransaction {
            for (op in operazioni) {
                if (op.contoValutaId == contoValutaId || op.contoValutaDestId == contoValutaId) continue
                dao.aggiornaOperazione(op.copy(contoValutaId = contoValutaId))
                // La contro-operazione collegata ora proviene dal nuovo conto.
                op.collegataId?.let { dao.operazione(it) }?.let { dao.aggiornaOperazione(it.copy(contoValutaDestId = contoValutaId)) }
                spostate++
            }
        }
        messaggio("Spostate $spostate operazioni in ${dati.value.etichetta(contoValutaId)}")
    }

    /** Eliminazione multipla (selezione nella lista operazioni), con le contro-operazioni collegate. */
    /**
     * Elimina le [operazioni]; con [ancheCollegate] anche le righe collegate sugli altri conti,
     * altrimenti quelle restano (senza collegamento) e il saldo degli altri conti non cambia.
     */
    fun eliminaOperazioni(operazioni: List<Operazione>, ancheCollegate: Boolean) = viewModelScope.launch {
        db.withTransaction {
            val ids = operazioni.map { it.id }.toHashSet()
            if (ancheCollegate) {
                ids += operazioni.mapNotNull { it.collegataId }
            } else {
                for (op in operazioni) {
                    val idAltra = op.collegataId ?: continue
                    if (idAltra in ids) continue
                    dao.operazione(idAltra)?.takeIf { it.collegataId == op.id }?.let { dao.aggiornaOperazione(it.copy(collegataId = null)) }
                }
            }
            ids.toList().chunked(500).forEach { dao.eliminaOperazioni(it) }
        }
        messaggio("Eliminate ${operazioni.size} operazioni")
    }

    // --- Riscontro spostamenti ---

    /** Collega le coppie di spostamenti (righe speculari su due conti) ancora non collegate. */
    fun collegaSpostamenti(coppie: List<Pair<Operazione, Operazione>>) = viewModelScope.launch {
        var collegati = 0
        db.withTransaction {
            for ((a, b) in coppie) {
                val x = dao.operazione(a.id) ?: continue
                val y = dao.operazione(b.id) ?: continue
                if (x.collegataId != null || y.collegataId != null || x.contoValutaId == y.contoValutaId) continue
                dao.aggiornaOperazione(x.copy(trasferimento = true, voceId = null, contoValutaDestId = y.contoValutaId, collegataId = y.id))
                dao.aggiornaOperazione(y.copy(trasferimento = true, voceId = null, contoValutaDestId = x.contoValutaId, collegataId = x.id))
                collegati++
            }
        }
        messaggio(if (collegati == 1) "Spostamento collegato" else "Collegati $collegati spostamenti")
    }

    /**
     * Elimina solo la riga [op]: se era collegata a una riga sull'altro conto, quella resta (senza
     * collegamento) invece di essere eliminata insieme.
     */
    fun eliminaSoloRiga(op: Operazione) = viewModelScope.launch {
        db.withTransaction {
            val x = dao.operazione(op.id) ?: return@withTransaction
            x.collegataId?.let { dao.operazione(it) }?.takeIf { it.collegataId == x.id }?.let { dao.aggiornaOperazione(it.copy(collegataId = null)) }
            dao.eliminaOperazioni(listOf(x.id))
        }
        messaggio("Operazione eliminata")
    }

    /** Toglie il collegamento di [op] (e quello reciproco della riga collegata). */
    fun scollegaSpostamento(op: Operazione) = viewModelScope.launch {
        db.withTransaction {
            val x = dao.operazione(op.id) ?: return@withTransaction
            x.collegataId?.let { dao.operazione(it) }?.takeIf { it.collegataId == x.id }?.let { dao.aggiornaOperazione(it.copy(collegataId = null)) }
            dao.aggiornaOperazione(x.copy(collegataId = null))
        }
    }

    // --- Cambi mensili ---

    /** Imposta il cambio CHF/EUR del [mese] ([chfEur] null: torna a quello calcolato). */
    fun salvaCambio(mese: YearMonth, chfEur: Double?) = viewModelScope.launch {
        if (chfEur == null) dao.eliminaCambio(meseInTesto(mese)) else dao.salvaCambio(Cambio(meseInTesto(mese), chfEur))
    }

    // --- Anagrafica conti ---

    /** [saldiIniziali]: valuta -> saldo iniziale in centesimi, solo per le valute attive sul conto. */
    fun salvaConto(conto: Conto, saldiIniziali: Map<String, Long>) = viewModelScope.launch {
        val nome = conto.nome.trim()
        if (nome.isEmpty()) return@launch messaggio("Il nome del conto è obbligatorio")
        if (saldiIniziali.isEmpty()) return@launch messaggio("Seleziona almeno una valuta")
        db.withTransaction {
            val idConto = if (conto.id == 0L) dao.inserisciConto(conto.copy(nome = nome)) else conto.id.also { dao.aggiornaConto(conto.copy(nome = nome)) }
            val esistenti = dao.contiValutaDelConto(idConto).associateBy { it.valuta }
            for ((valuta, saldo) in saldiIniziali) {
                val cv = esistenti[valuta]
                if (cv == null) dao.inserisciContoValuta(ContoValuta(contoId = idConto, valuta = valuta, saldoInizialeCent = saldo))
                else dao.aggiornaContoValuta(cv.copy(saldoInizialeCent = saldo))
            }
            for ((valuta, cv) in esistenti) {
                if (valuta in saldiIniziali) continue
                val usate = dao.contaOperazioniContoValuta(cv.id)
                if (usate > 0) messaggio("Valuta $valuta non rimossa: ha $usate operazioni")
                else dao.eliminaContoValuta(cv)
            }
        }
    }

    fun eliminaConto(conto: Conto) = viewModelScope.launch { dao.eliminaConto(conto) }

    // --- Anagrafica voci ---

    fun salvaVoce(voce: Voce, onFatto: () -> Unit) = viewModelScope.launch {
        val tipo = voce.tipo.trim()
        val sottotipo = voce.sottotipo?.trim()?.ifEmpty { null }
        if (tipo.isEmpty()) return@launch messaggio("Il tipo è obbligatorio")
        val duplicata = dati.value.voci.any {
            it.id != voce.id && it.tipo.equals(tipo, ignoreCase = true) && (it.sottotipo ?: "").equals(sottotipo ?: "", ignoreCase = true)
        }
        if (duplicata) return@launch messaggio("Voce già presente in anagrafica")
        val pulita = voce.copy(tipo = tipo, sottotipo = sottotipo, mesiRicorrenza = voce.mesiRicorrenza.coerceAtLeast(1))
        if (pulita.id == 0L) dao.inserisciVoce(pulita) else dao.aggiornaVoce(pulita)
        onFatto()
    }

    fun eliminaVoce(voce: Voce, onFatto: () -> Unit) = viewModelScope.launch {
        val usate = dao.contaOperazioniVoce(voce.id)
        if (usate > 0) {
            messaggio("Impossibile eliminare: la voce è usata da $usate operazioni")
        } else {
            dao.eliminaVoce(voce)
            onFatto()
        }
    }

    /** Sposta tutte le operazioni di [da] su [a], poi chiama [onFatto] con il numero di operazioni spostate. */
    fun spostaOperazioniVoce(da: Voce, a: Voce, onFatto: (Int) -> Unit) = viewModelScope.launch {
        if (da.id == a.id) return@launch messaggio("Scegli una voce diversa da quella di origine")
        val spostate = dao.spostaOperazioniVoce(da.id, a.id)
        messaggio("$spostate operazioni spostate su ${a.descrizione}")
        onFatto(spostate)
    }

    /** Voce per tipo/sottotipo (sottotipo vuoto = voce di solo tipo), creata se manca solo quella di tipo. */
    suspend fun voceId(tipo: String, sottotipo: String?): Long? {
        val voci = dati.value.voci
        val s = sottotipo?.trim()?.ifEmpty { null }
        voci.firstOrNull { it.tipo.equals(tipo.trim(), true) && (it.sottotipo ?: "").equals(s ?: "", true) }?.let { return it.id }
        if (s != null) return null
        // Tipo esistente ma senza una voce "solo tipo": la si crea con le stesse caratteristiche.
        val modello = voci.firstOrNull { it.tipo.equals(tipo.trim(), true) } ?: return null
        return dao.inserisciVoce(modello.copy(id = 0, sottotipo = null, importoPrevistoCent = null))
    }

    // --- Import da Excel ---

    /** Import in attesa delle scelte dell'utente su come associare le Bollette alle ricorrenti. */
    private val _analisiImport = MutableStateFlow<AnalisiImport?>(null)
    val analisiImport: StateFlow<AnalisiImport?> = _analisiImport.asStateFlow()

    fun mappatureBollette(): Map<String, String> = preferenze.caricaMappatureBollette()

    /** Prima fase: legge il file; se ci sono Bollette da associare attende [confermaImport]. */
    fun importaExcel(uri: Uri) = viewModelScope.launch {
        _importazioneInCorso.value = true
        try {
            val analisi = withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use { ImportatoreExcel(db).analizza(it) }
            } ?: throw IllegalStateException("Impossibile aprire il file")
            if (analisi.combinazioni.isEmpty()) scriviImport(analisi, emptyMap()) else _analisiImport.value = analisi
        } catch (e: Exception) {
            messaggio("Importazione non riuscita: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _importazioneInCorso.value = false
        }
    }

    /** Seconda fase: memorizza le scelte (riusate nei prossimi import) e sostituisce i dati. */
    fun confermaImport(scelte: Map<String, String>) = viewModelScope.launch {
        val analisi = _analisiImport.value ?: return@launch
        preferenze.salvaMappatureBollette(scelte)
        _analisiImport.value = null
        _importazioneInCorso.value = true
        try {
            scriviImport(analisi, scelte)
        } catch (e: Exception) {
            messaggio("Importazione non riuscita: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _importazioneInCorso.value = false
        }
    }

    /** Memorizza le scelte fatte finora senza importare: al prossimo import saranno già compilate. */
    fun salvaScelteImport(scelte: Map<String, String>) {
        preferenze.salvaMappatureBollette(scelte)
        _analisiImport.value = null
        messaggio("Scelte salvate (${scelte.size}): riprendi da ⋮ › Importa da Excel")
    }

    fun annullaImport() {
        _analisiImport.value = null
    }

    private suspend fun scriviImport(analisi: AnalisiImport, scelte: Map<String, String>) {
        val esito = ImportatoreExcel(db).scrivi(analisi, scelte)
        esito.targetRisparmioCent?.let { salvaImpostazioni(_impostazioni.value.copy(targetRisparmioCent = it)) }
        messaggio(
            "Importate ${esito.operazioni} operazioni, ${esito.voci} voci (${esito.vociRicorrenti} ricorrenti)" +
                (esito.targetRisparmioCent?.let { ", target ${formattaCent(it)}" } ?: "")
        )
    }

    // --- Import estratto conto (Gemini) e anagrafica associazioni ---

    val associazioni: StateFlow<List<Associazione>> = dao.associazioniFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _importEstratto = MutableStateFlow<ImportEstratto?>(null)
    val importEstratto: StateFlow<ImportEstratto?> = _importEstratto.asStateFlow()

    private val _testoAttesa = MutableStateFlow("Lettura del file…")
    val testoAttesa: StateFlow<String> = _testoAttesa.asStateFlow()

    /**
     * Manda l'estratto conto a Gemini e confronta i movimenti con le operazioni del conto/valuta
     * [contoValutaId] scelto dall'utente: tutti i movimenti vanno su quel conto/valuta (la valuta letta
     * da Gemini non sceglie più il conto: nelle descrizioni compaiono anche importi in valuta estera).
     * Tutti sono proposti con i tipi suggeriti dall'anagrafica associazioni; quelli già presenti
     * (stesso importo, data uguale) o simili (stesso importo entro [GIORNI_DOPPIONE] giorni) partono
     * deselezionati.
     */
    fun importaEstratto(uri: Uri, contoValutaId: Long) = viewModelScope.launch {
        val cvScelto = dati.value.contiValutaPerId[contoValutaId] ?: return@launch messaggio("Conto non trovato")
        val contoId = cvScelto.contoId
        _testoAttesa.value = "Analisi dell'estratto conto con Gemini…"
        _importazioneInCorso.value = true
        try {
            val esito = EstrattoGemini(BuildConfig.GEMINI_API_KEY, RegistroPromptStore(getApplication())).estrai(getApplication(), uri, cvScelto.valuta) { blocco, totale ->
                _testoAttesa.value = if (totale > 1) "Analisi con Gemini: blocco $blocco di $totale…" else "Analisi dell'estratto conto con Gemini…"
            }
            val movimenti = esito.movimenti
            val elencoAssociazioni = dao.associazioni()
            // Date delle operazioni esistenti per conto/valuta e importo, per riconoscere i doppioni.
            // Operazioni esistenti per conto/valuta e importo; ognuna è abbinata al più a un movimento,
            // prima quelle con data identica e poi quelle vicine (possibili doppioni).
            val esistenti = dati.value.operazioni.groupBy { it.contoValutaId to it.importoCent }
            val contoDi = movimenti.map { cvScelto }
            val usate = HashSet<Long>()
            val abbinate = arrayOfNulls<Pair<Operazione, Long>>(movimenti.size)
            for (massimaDistanza in listOf(0L, GIORNI_DOPPIONE)) {
                movimenti.forEachIndexed { i, m ->
                    if (abbinate[i] != null) return@forEachIndexed
                    val date = m.tutteLeDate.map { it.toEpochDay() }
                    abbinate[i] = esistenti[contoDi[i].id to m.importoCent].orEmpty()
                        .filter { it.id !in usate }
                        .map { op -> op to date.minOf { kotlin.math.abs(it - op.data) } }
                        .filter { it.second <= massimaDistanza }
                        .minByOrNull { it.second }
                        ?.also { usate += it.first.id }
                }
            }
            // Ordine nella giornata dalla posizione nel file: se l'estratto è dal più recente al più
            // vecchio (caso tipico) l'ordine è invertito, così "crescente" resta "più vecchia prima".
            val discendente = movimenti.size > 1 &&
                movimenti.first().tutteLeDate.min() > movimenti.last().tutteLeDate.min()
            val righe = movimenti.mapIndexed { indice, m ->
                val abbinata = abbinate[indice]
                val ordine = if (discendente) (movimenti.size - indice).toLong() else (indice + 1).toLong()
                val presenza = when {
                    abbinata == null -> Presenza.NUOVA
                    abbinata.second == 0L -> Presenza.PRESENTE
                    else -> Presenza.SIMILE
                }
                RigaEstratto(
                    indice, m, contoDi[indice].id, Associazioni.candidate(m.descrizione, elencoAssociazioni),
                    presenza, (abbinata?.second ?: 0L).toInt(), abbinata?.first, ordine
                )
            }
            if (righe.isEmpty()) {
                messaggio("Nessun movimento trovato nel file" + esito.avvisi.firstOrNull()?.let { ": $it" }.orEmpty())
            } else {
                _importEstratto.value = ImportEstratto(contoId, righe, esito.avvisi)
            }
        } catch (e: Exception) {
            messaggio("Import estratto conto non riuscito: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _importazioneInCorso.value = false
            _testoAttesa.value = "Lettura del file…"
        }
    }

    // --- Riscontro con l'estratto conto ---

    private val _riscontroEstratto = MutableStateFlow<StatoRiscontroEstratto?>(null)
    val riscontroEstratto: StateFlow<StatoRiscontroEstratto?> = _riscontroEstratto.asStateFlow()

    /** Legge l'estratto conto [uri] con Gemini e apre il riscontro con le operazioni del conto/valuta. */
    fun riscontraEstratto(uri: Uri, contoValutaId: Long) = viewModelScope.launch {
        val cv = dati.value.contiValutaPerId[contoValutaId] ?: return@launch messaggio("Conto non trovato")
        _testoAttesa.value = "Analisi dell'estratto conto con Gemini…"
        _importazioneInCorso.value = true
        try {
            val esito = EstrattoGemini(BuildConfig.GEMINI_API_KEY, RegistroPromptStore(getApplication())).estrai(getApplication(), uri, cv.valuta) { blocco, totale ->
                _testoAttesa.value = if (totale > 1) "Analisi con Gemini: blocco $blocco di $totale…" else "Analisi dell'estratto conto con Gemini…"
            }
            if (esito.movimenti.isEmpty()) {
                messaggio("Nessun movimento trovato nel file" + esito.avvisi.firstOrNull()?.let { ": $it" }.orEmpty())
            } else {
                _riscontroEstratto.value = StatoRiscontroEstratto(contoValutaId, cv.contoId, esito.movimenti, esito.avvisi)
            }
        } catch (e: Exception) {
            messaggio("Lettura dell'estratto conto non riuscita: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _importazioneInCorso.value = false
            _testoAttesa.value = "Lettura del file…"
        }
    }

    fun chiudiRiscontroEstratto() {
        _riscontroEstratto.value = null
    }

    /** Porta la data delle operazioni a quella del movimento collegato dell'estratto. */
    fun aggiornaDateDaEstratto(nuoveDate: Map<Long, Long>) = viewModelScope.launch {
        db.withTransaction {
            for ((id, data) in nuoveDate) {
                dao.operazione(id)?.let { dao.aggiornaOperazione(it.copy(data = data)) }
            }
        }
        messaggio(if (nuoveDate.size == 1) "Aggiornata 1 data" else "Aggiornate ${nuoveDate.size} date")
    }

    /**
     * Apre la registrazione (lo stesso popup dell'import da estratto conto: tipo per riga, righe
     * deselezionabili) dei movimenti [indici] dell'estratto in riscontro che mancano sul conto.
     */
    fun creaDaEstratto(indici: List<Int>) = viewModelScope.launch {
        val stato = _riscontroEstratto.value ?: return@launch
        val movimenti = stato.movimenti
        val associazioni = dao.associazioni()
        val discendente = movimenti.size > 1 && movimenti.first().tutteLeDate.min() > movimenti.last().tutteLeDate.min()
        val righe = indici.map { indice ->
            val m = movimenti[indice]
            val ordine = if (discendente) (movimenti.size - indice).toLong() else (indice + 1).toLong()
            RigaEstratto(indice, m, stato.contoValutaId, Associazioni.candidate(m.descrizione, associazioni), Presenza.NUOVA, 0, null, ordine)
        }
        _riscontroEstratto.value = null
        _importEstratto.value = ImportEstratto(stato.contoId, righe, stato.avvisi)
    }

    /** Tipo di data da registrare scelto l'ultima volta per il conto (default: data dell'operazione). */
    fun tipoDataEstratto(contoId: Long): TipoData = preferenze.caricaTipoDataEstratto(contoId)

    fun salvaTipoDataEstratto(contoId: Long, tipo: TipoData) = preferenze.salvaTipoDataEstratto(contoId, tipo)

    fun annullaImportEstratto() {
        _importEstratto.value = null
    }

    /** Registra i movimenti scelti; tipi/sottotipi non ancora in anagrafica vengono creati. */
    fun confermaImportEstratto(scelte: List<SceltaEstratto>, aggiornamenti: List<AggiornamentoData> = emptyList()) = viewModelScope.launch {
        _importEstratto.value = null
        var importate = 0
        var nuoveVoci = 0
        db.withTransaction {
            // Operazioni già presenti: si aggiornano solo la data (se scelto) e l'ordine dell'estratto conto,
            // anche sulla contro-operazione collegata di uno spostamento.
            for (a in aggiornamenti) {
                val op = dao.operazione(a.operazione.id) ?: continue
                val giorno = a.nuovaData?.toEpochDay() ?: op.data
                dao.aggiornaOperazione(op.copy(data = giorno, ordine = a.ordine ?: op.ordine))
                op.collegataId?.let { dao.operazione(it) }?.let {
                    dao.aggiornaOperazione(it.copy(data = giorno, ordine = a.ordine ?: it.ordine))
                }
            }
            val voci = dao.voci().toMutableList()
            suspend fun voceId(tipo: String, sottotipo: String?): Long {
                voci.firstOrNull { it.tipo.equals(tipo, true) && (it.sottotipo ?: "").equals(sottotipo ?: "", true) }?.let { return it.id }
                val modello = voci.firstOrNull { it.tipo.equals(tipo, true) }
                val nuova = Voce(
                    tipo = modello?.tipo ?: tipo,
                    sottotipo = sottotipo,
                    entrata = modello?.entrata ?: false,
                    ricorrente = modello?.ricorrente ?: false,
                    mesiRicorrenza = modello?.mesiRicorrenza ?: 1,
                    meseInizio = if (sottotipo == null) modello?.meseInizio else null
                )
                val id = dao.inserisciVoce(nuova)
                voci += nuova.copy(id = id)
                nuoveVoci++
                return id
            }
            for (scelta in scelte) {
                val m = scelta.riga.movimento
                val note = m.descrizione.ifBlank { null }
                val dest = scelta.destinazioneId
                if (scelta.tipo.equals(Associazione.TIPO_SPOSTAMENTO, ignoreCase = true) && dest != null) {
                    val op = Operazione(
                        contoValutaId = scelta.riga.contoValutaId,
                        data = scelta.data.toEpochDay(),
                        importoCent = m.importoCent,
                        trasferimento = true,
                        contoValutaDestId = dest,
                        note = note,
                        ordine = scelta.riga.ordine
                    )
                    val id = dao.inserisciOperazione(op)
                    // Contro-operazione solo nella stessa valuta: con un cambio l'importo accreditato non è noto.
                    if (dati.value.contiValutaPerId[dest]?.valuta == dati.value.contiValutaPerId[op.contoValutaId]?.valuta) {
                        val idControparte = dao.inserisciOperazione(
                            op.copy(contoValutaId = dest, importoCent = -m.importoCent, contoValutaDestId = op.contoValutaId, collegataId = id)
                        )
                        dao.aggiornaOperazione(op.copy(id = id, collegataId = idControparte))
                    }
                } else {
                    dao.inserisciOperazione(
                        Operazione(
                            contoValutaId = scelta.riga.contoValutaId,
                            data = scelta.data.toEpochDay(),
                            importoCent = m.importoCent,
                            voceId = voceId(scelta.tipo.trim(), scelta.sottotipo?.trim()?.ifEmpty { null }),
                            note = note,
                            ordine = scelta.riga.ordine
                        )
                    )
                }
                importate++
            }
        }
        messaggio(
            "Importate $importate operazioni dall'estratto conto" +
                aggiornamenti.count { it.nuovaData != null }.let { if (it > 0) ", aggiornate le date di $it" else "" } +
                (if (aggiornamenti.isNotEmpty()) ", ordine aggiornato per ${aggiornamenti.size} già presenti" else "") +
                (if (nuoveVoci > 0) " ($nuoveVoci nuove voci in anagrafica)" else "")
        )
    }

    fun salvaAssociazione(associazione: Associazione) = viewModelScope.launch {
        val pulita = associazione.copy(
            chiave = associazione.chiave.trim(),
            tipo = associazione.tipo.trim(),
            sottotipo = associazione.sottotipo?.trim()?.ifEmpty { null }
        )
        if (pulita.chiave.isEmpty() || pulita.tipo.isEmpty()) return@launch messaggio("Chiave e tipo sono obbligatori")
        if (pulita.id == 0L) dao.inserisciAssociazione(pulita) else dao.aggiornaAssociazione(pulita)
    }

    fun eliminaAssociazione(associazione: Associazione) = viewModelScope.launch { dao.eliminaAssociazione(associazione) }

    /** Aggiunge le associazioni di un elenco incollato ("Chiave (Tipo)" per riga), saltando i doppioni. */
    fun importaElencoAssociazioni(testo: String) = viewModelScope.launch {
        val esistenti = dao.associazioni().map { Triple(it.chiave.lowercase(), it.tipo.lowercase(), (it.sottotipo ?: "").lowercase()) }.toMutableSet()
        val nuove = Associazioni.leggiElenco(testo).filter { esistenti.add(Triple(it.chiave.lowercase(), it.tipo.lowercase(), (it.sottotipo ?: "").lowercase())) }
        dao.inserisciAssociazioni(nuove)
        messaggio("Aggiunte ${nuove.size} associazioni")
    }

    // --- Backup su Google Drive ---

    private val _statoBackup = MutableStateFlow(StatoBackup())
    val statoBackup: StateFlow<StatoBackup> = _statoBackup.asStateFlow()

    /** Intent della schermata di consenso Google da mostrare (scope Drive non ancora concesso). */
    private val _richiestaAutorizzazione = MutableStateFlow<Intent?>(null)
    val richiestaAutorizzazione: StateFlow<Intent?> = _richiestaAutorizzazione.asStateFlow()
    private var azioneInSospeso: (() -> Unit)? = null

    fun impostaAccountBackup(email: String) {
        salvaImpostazioni(_impostazioni.value.copy(emailBackup = email))
        _statoBackup.value = StatoBackup()
        aggiornaInfoBackup()
    }

    fun esitoAutorizzazione(concessa: Boolean) {
        _richiestaAutorizzazione.value = null
        val azione = azioneInSospeso
        azioneInSospeso = null
        if (concessa) azione?.invoke() else _statoBackup.value = _statoBackup.value.copy(inCorso = false, errore = "Autorizzazione Google negata")
    }

    private fun operazioneDrive(nome: String, azione: suspend (BackupDrive) -> Unit) {
        val email = _impostazioni.value.emailBackup ?: return messaggio("Scegli prima l'account Google")
        viewModelScope.launch {
            _statoBackup.value = _statoBackup.value.copy(inCorso = true, errore = null)
            try {
                azione(BackupDrive(getApplication(), email))
                _statoBackup.value = _statoBackup.value.copy(inCorso = false)
            } catch (e: UserRecoverableAuthIOException) {
                azioneInSospeso = { operazioneDrive(nome, azione) }
                _richiestaAutorizzazione.value = e.intent
            } catch (e: GooglePlayServicesAvailabilityIOException) {
                _statoBackup.value = _statoBackup.value.copy(inCorso = false, errore = "Google Play Services non disponibili")
            } catch (e: Exception) {
                _statoBackup.value = _statoBackup.value.copy(inCorso = false, errore = "$nome non riuscito: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun aggiornaInfoBackup() = operazioneDrive("Lettura backup") { drive ->
        _statoBackup.value = _statoBackup.value.copy(backup = drive.elenco(), infoCaricata = true)
    }

    /** Nuovo backup con un [testo] facoltativo; restano solo gli ultimi N (Impostazioni). */
    fun eseguiBackup(testo: String?) = operazioneDrive("Backup") { drive ->
        val copia = withContext(Dispatchers.IO) {
            File(getApplication<Application>().cacheDir, "backup.db").also { AppDatabase.fileDatabase(getApplication()).copyTo(it, overwrite = true) }
        }
        val rimasti = drive.carica(copia, testo?.trim(), _impostazioni.value.backupDaMantenere)
        copia.delete()
        _statoBackup.value = _statoBackup.value.copy(backup = rimasti, infoCaricata = true)
        messaggio("Backup completato")
    }

    /** Elimina il backup [id] da Drive. */
    fun eliminaBackup(id: String) = operazioneDrive("Eliminazione backup") { drive ->
        drive.elimina(id)
        _statoBackup.value = _statoBackup.value.copy(backup = drive.elenco(), infoCaricata = true)
        messaggio("Backup eliminato")
    }

    /** Sostituisce il database con il backup [id] su Drive e riavvia l'app. */
    fun ripristinaBackup(id: String) = operazioneDrive("Ripristino") { drive ->
        val app = getApplication<Application>()
        val scaricato = File(app.cacheDir, "ripristino.db")
        drive.scarica(id, scaricato)
        val intestazione = withContext(Dispatchers.IO) { scaricato.inputStream().use { input -> ByteArray(15).also { input.read(it) } } }
        if (String(intestazione, Charsets.US_ASCII) != "SQLite format 3") {
            scaricato.delete()
            throw IllegalStateException("il file su Drive non è un database valido")
        }
        withContext(Dispatchers.IO) {
            AppDatabase.chiudi()
            val destinazione = AppDatabase.fileDatabase(app)
            scaricato.copyTo(destinazione, overwrite = true)
            File(destinazione.path + "-journal").delete()
            scaricato.delete()
        }
        riavvia()
    }

    /** Ripristino parziale in attesa della scelta dei conti/valuta: backup scaricato e suoi conti. */
    private val _ripristinoParziale = MutableStateFlow<Pair<String, List<ContoBackup>>?>(null)
    val ripristinoParziale: StateFlow<Pair<String, List<ContoBackup>>?> = _ripristinoParziale.asStateFlow()

    private fun fileRipristinoParziale() = File(getApplication<Application>().cacheDir, "ripristino_parziale.db")

    /** Scarica il backup [id] e ne legge i conti/valuta, per scegliere cosa ripristinare. */
    fun preparaRipristinoParziale(id: String) = operazioneDrive("Lettura backup") { drive ->
        val file = fileRipristinoParziale()
        drive.scarica(id, file)
        val conti = withContext(Dispatchers.IO) {
            val intestazione = file.inputStream().use { input -> ByteArray(15).also { input.read(it) } }
            if (String(intestazione, Charsets.US_ASCII) != "SQLite format 3") throw IllegalStateException("il file su Drive non è un database valido")
            LetturaBackup(file).use { it.contiValuta() }
        }
        _ripristinoParziale.value = id to conti
    }

    fun annullaRipristinoParziale() {
        _ripristinoParziale.value = null
        fileRipristinoParziale().delete()
    }

    /** Ripristina solo i conti/valuta [idContiBackup] del backup scaricato, lasciando intatti gli altri. */
    fun eseguiRipristinoParziale(idContiBackup: Set<Long>) = viewModelScope.launch {
        _ripristinoParziale.value = null
        _statoBackup.value = _statoBackup.value.copy(inCorso = true, errore = null)
        val file = fileRipristinoParziale()
        try {
            val esito = withContext(Dispatchers.IO) { RipristinoParziale(db).esegui(file, idContiBackup) }
            messaggio(
                "Ripristinate ${esito.ripristinate} operazioni (sostituite ${esito.sostituite})" +
                    (if (esito.ricollegate > 0) ", ${esito.ricollegate} spostamenti ricollegati" else "") +
                    (if (esito.scollegate > 0) ", ${esito.scollegate} collegamenti rimossi" else "") +
                    (if (esito.nuoveVoci > 0) ", ${esito.nuoveVoci} voci aggiunte" else "")
            )
        } catch (e: Exception) {
            _statoBackup.value = _statoBackup.value.copy(errore = "Ripristino parziale non riuscito: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _statoBackup.value = _statoBackup.value.copy(inCorso = false)
            file.delete()
        }
    }

    private fun riavvia() {
        val app = getApplication<Application>()
        val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent?.let { app.startActivity(it) }
        Runtime.getRuntime().exit(0)
    }
}
