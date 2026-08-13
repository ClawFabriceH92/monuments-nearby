package com.fabrice.monumentsnearby.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Audioguide hors-ligne : TTS Android natif, aucune dépendance réseau.
 * Bufferise le texte si l'utilisateur clique avant l'initialisation du moteur.
 */
class GuideSpeaker(context: Context) : TextToSpeech.OnInitListener {

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var pending: String? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.FRENCH
            pending?.let { tts.speak(it, TextToSpeech.QUEUE_FLUSH, null, "guide") }
            pending = null
        }
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
