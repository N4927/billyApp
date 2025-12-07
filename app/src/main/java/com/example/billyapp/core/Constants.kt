package com.example.billyapp.core

import android.os.ParcelUuid
import java.util.UUID

object Constants {
    // Service UUID fisso per filtrare advertising/scan
    val SERVICE_UUID: ParcelUuid =
        ParcelUuid(UUID.fromString("0000feee-0000-1000-8000-00805f9b34fb"))

    // Updated to match your architecture (10 minutes = 600 seconds)
    const val ROTATION_SECONDS: Long = 600L // Δt_slot = 10 minutes

    // Additional constants for the new architecture
    const val BATCH_DURATION_HOURS = 24L // T_batch = 24 hours
    const val ENCOUNTER_TIMEOUT_MINUTES = 10L // Cleanup after 10 minutes
}