package com.mohammed.mifimonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohammed.mifimonitor.data.AppLog
import com.mohammed.mifimonitor.data.MiFiViewModel
import com.mohammed.mifimonitor.data.toReadableSize
import com.mohammed.mifimonitor.ui.theme.MiFiMonitorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLog.init(applicationContext)
        setContent {
            MiFiMonitorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MiFiViewModel = viewModel()) {
    var showLogs by remember { mutableStateOf(false) }
    if (showLogs) {
        LogsScreen(onBack = { showLogs = false })
        return
    }
    if (!vm.isLoggedIn) {
        LoginScreen(vm, onViewLogs = { showLogs = true })
    } else {
        var tab by remember { mutableStateOf(0) }
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("MiFi Monitor", fontWeight = FontWeight.SemiBold) },
                actions = {
                    val online = vm.status?.connected == true
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (online) Color(0xFF17B897) else Color(0xFFFF6B6B))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (online) "Online" else "Offline",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            )
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Dashboard, contentDescription = null) },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                    label = { Text("Wi-Fi") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    label = { Text("Login") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Article, contentDescription = null) },
                    label = { Text("Logs") }
                )
            }
            when (tab) {
                0 -> DashboardScreen(vm)
                1 -> WifiSettingsScreen(vm)
                2 -> LoginSettingsScreen(vm)
                3 -> LogsScreen(onBack = null)
            }
        }
    }
}

@Composable
fun LoginScreen(vm: MiFiViewModel, onViewLogs: () -> Unit) {
    var password by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.Router,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text("Connect to your MF937", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Make sure your phone's Wi-Fi is connected to the MiFi hotspot first.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Admin password") },
            leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.login(password) }, modifier = Modifier.fillMaxWidth()) {
            Text("Connect")
        }
        vm.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
        TextButton(onClick = onViewLogs) {
            Icon(Icons.Filled.Article, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("View connection logs")
        }
    }
}

@Composable
fun DashboardScreen(vm: MiFiViewModel) {
    val status = vm.status
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (status == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Loading device status…")
            }
            return@Column
        }

        SectionLabel("Device")
        StatRow(Icons.Filled.BatteryChargingFull, "Battery", "${status.batteryPercent}%" + if (status.isCharging) " · charging" else "")
        if (status.isCharging && status.batteryPercent < 100) {
            StatRow(
                Icons.Filled.BatteryChargingFull,
                "Time to full",
                vm.estimatedMinutesToFull?.let { formatMinutes(it) } ?: "Calculating…"
            )
        }
        StatRow(Icons.Filled.SignalCellularAlt, "Signal", "${status.signalBars}/5 bars")
        StatRow(Icons.Filled.NetworkCell, "Network", "${status.networkType} — ${status.carrierName}")
        StatRow(Icons.Filled.Public, "Connection", if (status.connected) "Online (${status.wanIp})" else "Offline")
        StatRow(Icons.Filled.PhoneAndroid, "Connected devices", "${status.connectedDevices}")
        StatRow(Icons.Filled.Wifi, "Wi-Fi network", status.ssid)

        Spacer(Modifier.height(4.dp))
        SectionLabel("Live speed")
        StatRow(Icons.Filled.Download, "Download", "${(vm.downloadSpeedBps * 8 / 1000)} kbps")
        StatRow(Icons.Filled.Upload, "Upload", "${(vm.uploadSpeedBps * 8 / 1000)} kbps")

        Spacer(Modifier.height(4.dp))
        SectionLabel("Data usage")
        StatRow(Icons.Filled.DataUsage, "This session", status.totalSessionBytes.toReadableSize())
        StatRow(Icons.Filled.CalendarMonth, "This billing cycle", status.totalMonthlyBytes.toReadableSize())
        Text(
            "\"Billing cycle\" comes straight from the device's own counter — confirm it resets on the same date your WE package renews.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun WifiSettingsScreen(vm: MiFiViewModel) {
    var ssid by remember { mutableStateOf(vm.status?.ssid ?: "") }
    var password by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionLabel("Network name")
        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text("SSID") },
            leadingIcon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.updateSsid(ssid) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save network name")
        }

        Divider()

        SectionLabel("Wi-Fi password")
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("New password") },
            leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = { vm.updateWifiPassword(password) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save password")
        }

        vm.actionMessage?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun LoginSettingsScreen(vm: MiFiViewModel) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val mismatch = confirmPassword.isNotEmpty() && newPassword != confirmPassword

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionLabel("Router admin login")
        Text(
            "Changes the password used to log into this app and http://192.168.8.1.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = oldPassword,
            onValueChange = { oldPassword = it },
            label = { Text("Current password") },
            leadingIcon = { Icon(Icons.Filled.Key, contentDescription = null) },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("New password") },
            leadingIcon = { Icon(Icons.Filled.VpnKey, contentDescription = null) },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm new password") },
            isError = mismatch,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        if (mismatch) {
            Text("Passwords don't match", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { vm.updateAdminPassword(oldPassword, newPassword) },
            enabled = !mismatch && oldPassword.isNotEmpty() && newPassword.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Change login password")
        }
        vm.actionMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun formatMinutes(totalMinutes: Int): String {
    if (totalMinutes < 60) return "~${totalMinutes} min"
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    return if (mins == 0) "~${hours}h" else "~${hours}h ${mins}m"
}

@Composable
fun LogsScreen(onBack: (() -> Unit)?) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val entries = com.mohammed.mifimonitor.data.AppLog.entries

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
            Text("Connection logs", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(com.mohammed.mifimonitor.data.AppLog.readFullLog()))
            }) { Text("Copy all") }
            TextButton(onClick = { com.mohammed.mifimonitor.data.AppLog.clear() }) { Text("Clear") }
        }
        Divider()
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No log entries yet — try connecting, then come back here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                entries.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
fun StatRow(icon: ImageVector, label: String, value: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        }
    }
}
