package com.example.billyapp.ble

import android.app.*
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
import com.example.billyapp.core.Encounter
import com.example.billyapp.core.EncounterBus
import com.example.billyapp.core.ProfileManager
import com.example.billyapp.core.RotatingIdGenerator
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.*

class BleServiceServer : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var generator: RotatingIdGenerator

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private val advCb = object : AdvertiseCallback() {}
    private var scanCb: ScanCallback? = null

    private var isActive = true

    // 🔹 Cache slot-based
    private val slotCache = mutableMapOf<String, SlotEncounter>()
    private val SLOT_DURATION = Constants.ROTATION_SECONDS // stessa durata ID rotante

    data class SlotEncounter(
        val idHex: String,
        var firstSeen: Long,
        var lastSeen: Long,
        var count: Int,
        var rssi: Int
    )

    override fun onCreate() {
        super.onCreate()
        startForegroundNoti("Initializing BLE service...")

        // Profilo locale
        val mySecret = ProfileManager.getSecretForBle(this, length = 8)
        generator = RotatingIdGenerator(mySecret, lengthBytes = 8)

        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        advertiser = adapter.bluetoothLeAdvertiser
        scanner = adapter.bluetoothLeScanner

        scope.launch { loopRotateAndAdvertise() }
        startScan()
        startBatchUploader()
    }

    override fun onDestroy() {
        isActive = false
        stopAdvertising()
        stopScan()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: android.content.Intent?): IBinder? = null

    // Foreground notification
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

            val ts = System.currentTimeMillis() / 1000
            val rotatingId = generator.currentIdBytes(ts)

            // payload = [timestamp 8B] + [rotatingId 8B]
            val payload = ByteBuffer.allocate(8 + rotatingId.size)
                .putLong(ts)
                .put(rotatingId)
                .array()

            startAdvertising(payload)

            delay(Constants.ROTATION_SECONDS * 1000L)
        }
        Log.d("BleServiceServer", "Stopped advertising")
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

    private fun startAdvertising(payload: ByteArray) {
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
                .addServiceData(Constants.SERVICE_UUID, payload)
                .build()

            adv.startAdvertising(settings, data, advCb)
            Log.d("BleServiceServer", "📡 Advertising payload size=${payload.size}")
            updateNotification("Pubblicazione BLE attiva")

        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "Errore permessi BLE: ${e.message}")
        }
    }

    private fun stopAdvertising() {
        if (!hasBluetoothPermissions()) return
        try {
            advertiser?.stopAdvertising(advCb)
            Log.d("BleServiceServer", "Stop advertising")
            updateNotification("Advertising stopped")
        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "Errore stop adv: ${e.message}")
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
                    if (payload.size < 16) return // 8B ts + 8B id

                    val bb = ByteBuffer.wrap(payload)
                    val ts = bb.long
                    val idBytes = payload.copyOfRange(8, payload.size)
                    val idHex = idBytes.joinToString("") { "%02x".format(it) }

                    updateSlotCache(idHex, ts, result.rssi)
                }
            }
            sc.startScan(listOf(filter), settings, scanCb)
            Log.d("BleServiceServer", "✅ Started scanning")
        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "Errore startScan: ${e.message}")
        }
    }

    private fun stopScan() {
        if (!hasBluetoothPermissions()) return
        try {
            scanner?.stopScan(scanCb)
        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "Errore stopScan: ${e.message}")
        }
        scanCb = null
    }

    // 🔹 Aggiorna la cache slot-based
    private fun updateSlotCache(idHex: String, ts: Long, rssi: Int) {
        val currentSlot = ts / SLOT_DURATION
        val slotKey = "$currentSlot-$idHex"

        val encounter = slotCache[slotKey]
        if (encounter != null) {
            encounter.lastSeen = ts
            encounter.count += 1
            encounter.rssi = rssi
        } else {
            slotCache[slotKey] = SlotEncounter(
                idHex = idHex,
                firstSeen = ts,
                lastSeen = ts,
                count = 1,
                rssi = rssi
            )
        }
        Log.d("BleServiceServer", "📥 Cached encounter $idHex slot=$currentSlot")
    }

    // 🔹 Invia batch al server periodicamente
    private fun startBatchUploader() {
        scope.launch {
            while (isActive) {
                delay(SLOT_DURATION * 1000L)

                val batch = slotCache.values.toList()
                slotCache.clear()

                if (batch.isNotEmpty()) {
                    sendBatchToServer(batch)
                }
            }
        }
    }

    private suspend fun sendBatchToServer(batch: List<SlotEncounter>) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://mockserver.example.com/api/batchResolve")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val arr = batch.map {
                    mapOf(
                        "id" to it.idHex,
                        "firstSeen" to it.firstSeen,
                        "lastSeen" to it.lastSeen,
                        "count" to it.count,
                        "rssi" to it.rssi
                    )
                }
                val json = JSONObject(mapOf("encounters" to arr))

                OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }

                if (conn.responseCode == 200) {
                    Log.d("BleServiceServer", "✅ Batch uploaded (${batch.size} encounters)")
                } else {
                    Log.e("BleServiceServer", "❌ Server error: ${conn.responseCode}")
                }
            } catch (e: Exception) {
                Log.e("BleServiceServer", "❌ Network error: ${e.message}")
            }
        }
    }

    private fun bytesToUuid(bytes: ByteArray): UUID {
        val bb = ByteBuffer.wrap(bytes.copyOfRange(0, 16))
        return UUID(bb.long, bb.long)
    }
}
