package com.fabrice.monumentsnearby.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/** Une voix TTS disponible sur l'appareil. */
data class TtsVoice(
    val name: String,
    val locale: String,
    /** Qualité déclarée par le moteur (Voice.QUALITY_*), 0 si inconnue. */
    val quality: Int = 0,
    /** Voix nécessitant le réseau (souvent les plus naturelles). */
    val network: Boolean = false
)

/**
 * Audioguide hors-ligne : TTS Android natif, aucune dépendance réseau.
 * - Bufferise le texte si l'utilisateur clique avant l'initialisation du moteur.
 * - Voix sélectionnable (voix système), choix persisté en SharedPreferences.
 * - Pause/reprise : le texte est découpé en phrases ; la pause coupe à la fin
 *   de la phrase en cours, la reprise continue à la phrase suivante.
 * - Mémoire de lecture : une lecture arrêtée en cours de route reprend à la
 *   même phrase quand on relance le même texte (clé [speak] `key`).
 * - Sans voix choisie, la meilleure voix française hors ligne est retenue.
 */
class GuideSpeaker(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("guide", Context.MODE_PRIVATE)

    private val tts: TextToSpeech = TextToSpeech(appContext, this)
    private var ready = false
    private var pending: String? = null
    private var selectedVoiceName: String? = prefs.getString("voice", null)

    /** Vitesse de parole (1.0 = normale). Persistée, appliquée à l'init pour
     *  ne pas hériter du réglage TTS système (souvent accéléré par l'utilisateur). */
    var speed: Float = prefs.getFloat("speed", 1.0f)
        private set

    val currentSpeed: Float get() = speed

    /** Nom de la voix sélectionnée (null = voix système par défaut). */
    val currentVoice: String? get() = selectedVoiceName

    // Lecture par phrases (pour la pause/reprise)
    private val phrases = mutableListOf<String>()
    private var phraseIndex = 0
    private var paused = false
    var isPaused: Boolean = false
        private set

    /** Appelé quand la lecture se termine (ou est annulée) — pour resynchroniser l'UI. */
    var onFinished: (() -> Unit)? = null

    /** Clé du texte en cours (monument, article…) pour la mémoire de lecture. */
    private var currentKey: String? = null

    /** Position mémorisée par clé : index de la phrase où reprendre. */
    private val progress = HashMap<String, Int>()

    /** Phrase de reprise de la dernière lecture lancée (0 = depuis le début). */
    var resumedFromPhrase: Int = 0
        private set

    /** Nombre de phrases de la lecture en cours. */
    val phraseCount: Int get() = phrases.size

    /** Voix disponibles : françaises d'abord, puis par qualité décroissante. */
    val voices: List<TtsVoice>
        get() = (tts.voices ?: emptySet())
            .sortedWith(
                compareByDescending<Voice> { it.locale.language == "fr" }
                    .thenByDescending { it.quality }
                    .thenBy { it.name }
            )
            .map {
                TtsVoice(
                    name = it.name,
                    locale = it.locale.toLanguageTag(),
                    quality = it.quality,
                    network = it.isNetworkConnectionRequired
                )
            }

    /**
     * Sans choix explicite, retient la voix française hors ligne de meilleure
     * qualité (les moteurs récents en proposent plusieurs, la voix par défaut
     * n'est pas toujours la plus naturelle).
     */
    private fun pickBestFrenchVoice(): Voice? =
        (tts.voices ?: emptySet())
            .filter { it.locale.language == "fr" && !it.isNetworkConnectionRequired }
            .maxByOrNull { it.quality }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.FRENCH
            applySelectedVoice()
            tts.setSpeechRate(speed) // écrase le réglage système → vitesse contrôlée par l'app
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}

                override fun onDone(id: String?) {
                    playNext()
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    playNext()
                }

                override fun onError(id: String?, errorCode: Int) {
                    playNext()
                }
            })
            pending?.let { speak(it, pendingKey) }
            pending = null
        }
    }

    fun setVoice(name: String) {
        selectedVoiceName = name
        prefs.edit().putString("voice", name).apply()
        if (ready) {
            tts.voices?.firstOrNull { it.name == name }?.let { tts.setVoice(it) }
        }
    }

    /** Règle la vitesse de parole (0.25–2.0) et la persiste. */
    fun setSpeed(rate: Float) {
        speed = rate.coerceIn(0.25f, 2.0f)
        prefs.edit().putFloat("speed", speed).apply()
        if (ready) {
            tts.setSpeechRate(speed)
        }
    }

    private fun applySelectedVoice() {
        val name = selectedVoiceName
        val voice = if (name != null) {
            tts.voices?.firstOrNull { it.name == name }
        } else {
            pickBestFrenchVoice()
        }
        voice?.let { tts.setVoice(it) }
    }

    /**
     * Lit un texte, découpé en phrases pour permettre la pause/reprise.
     * Avec une [key], une lecture précédente du même texte arrêtée en cours de
     * route reprend à la phrase mémorisée ([resumedFromPhrase] > 0).
     */
    fun speak(text: String, key: String? = null) {
        if (!ready) {
            pending = text
            pendingKey = key
            resumedFromPhrase = 0 // pas encore de reprise à annoncer
            return
        }
        rememberProgress()
        phrases.clear()
        phrases.addAll(splitPhrases(text))
        currentKey = key
        val resumeAt = key?.let { progress[it] }?.takeIf { it in 1 until phrases.size } ?: 0
        resumedFromPhrase = resumeAt
        phraseIndex = resumeAt
        paused = false
        isPaused = false
        playNext()
    }

    private var pendingKey: String? = null

    /** Mémorise où en est la lecture en cours (pour une reprise ultérieure). */
    private fun rememberProgress() {
        val key = currentKey ?: return
        // phraseIndex pointe sur la phrase suivante : la phrase en cours est
        // reprise si on s'est arrêté au milieu
        val at = (phraseIndex - 1).coerceAtLeast(0)
        if (phrases.isNotEmpty() && at in 1 until phrases.size) {
            progress[key] = at
        } else {
            progress.remove(key)
        }
    }

    /** Pause : coupe à la fin de la phrase en cours. */
    fun pause() {
        if (phrases.isEmpty() || paused) return
        paused = true
        isPaused = true
        tts.stop() // la phrase en cours n'appelle PAS onDone → reprise à cette phrase
    }

    /** Reprise : continue à la phrase suivante. */
    fun resume() {
        if (!paused) return
        paused = false
        isPaused = false
        playNext()
    }

    /** Bascule pause/reprise. Retourne le nouvel état (true = en pause). */
    fun togglePause(): Boolean {
        if (paused) resume() else pause()
        return paused
    }

    fun stop() {
        rememberProgress()
        paused = false
        isPaused = false
        phraseIndex = phrases.size
        phrases.clear()
        currentKey = null
        tts.stop()
        onFinished?.invoke() // permet à l'UI de cacher la barre de lecture
    }

    private fun splitPhrases(text: String): List<String> =
        speakable(text).split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() }

    /**
     * Retire ce qui se prononce mal : les `=` des titres de section wikitext
     * (lus « égale égale égale »), les puces et les séparateurs.
     */
    private fun speakable(text: String): String = text
        .replace(Regex("(?m)^\\s*=+\\s*(.*?)\\s*=+\\s*$"), "$1.")
        .replace(Regex("[=_*~`|]+"), " ")
        .replace(Regex("(?m)^\\s*[-•·–]\\s+"), "")
        .replace(Regex("[ \\t]{2,}"), " ")

    private fun playNext() {
        if (paused) return
        if (phraseIndex < phrases.size) {
            tts.speak(phrases[phraseIndex], TextToSpeech.QUEUE_FLUSH, null, "guide_$phraseIndex")
            phraseIndex++
        } else {
            // Fin de lecture : plus rien à reprendre pour ce texte
            currentKey?.let { progress.remove(it) }
            currentKey = null
            paused = false
            isPaused = false
            onFinished?.invoke()
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
