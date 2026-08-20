package com.fabrice.monumentsnearby.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/** Une voix TTS disponible sur l'appareil. */
data class TtsVoice(
    val name: String,
    val locale: String
)

/**
 * Audioguide hors-ligne : TTS Android natif, aucune dépendance réseau.
 * - Bufferise le texte si l'utilisateur clique avant l'initialisation du moteur.
 * - Voix sélectionnable (voix système), choix persisté en SharedPreferences.
 * - Pause/reprise : le texte est découpé en phrases ; la pause coupe à la fin
 *   de la phrase en cours, la reprise continue à la phrase suivante.
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

    /** Voix disponibles, françaises en premier. */
    val voices: List<TtsVoice>
        get() = (tts.voices ?: emptySet())
            .sortedWith(compareByDescending<Voice> { it.locale.language == "fr" }.thenBy { it.name })
            .map { TtsVoice(name = it.name, locale = it.locale.toLanguageTag()) }

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
            pending?.let { speak(it) }
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
        val name = selectedVoiceName ?: return
        tts.voices?.firstOrNull { it.name == name }?.let { tts.setVoice(it) }
    }

    /** Lit un texte, découpé en phrases pour permettre la pause/reprise. */
    fun speak(text: String) {
        if (!ready) {
            pending = text
            return
        }
        phrases.clear()
        phrases.addAll(splitPhrases(text))
        phraseIndex = 0
        paused = false
        isPaused = false
        playNext()
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
        paused = false
        isPaused = false
        phraseIndex = phrases.size
        phrases.clear()
        tts.stop()
        onFinished?.invoke() // permet à l'UI de cacher la barre de lecture
    }

    private fun splitPhrases(text: String): List<String> =
        text.split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() }

    private fun playNext() {
        if (paused) return
        if (phraseIndex < phrases.size) {
            tts.speak(phrases[phraseIndex], TextToSpeech.QUEUE_FLUSH, null, "guide_$phraseIndex")
            phraseIndex++
        } else {
            // Fin de lecture
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
