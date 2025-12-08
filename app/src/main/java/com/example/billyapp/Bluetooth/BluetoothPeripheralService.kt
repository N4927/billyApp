package com.example.billyapp.bluetooth

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.billyapp.kmm.KmmEnvironment
import com.billyapp.shared.core.BillyCore
import kotlinx.coroutines.*

class BluetoothPeripheralService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val core: BillyCore
        get() = KmmEnvironment.core

    private var advertiser: BluetoothLeAdvertiser? = null
    private val advCallback = object : AdvertiseCallback() {}

    @Volatile
    private var isActive = false

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification("Starting BLE advertising...")

        KmmEnvironment.init(applicationContext)

        val manager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter

        if (!adapter.isEnabled) {
            stopSelf()
            return
        }

        advertiser = adapter.bluetoothLeAdvertiser
        isActive = true

        scope.launch {
            advertisingLoop()
        }
    }

    override fun onDestroy() {
        isActive = false
        stopAdvertising()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------

    private fun startForegroundNotification(msg: String) {
        val nm = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= 26 &&
            nm.getNotificationChannel(BleConstants.NOTI_CHANNEL_PERIPHERAL) == null) {

            nm.createNotificationChannel(
                NotificationChannel(
                    BleConstants.NOTI_CHANNEL_PERIPHERAL,
                    "BLE Peripheral",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification: Notification =
            NotificationCompat.Builder(this, BleConstants.NOTI_CHANNEL_PERIPHERAL)
                .setContentTitle("Billy – Advertising")
                .setContentText(msg)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .build()

        startForeground(1002, notification)
    }

    private fun updateNotification(text: String) {
        startForegroundNotification(text)
    }
    //helper since bid is in hex
    private fun String.hexToByteArray(): ByteArray {
        require(length % 2 == 0) { "Invalid hex string length" }
        return ByteArray(length / 2) { i ->
            substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }


    // ---------------------------------------------------------
    // Advertising
    // ---------------------------------------------------------

    private suspend fun advertisingLoop() {
        try {
            core.ensureAdvertisingBatch()
        } catch (e: Exception) {
            Log.e("BluetoothPeripheral", "❌ Cannot ensure batch: ${e.message}")
            updateNotification("Batch error")
            return
        }


        while (isActive) {
            stopAdvertising()

            val bid = try {
                core.getCurrentBid()
            } catch (e: Exception) {
                Log.e("BluetoothPeripheral", "❌ getCurrentBid: ${e.message}")
                null
            }

            if (bid != null) {
                startAdvertising(bid.hex.hexToByteArray())

                updateNotification("Advertising active")
            } else {
                updateNotification("Waiting for BID...")
                core.ensureAdvertisingBatch()
            }

            delay(BleConstants.ROTATION_SECONDS * 1000)
        }
    }

    private fun startAdvertising(payload: ByteArray) {
        if (!hasPermissions()) {
            Log.w("BluetoothPeripheral", "⚠️ Missing permissions")
            return
        }

        val adv = advertiser ?: return

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(BleConstants.SERVICE_UUID)
            .addServiceData(BleConstants.SERVICE_UUID, payload)
            .build()

        try {
            adv.startAdvertising(settings, data, advCallback)
        } catch (e: Exception) {
            Log.e("BluetoothPeripheral", "❌ startAdvertising: ${e.message}")
        }
    }
    @android.annotation.SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        try {
            advertiser?.stopAdvertising(advCallback)
        } catch (_: Exception) {}
    }

    private fun hasPermissions(): Boolean {
        fun check(p: String) =
            ContextCompat.checkSelfPermission(this, p) ==
                    PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            check(android.Manifest.permission.BLUETOOTH_ADVERTISE) &&
                    check(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else true
    }
}
