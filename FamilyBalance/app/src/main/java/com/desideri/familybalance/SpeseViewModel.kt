package com.desideri.familybalance

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.desideri.familybalance.backup.BackupDrive
import com.desideri.familybalance.backup.InfoBackup
import com.desideri.familybalance.data.AppDatabase
import com.desideri.familybalance.data.Associazione
import com.desideri.familybalance.data.Conto
import com.desideri.familybalance.data.ContoValuta
import com.desideri.familybalance.data.Impostazioni
import com.desideri.familybalance.data.Operazione
import com.desideri.familybalance.data.Preferenze
import com.desideri.familybalance.data.Valute
import com.desideri.familybalance.data.Voce
import com.desideri.familybalance.importazione.AnalisiImport
import com.desideri.familybalance.importazione.ImportatoreExcel
import com.desideri.familybalance.estratto.EstrattoGemini
import com.desideri.familybalance.estratto.ImportEstratto
import com.desideri.familybalance.estratto.Presenza
import com.desideri.familybalance.estratto.RegistroPromptStore
import com.desideri.familybalance.estratto.RigaEstratto
import com.desideri.familybalance.estratto.SceltaEstratto
import com.desideri.familybalance.logica.Associazioni
import com.desideri.familybalance.logica.Calcoli
import com.desideri.familybalance.logica.MeseRicorrenti
import com.desideri.familybalance.logica.RigaBilancio
import com.desideri.familybalance.logica.formattaCent
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.Dispatchers
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

    /** Conti/valuta ordinati per nome conto e valuta, come mostrati nelle liste. */
    val contiValutaOrdinati: List<ContoValuta> by lazy {
        contiValuta.sortedWith(compareBy({ contiPerId[it.contoId]?.nome?.lowercase() ?: "" }, { it.valuta }))
    }

    fun etichetta(contoValutaId: Long?): String {
        val cv = contoValutaId?.let { contiValutaPerId[it] } ?: return "Conto eliminato"
        return "${contiPerId[cv.contoId]?.nome ?: "?"} ${cv.valuta}"
    }
}

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

    val dati: StateFlow<DatiApp> = combine(dao.contiFlow(), dao.contiValutaFlow(), dao.vociFlow(), dao.operazioniFlow()) { conti, cv, voci, ops ->
        DatiApp(conti, cv, voci, ops, caricati = true)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DatiApp())

    /** Saldo in centesimi (valuta propria) di ogni conto/valuta. */
    val saldi: StateFlow<Map<Long, Long>> = dati.map { Calcoli.saldiCent(it.contiValuta, it.operazioni) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val bilancio: StateFlow<List<RigaBilancio>> = combine(dati, _impostazioni) { d, imp ->
        Calcoli.bilancio(d.contiValuta, d.voci, d.operazioni, imp.cambioChfEur, imp.targetRisparmioCent / 100.0, YearMonth.now())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Spese ricorrenti da 12 mesi fa a 12 mesi avanti. */
    val ricorrenti: StateFlow<List<MeseRicorrenti>> = combine(dati, _impostazioni) { d, imp ->
        val oggi = YearMonth.now()
        val mesi = (-12L..12L).map { oggi.plusMonths(it) }
        Calcoli.ricorrenti(mesi, d.voci, d.contiValuta, d.operazioni, imp.cambioChfEur, oggi)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
     * Salva un'operazione. Per uno spostamento inserito dall'app crea (o aggiorna) la
     * contro-operazione sul conto di destinazione con importo [importoDestinazioneCent] (o l'opposto
     * dell'importo, se nella stessa valuta). Gli spostamenti importati senza contro-operazione
     * collegata vengono aggiornati solo sul proprio conto, perché la riga speculare esiste già.
     */
    fun salvaOperazione(op: Operazione, importoDestinazioneCent: Long?) = viewModelScope.launch {
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
                when {
                    precedente == null -> {
                        val id = dao.inserisciOperazione(op.copy(id = 0, voceId = null, collegataId = null))
                        val idControparte = dao.inserisciOperazione(controparte.copy(collegataId = id))
                        dao.aggiornaOperazione(op.copy(id = id, voceId = null, collegataId = idControparte))
                    }
                    collegata != null -> {
                        dao.aggiornaOperazione(controparte.copy(id = collegata.id, collegataId = op.id))
                        dao.aggiornaOperazione(op.copy(voceId = null, collegataId = collegata.id))
                    }
                    precedente?.trasferimento == false -> {
                        val idControparte = dao.inserisciOperazione(controparte.copy(collegataId = op.id))
                        dao.aggiornaOperazione(op.copy(voceId = null, collegataId = idControparte))
                    }
                    else -> dao.aggiornaOperazione(op.copy(voceId = null, collegataId = null))
                }
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
     * Manda l'estratto conto a Gemini e confronta i movimenti con le operazioni del [contoId]: tutti
     * sono proposti all'utente con i tipi suggeriti dall'anagrafica associazioni, ma quelli già
     * presenti (stesso importo nella stessa valuta, data valuta o contabile uguale) o simili (stesso
     * importo entro [GIORNI_DOPPIONE] giorni) partono deselezionati.
     */
    fun importaEstratto(uri: Uri, contoId: Long) = viewModelScope.launch {
        val contiValuta = dati.value.contiValuta.filter { it.contoId == contoId }.sortedBy { if (it.valuta == Valute.EUR) 0 else 1 }
        if (contiValuta.isEmpty()) return@launch messaggio("Il conto scelto non ha valute configurate")
        _testoAttesa.value = "Analisi dell'estratto conto con Gemini…"
        _importazioneInCorso.value = true
        try {
            val movimenti = EstrattoGemini(BuildConfig.GEMINI_API_KEY, RegistroPromptStore(getApplication())).estrai(getApplication(), uri, contiValuta.first().valuta)
            val elencoAssociazioni = dao.associazioni()
            // Date delle operazioni esistenti per conto/valuta e importo, per riconoscere i doppioni.
            val esistenti = dati.value.operazioni.groupBy({ it.contoValutaId to it.importoCent }, { it.data })
            val righe = movimenti.mapIndexed { indice, m ->
                val cv = contiValuta.firstOrNull { it.valuta == m.valuta } ?: contiValuta.first()
                val date = esistenti[cv.id to m.importoCent].orEmpty()
                val esatte = listOfNotNull(m.data, m.dataContabile).map { it.toEpochDay() }
                val distanza = date.minOfOrNull { d -> esatte.minOf { kotlin.math.abs(it - d) } }
                val presenza = when {
                    distanza == 0L -> Presenza.PRESENTE
                    distanza != null && distanza <= GIORNI_DOPPIONE -> Presenza.SIMILE
                    else -> Presenza.NUOVA
                }
                RigaEstratto(indice, m, cv.id, Associazioni.candidate(m.descrizione, elencoAssociazioni), presenza, (distanza ?: 0L).toInt())
            }
            if (righe.isEmpty()) {
                messaggio("Nessun movimento trovato nel file")
            } else {
                _importEstratto.value = ImportEstratto(contoId, righe)
            }
        } catch (e: Exception) {
            messaggio("Import estratto conto non riuscito: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            _importazioneInCorso.value = false
            _testoAttesa.value = "Lettura del file…"
        }
    }

    fun annullaImportEstratto() {
        _importEstratto.value = null
    }

    /** Registra i movimenti scelti; tipi/sottotipi non ancora in anagrafica vengono creati. */
    fun confermaImportEstratto(scelte: List<SceltaEstratto>) = viewModelScope.launch {
        _importEstratto.value = null
        var importate = 0
        var nuoveVoci = 0
        db.withTransaction {
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
                        data = m.data.toEpochDay(),
                        importoCent = m.importoCent,
                        trasferimento = true,
                        contoValutaDestId = dest,
                        note = note
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
                            data = m.data.toEpochDay(),
                            importoCent = m.importoCent,
                            voceId = voceId(scelta.tipo.trim(), scelta.sottotipo?.trim()?.ifEmpty { null }),
                            note = note
                        )
                    )
                }
                importate++
            }
        }
        messaggio("Importate $importate operazioni dall'estratto conto" + if (nuoveVoci > 0) " ($nuoveVoci nuove voci in anagrafica)" else "")
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

    private fun riavvia() {
        val app = getApplication<Application>()
        val intent = app.packageManager.getLaunchIntentForPackage(app.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent?.let { app.startActivity(it) }
        Runtime.getRuntime().exit(0)
    }
}
