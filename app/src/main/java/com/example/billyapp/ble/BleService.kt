package com.example.billyapp.ble

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.billyapp.core.Constants
import com.example.billyapp.core.EncounterRepository
import com.example.billyapp.core.RotatingIdGenerator
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.UUID

import com.example.billyapp.core.Encounter
import com.example.billyapp.core.EncounterBus
import com.example.billyapp.core.ProfileManager



class BleService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var generator: RotatingIdGenerator
    private val repo = EncounterRepository()

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private val advCb = object : AdvertiseCallback() {}
    private var scanCb: ScanCallback? = null

    private var isActive = true

    // 🔹 Mappa utenti -> secret (segreti condivisi)
    private val userSecrets = mapOf(
        "Alice" to "secret-alice".toByteArray(),
        "Bob" to "secret-bob".toByteArray(),
        "Charlie" to "secret-charlie".toByteArray()
    )

    override fun onCreate() {
        super.onCreate()
        startForegroundNoti("Initializing BLE service...")

        // 🔹 Secret locale (scegli quello del device)
        val mySecret = ProfileManager.getSecretForBle(this, length = 8)
        generator = RotatingIdGenerator(mySecret, lengthBytes = 8)


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

            // 🔹 Concatena [timestamp 8B] + [rotatingId 16B]
            val payload = ByteBuffer.allocate(8 + rotatingId.size)
                .putLong(ts)
                .put(rotatingId)
                .array()

            startAdvertising(payload)

            delay(Constants.ROTATION_SECONDS * 1000L)
        }
        Log.d("BleService", "Stopped advertising due to service inactive")
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
        if (!hasBluetoothPermissions()) {
            Log.w("BleService", "Permessi Bluetooth non concessi, skip startAdvertising")
            return
        }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) {
                Log.w("BleService", "Bluetooth non abilitato, skip startAdvertising")
                return
            }
            val adv = advertiser ?: return

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(false)
                .build()

            val data = AdvertiseData.Builder()
                .addServiceUuid(Constants.SERVICE_UUID) // UUID fisso per tutti
                .addServiceData(Constants.SERVICE_UUID, payload) // timestamp + rotatingId
                .build()

            adv.startAdvertising(settings, data, advCb)
            Log.d("BleService", "Advertising BLE payload size=${payload.size}")
            updateNotification("Pubblicazione BLE attiva")

        } catch (e: SecurityException) {
            Log.e("BleService", "Errore permessi BLE: ${e.message}")
        }
    }

    private fun stopAdvertising() {
        if (!hasBluetoothPermissions()) {
            Log.w("BleService", "Permessi Bluetooth non concessi, skip stopAdvertising")
            return
        }
        try {
            advertiser?.stopAdvertising(advCb)
            Log.d("BleService", "Stop pubblicazione BLE")
            updateNotification("Pubblicazione BLE fermata")
        } catch (e: SecurityException) {
            Log.e("BleService", "Errore permessi BLE: ${e.message}")
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
        if (!hasBluetoothPermissions()) {
            Log.w("BleService", "Permessi Bluetooth non concessi, skip startScan")
            return
        }
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) {
                Log.w("BleService", "Bluetooth non abilitato, skip startScan")
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
                // dentro BleService, onScanResult
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val record = result.scanRecord ?: return
                    val payload = record.getServiceData(Constants.SERVICE_UUID) ?: return
                    if (payload.size < 8) return

                    val bb = ByteBuffer.wrap(payload)
                    val ts = bb.long
                    val idBytes = payload.copyOfRange(8, payload.size)
                    val idHex = idBytes.joinToString("") { "%02x".format(it) }

                    if (!repo.shouldProcess(idHex, ts)) return

                    val resolvedName = resolveNameFromId(idBytes, ts)

                    val encounter = Encounter(
                        idHex = idHex,
                        rssi = result.rssi,
                        timestampSec = ts,
                        resolvedName = resolvedName
                    )

                    // Invia al bus
                    scope.launch {
                        EncounterBus.emit(encounter)
                    }

                    Log.d("BleService", "Trovato: ${resolvedName ?: "Sconosciuto"} ($idHex)")
                }

            }
            sc.startScan(listOf(filter), settings, scanCb)
        } catch (e: SecurityException) {
            Log.e("BleService", "Errore permessi BLE: ${e.message}")
        }
    }

    private fun stopScan() {
        if (!hasBluetoothPermissions()) {
            Log.w("BleService", "Permessi Bluetooth non concessi, skip stopScan")
            return
        }
        try {
            scanner?.stopScan(scanCb)
        } catch (e: SecurityException) {
            Log.e("BleService", "Errore permessi BLE: ${e.message}")
        }
        scanCb = null
    }

    // 🔹 Reverse lookup del rotatingId
    private fun resolveNameFromId(idBytes: ByteArray, timestampSec: Long): String? {
        val window = 2 // +/- 2 bucket di tolleranza
        for ((name, secret) in userSecrets) {
            val generator = RotatingIdGenerator(secret)
            for (offset in -window..window) {
                val testTime = timestampSec + offset * Constants.ROTATION_SECONDS
                val testId = generator.currentIdBytes(testTime)
                if (testId.contentEquals(idBytes)) return name
            }
        }
        return null
    }

    private fun bytesToUuid(bytes: ByteArray): UUID {
        val bb = ByteBuffer.wrap(bytes.copyOfRange(0, 16))
        return UUID(bb.long, bb.long)
    }
}
