package com.example.billyapp.ble

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.billyapp.core.Constants
import com.example.billyapp.core.EncounterRepository
import com.example.billyapp.core.RotatingIdGenerator
import com.example.billyapp.core.api.MatchReq
import com.example.billyapp.core.api.Net
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

    override fun onCreate() {
        super.onCreate()
        startForegroundNoti()

        val secret = UUID.randomUUID().toString().toByteArray()
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

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundNoti() {
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
            .setContentTitle("Nearby attivo")
            .setContentText("Advertising & scanning")
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

    // SOLO QUESTA VERSIONE DI startAdvertising!
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
                .build()

            adv.startAdvertising(settings, data, advCb)
        } catch (e: SecurityException) {
            // Permesso non concesso a runtime. Gestisci come preferisci.
        }
    }

    private fun stopAdvertising() {
        if (!hasBluetoothPermissions()) return
        try {
            advertiser?.stopAdvertising(advCb)

        } catch (e: SecurityException) {
            // Gestisci il caso: il permesso BLUETOOTH_ADVERTISE non è concesso
        }
    }


    private fun startScan() {
        if (!hasBluetoothPermissions()) return
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
            if (!adapter.isEnabled) return
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

                    scope.launch {
                        try {
                            val uuid = bytesToUuid(payload).toString()
                            Net.api.match(MatchReq(uuid = uuid, timestamp = now))
                        } catch (_: Exception) { }
                    }
                }
            }
            sc.startScan(listOf(filter), settings, scanCb)
        } catch (e: SecurityException) {
            // Permesso non concesso a runtime.
        }
    }

    private fun stopScan() {
        if (!hasBluetoothPermissions()) return
        try {
            scanner?.stopScan(scanCb)
        } catch (e: SecurityException) {
            // Permesso non concesso a runtime.
        }
        scanCb = null
    }

    private fun bytesToUuid(bytes: ByteArray): UUID {
        val bb = ByteBuffer.wrap(bytes.copyOfRange(0, 16))
        return UUID(bb.long, bb.long)
    }
}
