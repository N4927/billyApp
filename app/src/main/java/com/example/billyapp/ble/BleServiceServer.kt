package com.example.billyapp.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.billyapp.core.Constants
import com.example.billyapp.core.RotatingIdGenerator
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

// MARK: - Encounter
data class Encounter(
    var name: String,
    var count: Int
)

class BleServiceServer : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var generator: RotatingIdGenerator

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private val advCb = object : AdvertiseCallback() {}
    private var scanCb: ScanCallback? = null

    private var isActive = true

    // 🔹 Encounters salvati (thread-safe)
    private val encounters = ConcurrentHashMap<String, Encounter>()
    private val lock = Mutex()

    override fun onCreate() {
        super.onCreate()
        startForegroundNoti("Initializing BLE service...")

        val secret = "public-secret-placeholder".toByteArray()
        generator = RotatingIdGenerator(secret)

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        advertiser = adapter.bluetoothLeAdvertiser
        scanner = adapter.bluetoothLeScanner

        scope.launch { loopRotateAndAdvertise() }
        startScan()
    }

    override fun onDestroy() {
        isActive = false
        stopAdvertising()
        stopScan()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: android.content.Intent?): IBinder? = null

    private fun startForegroundNoti(contentText: String = "Advertising & scanning") {
        val channelId = "ble_channel"
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        "BLE activity",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
        val noti: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BillyApp")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
        startForeground(1, noti)
    }

    private suspend fun loopRotateAndAdvertise() {
        while (isActive) {
            stopAdvertising()
            val idBytes = generator.currentIdBytes()
            startAdvertising(idBytes)
            delay(Constants.ROTATION_SECONDS * 1000L)
        }
        Log.d("BleServiceServer", "Stopped advertising due to service inactive")
        updateNotification("Pubblicazione BLE fermata")
    }

    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startAdvertising(idBytes: ByteArray) {
        if (!hasBluetoothPermissions()) return
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) return
            val adv = advertiser ?: return

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(false)
                .build()

            val data = AdvertiseData.Builder()
                .addServiceUuid(Constants.SERVICE_UUID)
                .addServiceData(Constants.SERVICE_UUID, idBytes)
                .setIncludeDeviceName(true)
                .build()

            adv.startAdvertising(settings, data, advCb)
            Log.d("BleServiceServer", "📡 Pubblicazione BLE con id: ${idBytes.joinToString("") { "%02x".format(it) }}")
            updateNotification("Pubblicazione BLE attiva")

        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "Errore permessi BLE: ${e.message}")
        }
    }

    private fun stopAdvertising() {
        if (!hasBluetoothPermissions()) return
        try {
            advertiser?.stopAdvertising(advCb)
            Log.d("BleServiceServer", "Stop pubblicazione BLE")
            updateNotification("Pubblicazione BLE fermata")
        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "Errore permessi BLE: ${e.message}")
        }
    }

    private fun updateNotification(text: String) {
        val channelId = "ble_channel"
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BillyApp")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(1, notification)
    }

    private fun startScan() {
        if (!hasBluetoothPermissions()) return
        try {
            val sc = scanner ?: return
            val filter = ScanFilter.Builder()
                .setServiceUuid(Constants.SERVICE_UUID)
                .build()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanCb = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val record = result.scanRecord ?: return
                    val payload = record.getServiceData(Constants.SERVICE_UUID) ?: return
                    val now = System.currentTimeMillis() / 1000
                    val idHex = payload.joinToString("") { "%02x".format(it) }

                    scope.launch {
                        val resolvedName = resolveNameFromServer(idHex, now)
                        if (resolvedName != null) {
                            Log.d("BleServiceServer", "✅ Rilevato: $resolvedName")
                            saveEncounter(resolvedName)
                        } else {
                            Log.d("BleServiceServer", "⚠️ Dispositivo sconosciuto")
                            saveEncounter("Sconosciuto")
                        }
                    }
                }
            }
            sc.startScan(listOf(filter), settings, scanCb)
        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "Errore permessi BLE: ${e.message}")
        }
    }

    private fun stopScan() {
        if (!hasBluetoothPermissions()) return
        try {
            scanner?.stopScan(scanCb)
        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "Errore permessi BLE: ${e.message}")
        }
        scanCb = null
    }

    // 🔹 Chiamata al server
    private suspend fun resolveNameFromServer(idHex: String, timestampSec: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://mockserver.example.com/api/resolve")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val json = JSONObject()
                json.put("id", idHex)
                json.put("timestamp", timestampSec)

                OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val obj = JSONObject(response)
                    return@withContext obj.optString("name", null)
                } else {
                    Log.e("BleServiceServer", "❌ Errore server: ${conn.responseCode}")
                }
            } catch (e: Exception) {
                Log.e("BleServiceServer", "❌ Errore rete: ${e.message}")
            }
            null
        }
    }

    // 🔹 Gestione Encounter
    private suspend fun saveEncounter(name: String) {
        lock.withLock {
            val encounter = encounters[name]
            if (encounter != null) {
                encounter.count += 1
            } else {
                encounters[name] = Encounter(name, 1)
            }
        }
    }

    private fun bytesToUuid(bytes: ByteArray): UUID {
        val bb = ByteBuffer.wrap(bytes.copyOfRange(0, 16))
        return UUID(bb.long, bb.long)
    }
}
