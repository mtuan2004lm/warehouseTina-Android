package com.example.tinawarehouse.sound

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import com.example.tinawarehouse.R
import com.example.tinawarehouse.data.KeyValueStore

/** Phát âm quét: âm bíp của Android (ToneGenerator) hoặc giọng đọc "Block" (res/raw/block_voice.m4a). */
class SoundPlayer(private val context: Context, private val prefs: KeyValueStore) {
    private var tone: ToneGenerator? = null
    private var voice: MediaPlayer? = null

    /** Phát âm theo mã đã chọn (0 = tắt, -1 = giọng đọc). */
    fun play(id: Int) {
        when {
            id == SoundChoices.OFF -> Unit
            id == SoundChoices.VOICE -> playVoice()
            else -> playTone(id)
        }
    }

    /** Phát âm người dùng đã chọn cho một trạng thái quét. */
    fun play(sound: ScanSound) = play(sound.current(prefs))

    private fun playTone(id: Int) {
        val type = toneFor(id) ?: return
        runCatching {
            val tg = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 100).also { tone = it }
            tg.startTone(type, 250)
        }
    }

    private fun playVoice() {
        runCatching {
            voice?.release()
            voice = MediaPlayer.create(context, R.raw.block_voice)?.also { mp ->
                mp.setOnCompletionListener { it.release(); if (voice === it) voice = null }
                mp.start()
            }
        }
    }

    fun release() {
        tone?.release(); tone = null
        voice?.release(); voice = null
    }

    private fun toneFor(id: Int): Int? = when (id) {
        SoundChoices.BEEP -> ToneGenerator.TONE_PROP_BEEP
        SoundChoices.BEEP2 -> ToneGenerator.TONE_PROP_BEEP2
        SoundChoices.ACK -> ToneGenerator.TONE_PROP_ACK
        SoundChoices.NACK -> ToneGenerator.TONE_PROP_NACK
        SoundChoices.PROMPT -> ToneGenerator.TONE_PROP_PROMPT
        SoundChoices.PIP -> ToneGenerator.TONE_SUP_PIP
        SoundChoices.RADIO_ACK -> ToneGenerator.TONE_SUP_RADIO_ACK
        SoundChoices.ERROR -> ToneGenerator.TONE_SUP_ERROR
        SoundChoices.CONGESTION -> ToneGenerator.TONE_SUP_CONGESTION
        SoundChoices.BUSY -> ToneGenerator.TONE_SUP_BUSY
        SoundChoices.CALL_WAITING -> ToneGenerator.TONE_SUP_CALL_WAITING
        SoundChoices.CONFIRM -> ToneGenerator.TONE_CDMA_CONFIRM
        SoundChoices.ABBR_ALERT -> ToneGenerator.TONE_CDMA_ABBR_ALERT
        SoundChoices.ALERT_LITE -> ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE
        SoundChoices.CALL_GUARD -> ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
        else -> null
    }
}
