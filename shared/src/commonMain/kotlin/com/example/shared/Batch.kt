package com.example.shared

import kotlinx.serialization.Serializable

@Serializable
data class Batch(
    val firstSlotIndex: Long,   // S1 in the report
    val bids: List<String>      // hex strings, one per slot, length = 32 hex = 16 B
)