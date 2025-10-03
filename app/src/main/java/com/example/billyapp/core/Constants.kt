package com.example.billyapp.core

import android.os.ParcelUuid
import java.util.UUID

object Constants {
    // Service UUID fisso per filtrare advertising/scan
    val SERVICE_UUID: ParcelUuid =
        ParcelUuid(UUID.fromString("0000feee-0000-1000-8000-00805f9b34fb"))

    const val ROTATION_SECONDS: Long = 20L
}
