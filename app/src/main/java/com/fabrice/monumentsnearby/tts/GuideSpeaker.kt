package com.fabrice.monumentsnearby.tts

import android.content.Context
import android.speech.tts.TextToSpeech
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
 */
class GuideSpeaker(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("guide", Context.MODE_PRIVATE)

    private val tts: TextToSpeech = TextToSpeech(appContext, this)
    private var ready = false
    private var pending: String? = null
    private var selectedVoiceName: String? = prefs.getString("voice", null)

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

    private fun applySelectedVoice() {
        val name = selectedVoiceName ?: return
        tts.voices?.firstOrNull { it.name == name }?.let { tts.setVoice(it) }
    }

    fun speak(text: String) {
        if (ready) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "guide")
        } else {
            pending = text
        }
    }

    fun stop() = tts.stop()

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
