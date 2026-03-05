package com.example.billyapp.bluetooth

import android.os.ParcelUuid
import java.util.UUID

object BleConstants {

    // Same service UUID used by iOS
    val SERVICE_UUID: ParcelUuid =
        ParcelUuid(UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB"))

    // Characteristic UUID for BID
    val BID_CHARACTERISTIC_UUID: ParcelUuid =
        ParcelUuid(UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB"))

    // Rotate the advertised BID every N seconds
    const val ROTATION_SECONDS = 10L

    // Notification channels
    const val NOTI_CHANNEL_CENTRAL = "ble_channel_central"
    const val NOTI_CHANNEL_PERIPHERAL = "ble_channel_peripheral"
}