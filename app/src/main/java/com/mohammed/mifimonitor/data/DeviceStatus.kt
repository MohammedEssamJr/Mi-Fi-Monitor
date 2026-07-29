package com.mohammed.mifimonitor.data

import org.json.JSONObject

data class DeviceStatus(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val signalBars: Int,          // 0-5
    val networkType: String,      // e.g. "LTE", "3G"
    val carrierName: String,
    val wanIp: String,
    val connected: Boolean,
    val sessionRxBytes: Long,     // this session, resets on reconnect
    val sessionTxBytes: Long,
    val monthlyRxBytes: Long,     // current billing-cycle usage, if firmware exposes it
    val monthlyTxBytes: Long,
    val connectedDevices: Int,
    val ssid: String
) {
    val totalMonthlyBytes: Long get() = monthlyRxBytes + monthlyTxBytes
    val totalSessionBytes: Long get() = sessionRxBytes + sessionTxBytes

    companion object {
        fun from(json: JSONObject): DeviceStatus = DeviceStatus(
            batteryPercent = json.optInt("battery_pers", json.optInt("battery_vol_percent", -1)),
            isCharging = json.optString("battery_charging") == "1",
            signalBars = json.optInt("signalbar", -1),
            networkType = json.optString("network_type", "?"),
            carrierName = json.optString("network_provider", "?"),
            wanIp = json.optString("wan_ipaddr", "-"),
            connected = json.optString("ppp_status") == "ppp_connected" ||
                json.optString("ppp_status").contains("connect", ignoreCase = true),
            sessionRxBytes = json.optString("realtime_rx_bytes", "0").toLongOrNull() ?: 0L,
            sessionTxBytes = json.optString("realtime_tx_bytes", "0").toLongOrNull() ?: 0L,
            monthlyRxBytes = json.optString("monthly_rx_bytes", "0").toLongOrNull() ?: 0L,
            monthlyTxBytes = json.optString("monthly_tx_bytes", "0").toLongOrNull() ?: 0L,
            connectedDevices = json.optInt("station_num", 0),
            ssid = json.optString("wifi_ssid1", "-")
        )
    }
}

/** Formats bytes as a human string (KB/MB/GB), rounded to 2 decimals. */
fun Long.toReadableSize(): String {
    if (this < 1024) return "$this B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = this.toDouble()
    var unitIndex = -1
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.2f %s".format(value, units[unitIndex])
}
