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
import com.example.shared.Encounter
import com.example.shared.EncounterBus
import com.example.shared.EncounterRepository
import com.example.shared.FakeServer
import com.example.billyapp.core.UserManager
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

class BleServiceServer : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val encounterRepo = EncounterRepository()
    private lateinit var userManager: UserManager

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var isActive = true
    private val advCb = object : AdvertiseCallback() {}
    private var scanCb: ScanCallback? = null

    // 🔧 TEST-ONLY: httpClient that accepts any certificate (unsafe)
    private val httpClient: OkHttpClient = getUnsafeOkHttpClient()

    override fun onCreate() {
        super.onCreate()
        startForegroundNoti("Initializing BLE service...")

        userManager = UserManager(this)
        val name = userManager.getUserName()
        Log.i("BleServiceServer", "📱 Device name: $name")
        Log.i("BleServiceServer", "🔐 Using server-side encrypted BIDs with batch system")

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

    // 🔹 Advertising loop - UPDATED for new architecture
    private suspend fun loopRotateAndAdvertise() {
        while (isActive) {
            stopAdvertising()

            // ✅ NEW ARCHITECTURE: Get current BID from server-side batch
            val bidBytes = userManager.getCurrentBidBytes()

            if (bidBytes != null) {
                Log.d("BleServiceServer", "📡 Advertising BID from batch: ${bidBytes.size} bytes")
                Log.d("BleServiceServer", "🧩 BID Hex: ${bidBytes.joinToString("") { "%02x".format(it) }}")

                startAdvertising(bidBytes)
                updateNotification("BLE active - ${userManager.getBatchStatus()}")
            } else {
                Log.w("BleServiceServer", "⚠️ No valid BID available - batch may be expired")
                updateNotification("BLE inactive - No valid batch")

                // ✅ Try to refresh batch
                if (userManager.refreshBatch()) {
                    Log.i("BleServiceServer", "✅ Batch refreshed successfully")
                } else {
                    Log.e("BleServiceServer", "❌ Failed to refresh batch")
                }
            }

            delay(Constants.ROTATION_SECONDS * 1000L)
        }

        Log.d("BleServiceServer", "Stopped advertising due to service inactive")
        updateNotification("BLE advertising stopped")
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
            Log.w("BleServiceServer", "⚠️ Bluetooth permissions not granted, skip startAdvertising")
            return
        }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) {
                Log.w("BleServiceServer", "⚠️ Bluetooth not enabled, skip startAdvertising")
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
            updateNotification("BLE active")

        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "❌ BLE permission error: ${e.message}")
        }
    }

    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advCb)
            Log.d("BleServiceServer", "🛑 Stop advertising")
        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "❌ Stop advertising error: ${e.message}")
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

    // 🔹 Scanning logic - UPDATED for new architecture
    private fun startScan() {
        if (!hasBluetoothPermissions()) {
            Log.w("BleServiceServer", "⚠️ Bluetooth permissions not granted, skip startScan")
            return
        }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) {
                Log.w("BleServiceServer", "⚠️ Bluetooth not enabled, skip startScan")
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
                    if (payload.size != 16) return // Only accept full 16-byte BIDs

                    val payloadHex = payload.joinToString("") { "%02x".format(it) }
                    val currentTime = System.currentTimeMillis() / 1000

                    Log.d("BleServiceServer", "📡 Received BID: ${payloadHex.take(16)}..., RSSI: ${result.rssi}")

                    // ✅ NEW: Use EncounterRepository for processing (matches new architecture)
                    scope.launch {
                        val encounter = encounterRepo.processEncounter(payloadHex, result.rssi)
                        if (encounter != null) {
                            EncounterBus.emit(encounter)
                            Log.d("BleServiceServer", "🟢 Emitted encounter for ${encounter.resolvedName ?: "Unknown user"}")
                        }
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e("BleServiceServer", "❌ Scan failed with error: $errorCode")
                }
            }

            sc.startScan(listOf(filter), settings, scanCb)
            Log.d("BleServiceServer", "🔍 Started BLE scanning")

        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "❌ Start scan error: ${e.message}")
        }
    }

    private fun stopScan() {
        try {
            scanner?.stopScan(scanCb)
            scanCb = null
            Log.d("BleServiceServer", "🛑 Stop scanning")
        } catch (e: SecurityException) {
            Log.e("BleServiceServer", "❌ Stop scan error: ${e.message}")
        }
    }

    // 🔹 HTTP lookup - Optional: Keep for ETHZ server integration
    private suspend fun resolveViaServer(bidHex: String, timestamp: Long): String? {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("timestamp", timestamp)
                    put("cipher8_hex", bidHex) // ✅ Use full BID hex (16 bytes)
                }

                val request = Request.Builder()
                    .url("https://19.hackathon.ethz.ch/api/match/")
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("BleServiceServer", "⚠️ Server returned ${response.code}")
                        // Fallback to local resolution
                        val localUser = FakeServer.resolveBid(bidHex, timestamp)
                        return@withContext localUser?.name // ✅ Fixed: was displayName
                    }

                    val body = response.body?.string() ?: return@withContext null
                    Log.d("BleServiceServer", "🌐 Server response: $body")

                    val obj = JSONObject(body)
                    val displayName = obj.optString("display_name", null)
                    if (!displayName.isNullOrBlank()) return@withContext displayName

                    // If server didn't return display_name, try local fallback
                    val localUserFromServerNull = FakeServer.resolveBid(bidHex, timestamp)
                    return@withContext localUserFromServerNull?.name // ✅ Fixed: was displayName
                }
            } catch (e: Exception) {
                Log.e("BleServiceServer", "❌ HTTP error: ${e.message}")
                // Fallback to local resolution on exception
                return@withContext try {
                    FakeServer.resolveBid(bidHex, timestamp)?.name // ✅ Fixed: was displayName
                } catch (ex: Exception) {
                    Log.e("BleServiceServer", "❌ Local fallback error: ${ex.message}")
                    null
                }
            }
        }
    }

    /**
     * TEST-ONLY: create an OkHttpClient that accepts all certificates.
     * DO NOT ship this in production.
     */
    private fun getUnsafeOkHttpClient(): OkHttpClient {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })

            val sslContext = SSLContext.getInstance("SSL")
            sslContext.init(null, trustAllCerts, SecureRandom())
            val sslSocketFactory = sslContext.socketFactory

            return OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            Log.e("BleServiceServer", "❌ Failed to create unsafe http client: ${e.message}")
            // Fallback to a normal client if something goes wrong creating unsafe client
            return OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }

    private fun String.decodeHexToByteArray(): ByteArray {
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}