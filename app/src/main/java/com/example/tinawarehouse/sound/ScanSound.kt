package com.example.tinawarehouse.sound

import com.example.tinawarehouse.data.KeyValueStore
import com.example.tinawarehouse.i18n.tr

/**
 * Âm thanh theo trạng thái quét; người dùng chọn trong Cài đặt. 0 = tắt, -1 = giọng đọc "Block",
 * số dương = mã âm bíp của Android (xem [SoundChoices]). Port từ `ScanSound` (SettingsView.swift).
 */
enum class ScanSound(val title: String, val defaultId: Int) {
    FOUND("Có trong đơn hàng", SoundChoices.ACK),
    TING("Chưa có thông tin (ting)", SoundChoices.PROMPT),
    NO_NAME("Kiện NO NAME", SoundChoices.PROMPT),
    DUPLICATE("Mã trùng", SoundChoices.NACK),
    BLOCKED("Bắt buộc bấm END CODE", SoundChoices.NACK),
    NO_WAREHOUSE("Thiếu mã kho", SoundChoices.NACK),
    REJECTED("Công ty từ chối / lỗi", SoundChoices.VOICE),
    BLOCK("Kiện đang bị block", SoundChoices.VOICE),
    BUSINESS("Hàng kinh doanh (DOFA)", SoundChoices.BEEP2);

    /** Khoá lưu cài đặt (giống iOS: snd_<tên>; rejected dùng khoá v2). */
    val key: String get() = if (this == REJECTED) "snd_rejected2" else "snd_" + name.lowercase()
    val label: String get() = tr(title)

    fun current(prefs: KeyValueStore): Int = prefs.getInt(key) ?: defaultId

    companion object {
        fun resetAll(prefs: KeyValueStore) = entries.forEach { prefs.putInt(it.key, null) }
    }
}

/** Danh sách âm có thể chọn. Số là mã của ta (ổn định, lưu vào cài đặt); ánh xạ sang ToneGenerator nằm ở `SoundPlayer`. */
object SoundChoices {
    const val OFF = 0
    const val VOICE = -1
    const val BEEP = 1
    const val BEEP2 = 2
    const val ACK = 3
    const val NACK = 4
    const val PROMPT = 5
    const val PIP = 6
    const val RADIO_ACK = 7
    const val ERROR = 8
    const val CONGESTION = 9
    const val BUSY = 10
    const val CALL_WAITING = 11
    const val CONFIRM = 12
    const val ABBR_ALERT = 13
    const val ALERT_LITE = 14
    const val CALL_GUARD = 15

    val all: List<Int> = listOf(OFF, VOICE) + (BEEP..CALL_GUARD).toList()

    fun label(id: Int): String = when (id) {
        OFF -> tr("Tắt")
        VOICE -> tr("Giọng đọc \"Block\"")
        else -> tr("Âm {0}", id)
    }
}
