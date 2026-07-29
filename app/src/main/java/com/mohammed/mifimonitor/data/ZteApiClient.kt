package com.mohammed.mifimonitor.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Thrown when the device responds 401 — wrong admin password. */
class AuthFailedException(message: String) : Exception(message)

/**
 * Talks to the MF937's built-in web admin API. This is the same JSON API
 * the stock http://192.168.8.1 page uses under the hood — there is no
 * official SDK, so this is a thin HTTP client around it.
 *
 * Auth: this unit gates the whole admin site with standard HTTP Basic
 * Authentication (fixed username "admin", browser-native login popup —
 * not a page-rendered form). Every request below sends that header.
 *
 * IMPORTANT — verify field names against your unit:
 * The exact `cmd` names below are the common ones shared across the ZTE
 * MF910/MF823/MF937 firmware family. Some carrier firmware builds rename
 * a few fields. To confirm/adjust yours:
 *   1. On a laptop on the MiFi's Wi-Fi, open http://192.168.8.1 and log in.
 *   2. Open DevTools > Network, refresh the status page.
 *   3. Look at the request to goform_get_cmd_process — the `cmd=` list in
 *      the URL and the JSON keys in the response are exactly what to put
 *      in STATUS_CMDS / the field names in [DeviceStatus.from].
 *
 * Network note: requests are explicitly bound to the phone's Wi-Fi network
 * (not "whatever network Android picks"), in case mobile data is also on.
 */
