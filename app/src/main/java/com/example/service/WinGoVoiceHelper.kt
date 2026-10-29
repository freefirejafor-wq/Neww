package com.example.service

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import com.example.data.model.QuantumPrediction
import java.util.Locale

/**
 * Built-in Android TextToSpeech + Countdown Tone Engine for WinGo 30S:
 * - Announces new 30S signals automatically ("Signal BIG, Number 6 or 8, Confidence 92 percent")
 * - Announces 7-second lock-in warning + subtle countdown beep at 5s..1s
 * - Announces verified WIN results
 */
object WinGoVoiceHelper {
    private const val PREFS_NAME = "wingo_voice_prefs"
    private const val KEY_VOICE_ENABLED = "voice_alert_enabled"

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var lastAnnouncedSignalPeriod: String = ""
    private var lastLockWarningPeriod: String = ""
    private var lastAnnouncedWinPeriod: String = ""
    private var lastBeepSecond: Int = -1

    fun isVoiceEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_VOICE_ENABLED, true)
    }

    fun setVoiceEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_VOICE_ENABLED, enabled)
            .apply()
        if (!enabled) {
            try {
                tts?.stop()
            } catch (_: Throwable) {}
        } else {
            ensureInitialized(context)
        }
    }

    fun ensureInitialized(context: Context) {
        if (tts != null) return
        try {
            val appCtx = context.applicationContext
            tts = TextToSpeech(appCtx) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsReady = true
                    try {
                        val bnLocale = Locale("bn", "BD")
                        val res = tts?.setLanguage(bnLocale)
                        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                            tts?.language = Locale.US
                        }
                        tts?.setSpeechRate(1.08f)
                    } catch (_: Throwable) {}
                }
            }
        } catch (_: Throwable) {}
    }

    fun onNewPredictionReady(
        context: Context,
        prediction: QuantumPrediction,
        activeLevel: Int,
        betAmount: Int
    ) {
        if (!isVoiceEnabled(context)) return
        ensureInitialized(context)
        val period = prediction.nextPeriod
        if (period.isBlank() || period == "----" || period == lastAnnouncedSignalPeriod) return
        lastAnnouncedSignalPeriod = period

        val shortP = period.takeLast(3)
        val isTrap = prediction.riskBadge.contains("TRAP", ignoreCase = true) ||
            prediction.riskBadge.contains("WAIT", ignoreCase = true)

        val speechText = if (isTrap) {
            "Round $shortP. Market trap detected. Wait or play level 1 small stake on ${prediction.predictedSize}, number ${prediction.primaryNumber}."
        } else {
            "Round $shortP. VIP Signal ${prediction.predictedSize}. Number ${prediction.primaryNumber} and ${prediction.backupNumber}. Confidence ${prediction.confidence} percent. Level $activeLevel, bet $betAmount."
        }
        speak(speechText)
    }

    fun onCountdownTick(
        context: Context,
        remainingSeconds: Int,
        prediction: QuantumPrediction,
        activeLevel: Int
    ) {
        if (!isVoiceEnabled(context)) return
        val period = prediction.nextPeriod
        if (period.isBlank() || period == "----") return

        if (remainingSeconds == 8 && lastLockWarningPeriod != period) {
            lastLockWarningPeriod = period
            val isTrap = prediction.riskBadge.contains("TRAP", ignoreCase = true)
            val msg = if (isTrap) {
                "8 seconds left. Caution, trap round."
            } else {
                "8 seconds left. Lock ${prediction.predictedSize}, number ${prediction.primaryNumber}, level $activeLevel."
            }
            speak(msg)
        } else if (remainingSeconds in 1..4 && lastBeepSecond != remainingSeconds) {
            lastBeepSecond = remainingSeconds
            playShortBeep(isFinal = remainingSeconds == 1)
        }
    }

    fun onRoundWon(
        context: Context,
        period: String,
        actualSize: String,
        actualNumber: Int?,
        numberHit: Boolean,
        streak: Int
    ) {
        if (!isVoiceEnabled(context)) return
        if (period.isBlank() || period == lastAnnouncedWinPeriod) return
        lastAnnouncedWinPeriod = period

        val jackpot = if (numberHit) "Jackpot number $actualNumber hit! " else ""
        speak("Boom! Win confirmed on $actualSize. $jackpot Win streak $streak.")
    }

    private fun speak(text: String) {
        if (!isTtsReady) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wingo_tts_${System.currentTimeMillis()}")
        } catch (_: Throwable) {}
    }

    private fun playShortBeep(isFinal: Boolean) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 55)
            val tone = if (isFinal) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP
            toneGen.startTone(tone, if (isFinal) 140 else 75)
        } catch (_: Throwable) {}
    }
}
