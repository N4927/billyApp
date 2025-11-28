package com.billyapp.shared.domain.model

import kotlin.jvm.JvmInline
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * B_id: Encrypted BLE Identifier (16 bytes / 128 bit).
 * * ARCHITECTURE NOTE:
 * This is a Value Class (JvmInline). It adds type safety without runtime allocation overhead.
 * It enforces the Hexadecimal format and normalizes content to lowercase to guarantee 
 * consistent equality checks across the system.
 */
@JvmInline
value class Bid(private val rawHex: String) {
    
    val hex: String
        get() = rawHex.lowercase()

    init {
        // Defensive Validation: Fail fast if data is corrupt.
        require(rawHex.length == 32) { 
            "Domain Error: Bid must be exactly 32 hex characters (16 bytes). Got: ${rawHex.length}" 
        }
        require(rawHex.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            "Domain Error: Bid must contain only valid Hex characters."
        }
    }
}

/**
 * U_view: Resolved User visible in the UI.
 * * ARCHITECTURE NOTE:
 * As per privacy specs, we do not persist user IDs. The 'name' acts as the logical key
 * for UI deduplication in the ResolvedRepository.
 */
data class ResolvedUser(
    val name: String,      // N_u
    val lastSeen: Instant  // t_last
)

/**
 * Intermediate object representing an item waiting in the ingestion queue.
 * Replaces generic Pair<Long, Bid> for better semantic clarity.
 */
data class QueuedItem(
    val id: Long, // Database Primary Key (for ACK/Deletion)
    val bid: Bid  // Domain Value
)

// --- DTOs (Data Transfer Objects) ---
// These belong in the Data Layer conceptually, but keeping them here 
// for simplicity in a Modular Monolith is acceptable if marked clearly.

@Serializable
data class BatchResponse(
    @SerialName("start_slot") val startSlot: Long,
    @SerialName("slot_duration") val slotDuration: Int,
    @SerialName("b_ids") val bIds: List<String>
)

@Serializable
data class ResolveResponse(
    @SerialName("display_name") val displayName: String,
    @SerialName("contact_timestamp") val contactTimestamp: Long // Unix Epoch Seconds
)