class ZteApiClient(
    context: Context,
    private val baseUrl: String = "http://192.168.8.1"
) {

    private val appContext = context.applicationContext

    /** Set once login() succeeds; sent as the Basic Auth password on every request after. */
    private var currentPassword: String = ""

    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // The MF937's built-in web server is a tiny embedded httpd that
            // doesn't handle HTTP keep-alive cleanly — it can silently drop
            // a connection OkHttp thinks is still reusable, which surfaces
            // as "unexpected end of stream". Disabling the connection pool
            // forces a fresh TCP connection per request, avoiding that.
            .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.NANOSECONDS))

        wifiNetwork()?.let { network ->
            builder.socketFactory(network.socketFactory)
        }
        builder.build()
    }

    private val referer = "$baseUrl/index.html"

    /** Finds the phone's currently connected Wi-Fi network specifically (not cellular). */
    private fun wifiNetwork(): Network? {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        return cm.allNetworks.firstOrNull { network ->
            val caps = cm.getNetworkCapabilities(network)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
    }

    // Split into small groups rather than one long comma list — the MF937's
    // embedded web server appears to have a small request buffer, and a
    // single long query string produced a truncated/corrupt response
    // ("unexpected end of stream") on the very first connection attempt.
    private val STATUS_CMD_GROUPS = listOf(
        listOf("battery_charging", "battery_vol_percent", "battery_pers", "signalbar"),
        listOf("network_type", "network_provider", "rssi", "ppp_status"),
        listOf("wan_ipaddr", "realtime_tx_bytes", "realtime_rx_bytes", "realtime_time"),
        listOf("monthly_tx_bytes", "monthly_rx_bytes"),
        listOf("wifi_chip_type", "wifi_ssid1", "station_num", "loginfo")
    )

    // Smallest possible request, used only to test reachability + the
    // password during login — kept separate from the full status pull so
    // a long-URL problem there doesn't also block login itself.
    private val PING_CMD = "signalbar"

    private fun authHeader(): String = Credentials.basic("admin", currentPassword)

    private val SENSITIVE_KEYS = setOf("password", "oldpassword", "newpassword", "confirmpassword", "wpapsk")

    private fun maskSensitive(params: Map<String, String>): Map<String, String> =
        params.mapValues { (k, v) -> if (k.lowercase() in SENSITIVE_KEYS) "••••" else v }

    private fun get(cmd: String, extraParams: String = ""): JSONObject {
        val url = "$baseUrl/goform/goform_get_cmd_process?isTest=false&cmd=$cmd&multi_data=1$extraParams"
        val request = Request.Builder()
            .url(url)
            .header("Referer", referer)
            .header("Connection", "close")
            .header("Authorization", authHeader())
            .build()
        AppLog.log("HTTP", "GET $url")
        try {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string() ?: "{}"
                AppLog.log("HTTP", "-> ${resp.code} ${body.take(500)}")
                if (resp.code == 401) throw AuthFailedException("Wrong admin password")
                return JSONObject(body)
            }
        } catch (e: Exception) {
            AppLog.logError("HTTP", e)
            throw e
        }
    }

    private fun post(formPairs: Map<String, String>): JSONObject {
        val bodyBuilder = FormBody.Builder()
        formPairs.forEach { (k, v) -> bodyBuilder.add(k, v) }
        val request = Request.Builder()
            .url("$baseUrl/goform/goform_set_cmd_process")
            .header("Referer", referer)
            .header("Connection", "close")
            .header("Authorization", authHeader())
            .post(bodyBuilder.build())
            .build()
        AppLog.log("HTTP", "POST ${formPairs["goformId"]} params=${maskSensitive(formPairs)}")
        try {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string() ?: "{}"
                AppLog.log("HTTP", "-> ${resp.code} ${body.take(500)}")
                if (resp.code == 401) throw AuthFailedException("Wrong admin password")
                return runCatching { JSONObject(body) }.getOrDefault(JSONObject())
            }
        } catch (e: Exception) {
            AppLog.logError("HTTP", e)
            throw e
        }
    }

    /**
     * "Logs in" by trying the password as HTTP Basic Auth against a real
     * status request — there's no separate login endpoint here, since the
     * whole site is gated by Basic Auth rather than a form/session.
     * Returns false only for a wrong password (401); network problems
     * (unreachable device, dropped connection, etc.) are thrown so the
     * caller can show the real error instead of a generic "wrong password".
     */
    fun login(password: String): Boolean {
        currentPassword = password
        AppLog.log("Auth", "Attempting login (password masked)")
        return try {
            get(PING_CMD)
            AppLog.log("Auth", "Login accepted")
            true
        } catch (e: AuthFailedException) {
            AppLog.log("Auth", "Login rejected: wrong password")
            currentPassword = ""
            false
        }
    }

    /** Pulls status by issuing several small requests and merging the results. */
    fun getStatus(): JSONObject {
        val merged = JSONObject()
        for (group in STATUS_CMD_GROUPS) {
            val partial = get(group.joinToString(","))
            partial.keys().forEach { key -> merged.put(key, partial.get(key)) }
        }
        return merged
    }

    /** Renames the Wi-Fi network. */
    fun setSsid(newSsid: String): Boolean {
        val result = post(
            mapOf(
                "isTest" to "false",
                "goformId" to "WIFI_SSID_SET",
                "SSID" to newSsid,
                "notCallback" to "true"
            )
        )
        return result.optString("result") != "" // adjust once you see the real response
    }

    /** Changes the Wi-Fi password (WPA2-PSK). */
    fun setWifiPassword(newPassword: String): Boolean {
        val result = post(
            mapOf(
                "isTest" to "false",
                "goformId" to "WIFI_WPA_PSK_SET",
                "WPAPSK" to newPassword,
                "notCallback" to "true"
            )
        )
        return result.optString("result") != ""
    }

    /**
     * Changes the router admin login password (the one used at
     * http://192.168.8.1 and in this app's login screen).
     * Common ZTE param name is PASSWORD_CHANGE with Old/New/Confirm fields —
     * verify this one via DevTools same as the others, it's one of the
     * more carrier-varied endpoints. Since auth here is HTTP Basic (not a
     * session), a successful change also means currentPassword must be
     * updated locally or the next request will 401.
     */
    fun changeAdminPassword(oldPassword: String, newPassword: String): Boolean {
        val result = post(
            mapOf(
                "isTest" to "false",
                "goformId" to "PASSWORD_CHANGE",
                "oldPassword" to oldPassword,
                "newPassword" to newPassword,
                "confirmPassword" to newPassword
            )
        )
        val success = result.optString("result") != ""
        if (success) currentPassword = newPassword
        return success
    }
}
