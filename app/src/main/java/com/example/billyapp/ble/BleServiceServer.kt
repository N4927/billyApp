package com.example.billyapp.ble

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.billyapp.core.Constants
import com.example.billyapp.core.Encounter
import com.example.billyapp.core.EncounterBus
import com.example.billyapp.core.EncounterRepository
import com.example.billyapp.core.UserManager
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

class BleServiceServer : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repo = EncounterRepository()
    private lateinit var userManager: UserManager

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var isActive = true
    private val advCb = object : AdvertiseCallback() {}
    private var scanCb: ScanCallback? = null

    // 🔹 HTTP client (no caching)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        startForegroundNoti("Initializing BLE service...")

        userManager = UserManager(this)
        val name = userManager.getUserName()
        Log.i("BleServiceServer", "📱 Device name: $name")
        Log.i("BleServiceServer", "🔐 Initializing cryptography for secure BLE communication")

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

    override fun onBind(intent: Intent?): IBinder? = null

    // 🔹 Foreground notification
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

    // 🔹 Advertising loop
    private suspend fun loopRotateAndAdvertise() {
        while (isActive) {
            stopAdvertising()

            val ts = System.currentTimeMillis() / 1000
            val personalId = userManager.getPersonalIdentifier()
            val cryptoManager = userManager.getCryptographyManager()
            val encryptedPayload = cryptoManager.encryptRotatingIdentifier(personalId, ts)

            Log.d("BleServiceServer", "🧩 Advertising name=${userManager.getUserName()}")
            Log.d("BleServiceServer", "🧩 Personal ID=$personalId")
            Log.d("BleServiceServer", "🧩 Encrypted payload=${encryptedPayload.joinToString("") { "%02x".format(it) }}")
            Log.d("BleServiceServer", "🧩 Timestamp=$ts")

            startAdvertising(encryptedPayload)
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

    // 🔹 Advertising logic
    private fun startAdvertising(payload: ByteArray) {
        if (!hasBluetoothPermissions()) {
            Log.w("BleServiceServer", "⚠️ Permessi Bluetooth non concessi, skip startAdvertising")
            return
        }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) {
                Log.w("BleServiceServer", "⚠️ Bluetooth non abilitato, skip startAdvertising")
                return
            }
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
            Log.d("BleServiceServer", "🚀 Advertising BLE payload size=${payload.size}")
            updateNotification("BLE attivo")

        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "❌ Errore permessi BLE: ${e.message}")
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advCb)
            Log.d("BleServiceServer", "🛑 Stop advertising")
        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "❌ Errore stopAdvertising: ${e.message}")
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

    // 🔹 Scanning logic
    private fun startScan() {
        if (!hasBluetoothPermissions()) {
            Log.w("BleServiceServer", "⚠️ Permessi Bluetooth non concessi, skip startScan")
            return
        }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) {
                Log.w("BleServiceServer", "⚠️ Bluetooth non abilitato, skip startScan")
                return
            }
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
                    if (payload.size != 16) return // solo payload completi

                    val timestampBytes = payload.copyOfRange(0, 8)
                    val ts = ByteBuffer.wrap(timestampBytes).order(ByteOrder.BIG_ENDIAN).long
                    val payloadHex = payload.joinToString("") { "%02x".format(it) }

                    if (!repo.shouldProcess(payloadHex, ts)) return

                    scope.launch {
                        val resolvedName = resolveViaServer(payload, ts) ?: "Unknown"

                        val encounter = Encounter(
                            idHex = payloadHex,
                            rssi = result.rssi,
                            timestampSec = ts,
                            resolvedName = resolvedName
                        )

                        EncounterBus.emit(encounter)
                        Log.d("BleServiceServer", "🟢 Emitted encounter for $resolvedName")
                        Log.d("BleServiceServer", "📡 Found: $resolvedName (encrypted: ${payloadHex.take(16)}...)")
                    }
                }
            }

            sc.startScan(listOf(filter), settings, scanCb)
        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "❌ Errore startScan: ${e.message}")
        }
    }

    private fun stopScan() {
        try {
            scanner?.stopScan(scanCb)
            scanCb = null
            Log.d("BleServiceServer", "🛑 Stop scanning")
        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "❌ Errore stopScan: ${e.message}")
        }
    }

    // 🔹 HTTP lookup - nuovo endpoint HTTPS
    private suspend fun resolveViaServer(payload: ByteArray, timestamp: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                val cipher8Hex = payload.copyOfRange(0, 8)
                    .joinToString("") { "%02x".format(it) }

                val json = JSONObject().apply {
                    put("timestamp", timestamp)
                    put("cipher8_hex", cipher8Hex)
                }

                val request = Request.Builder()
                    .url("https://19.hackathon.ethz.ch/api/match/") // ✅ nuovo endpoint HTTPS
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("BleServiceServer", "⚠️ Server returned ${response.code}")
                        return@withContext null
                    }

                    val body = response.body?.string() ?: return@withContext null
                    Log.d("BleServiceServer", "🌐 Server response: $body")

                    val obj = JSONObject(body)
                    obj.optString("display_name", "Unknown")
                }
            } catch (e: Exception) {
                Log.e("BleServiceServer", "❌ HTTP error: ${e.message}")
                null
            }
        }
    }
}
