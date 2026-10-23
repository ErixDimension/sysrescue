package com.terangbulan.sysrescue.util

import kotlinx.coroutines.flow.MutableStateFlow

object DebugLogger {
    private val _logs = MutableStateFlow("")
    val logs = _logs
    private val _status = MutableStateFlow("Menunggu device...")
    val status = _status

    @Synchronized
    fun log(msg: String) {
        val time = formatTime(System.currentTimeMillis())
        val current = _logs.value
        val newLog = "$current[$time] $msg\n"
        // Batasi panjang log maksimal 3000 karakter agar memori aman
        _logs.value = if (newLog.length > 3000) newLog.takeLast(3000) else newLog
    }

    @Synchronized
    fun setStatus(s: String) {
        _status.value = s
    }

    @Synchronized
    fun clear() {
        _logs.value = ""
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        val h = (s / 3600) % 24
        val m = (s / 60) % 60
        val sec = s % 60
        val millis = ms % 1000
        return String.format("%02d:%02d:%02d.%03d", h, m, sec, millis)
    }
}
