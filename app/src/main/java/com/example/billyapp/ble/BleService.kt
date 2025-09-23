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
import com.example.billyapp.core.EncounterRepository
import com.example.billyapp.core.RotatingIdGenerator
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.util.UUID

class BleService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var generator: RotatingIdGenerator
    private val repo = EncounterRepository()

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private val advCb = object : AdvertiseCallback() {}
    private var scanCb: ScanCallback? = null

    private var isActive = true

    private val userSecrets = mapOf(
        "Person1" to "secretperson1".toByteArray(),
        "Person2" to "secretperson2".toByteArray()
    )

    override fun onCreate() {
        super.onCreate()
        startForegroundNoti("Initializing BLE service...")

        val secret = "secretperson1".toByteArray()
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

    private fun startAdvertising(idBytes: ByteArray) {
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

            // Qui aggiungiamo il service UUID e i dati associati
            val data = AdvertiseData.Builder()
                .addServiceUuid(Constants.SERVICE_UUID)
                .addServiceData(Constants.SERVICE_UUID, idBytes)
                .setIncludeDeviceName(true)
                .build()

            adv.startAdvertising(settings, data, advCb)
            Log.d("BleService", "Inizio pubblicazione BLE con id: ${idBytes.joinToString("") { "%02x".format(it) }}")
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
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val record = result.scanRecord ?: return
                    val payload = record.getServiceData(Constants.SERVICE_UUID) ?: return
                    val now = System.currentTimeMillis() / 1000
                    val idHex = payload.joinToString("") { "%02x".format(it) }
                    if (!repo.shouldProcess(idHex, now)) return

                    val resolvedName = resolveNameFromId(idHex, now)
                    Log.d("BleService", "Rilevato dispositivo: ${resolvedName ?: "Sconosciuto"} UUID: $idHex alle $now")
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

    private fun resolveNameFromId(idHex: String, timestampSec: Long): String? {
        val window = 10
        for ((name, secret) in userSecrets) {
            val generator = RotatingIdGenerator(secret)
            for (offset in -window..window) {
                val testTime = timestampSec + offset * Constants.ROTATION_SECONDS
                val testId = generator.currentIdBytes(testTime).joinToString("") { "%02x".format(it) }
                if (testId == idHex) return name
            }
        }
        return null
    }

    private fun bytesToUuid(bytes: ByteArray): UUID {
        val bb = ByteBuffer.wrap(bytes.copyOfRange(0, 16))
        return UUID(bb.long, bb.long)
    }
}
