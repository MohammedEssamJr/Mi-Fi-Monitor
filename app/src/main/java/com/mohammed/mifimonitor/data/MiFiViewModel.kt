package com.mohammed.mifimonitor.data

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MiFiViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ZteApiClient(application)

    var status by mutableStateOf<DeviceStatus?>(null)
        private set

    var downloadSpeedBps by mutableStateOf(0L)
        private set
    var uploadSpeedBps by mutableStateOf(0L)
        private set

    /** Minutes until 100% while charging. Null = not charging, or not enough data yet. */
    var estimatedMinutesToFull by mutableStateOf<Int?>(null)
        private set

    var isLoggedIn by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var actionMessage by mutableStateOf<String?>(null)
        private set

    private var lastRx = -1L
    private var lastTx = -1L
    private var lastTimestamp = 0L

    // Baseline point (percent + time) marking when the current charging
    // session started, used to derive a %/second charge rate.
    private var chargeBaselinePercent: Int? = null
    private var chargeBaselineTimeMs: Long = 0L

    fun login(password: String) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { api.login(password) }.getOrElse { e ->
                    errorMessage = "Couldn't reach the device: ${e.javaClass.simpleName} — ${e.message}"
                    false
                }
            }
            isLoggedIn = ok
            if (!ok && errorMessage == null) errorMessage = "Login failed — wrong password?"
            if (ok) startPolling()
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                refreshOnce()
                delay(2000)
            }
        }
    }

    private suspend fun refreshOnce() {
        val json = withContext(Dispatchers.IO) {
            runCatching { api.getStatus() }.getOrNull()
        } ?: return

        val newStatus = DeviceStatus.from(json)
        val now = System.currentTimeMillis()

        if (lastRx >= 0 && lastTimestamp > 0) {
            val elapsedSec = (now - lastTimestamp) / 1000.0
            if (elapsedSec > 0) {
                downloadSpeedBps = ((newStatus.sessionRxBytes - lastRx) / elapsedSec).toLong().coerceAtLeast(0)
                uploadSpeedBps = ((newStatus.sessionTxBytes - lastTx) / elapsedSec).toLong().coerceAtLeast(0)
            }
        }
        lastRx = newStatus.sessionRxBytes
        lastTx = newStatus.sessionTxBytes
        lastTimestamp = now
        status = newStatus
        updateChargeEstimate(newStatus, now)
    }

    private fun updateChargeEstimate(newStatus: DeviceStatus, now: Long) {
        if (!newStatus.isCharging || newStatus.batteryPercent < 0) {
            chargeBaselinePercent = null
            estimatedMinutesToFull = null
            return
        }
        if (newStatus.batteryPercent >= 100) {
            estimatedMinutesToFull = 0
            chargeBaselinePercent = null
            return
        }

        val baseline = chargeBaselinePercent
        // (Re)start the baseline if charging just began, or the percent
        // dropped since our last baseline (unplugged and replugged).
        if (baseline == null || newStatus.batteryPercent < baseline) {
            chargeBaselinePercent = newStatus.batteryPercent
            chargeBaselineTimeMs = now
            estimatedMinutesToFull = null
            return
        }

        val elapsedSec = (now - chargeBaselineTimeMs) / 1000.0
        val deltaPercent = newStatus.batteryPercent - baseline

        // Need at least a minute of data and some visible movement before
        // trusting a rate — otherwise the estimate swings wildly.
        estimatedMinutesToFull = if (elapsedSec >= 60 && deltaPercent > 0) {
            val percentPerSecond = deltaPercent / elapsedSec
            val remainingPercent = 100 - newStatus.batteryPercent
            (remainingPercent / percentPerSecond / 60).toInt().coerceAtLeast(1)
        } else {
            null // still gathering data — UI shows "Calculating…"
        }
    }

    fun updateSsid(newSsid: String) = runAction { api.setSsid(newSsid) }
    fun updateWifiPassword(newPassword: String) = runAction { api.setWifiPassword(newPassword) }
    fun updateAdminPassword(oldPassword: String, newPassword: String) =
        runAction { api.changeAdminPassword(oldPassword, newPassword) }

    private fun runAction(block: () -> Boolean) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { block() }.getOrDefault(false) }
            actionMessage = if (ok) "Saved. The device may reboot its Wi-Fi radio." else "That didn't seem to work — the field name may need adjusting."
        }
    }
}
