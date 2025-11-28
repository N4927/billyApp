package com.billyapp.shared.domain.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * B_id: Encrypted BLE Identifier (16 bytes / 128 bit).
 *
 * ARCHITECTURE NOTE:
 * This is a Value Class (JvmInline). It adds type safety without runtime allocation overhead.
 * It enforces the Hexadecimal format and normalizes content to lowercase to guarantee
 * consistent equality checks across the system.
 *
 * Domain Invariants:
 * - Must be exactly 32 characters long (representing 16 bytes).
 * - Must contain only valid Hexadecimal characters (0-9, a-f).
 */
@JvmInline
value class Bid(private val rawHex: String) {
    /**
     * Returns the normalized (lowercase) hexadecimal string representation.
     * Safe for use in API calls and Database persistence.
     */
    val hex: String
        get() = rawHex.lowercase()

    init {
        // Defensive Validation: Fail fast if data is corrupt.
        // This prevents "Garbage In, Garbage Out" issues deep in the system.
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
 *
 * ARCHITECTURE NOTE:
 * As per privacy specs, we do not persist user IDs locally. The 'name' acts as the logical key
 * for UI deduplication in the ResolvedRepository.
 * This model is ephemeral and exists only in memory.
 */
data class ResolvedUser(
    // N_u: Display Name
    val name: String,
    // t_last: Timestamp for TTL calculation
    val lastSeen: Instant,
)

/**
 * Intermediate object representing an item waiting in the ingestion queue.
 *
 * Replaces generic Pair<Long, Bid> for better semantic clarity.
 * Used to couple the Database Primary Key with the Domain Value for
 * transactional processing (Peek -> Process -> Delete by ID).
 */
data class QueuedItem(
    // Database Primary Key (for ACK/Deletion)
    val id: Long,
    // Domain Value
    val bid: Bid,
)

// --- DTOs (Data Transfer Objects) ---
// These belong in the Data Layer conceptually, but keeping them here
// for simplicity in a Modular Monolith is acceptable if marked clearly.

/**
 * Network Response for Batch Download.
 * Contains the cryptographic material for a range of time slots.
 */
@Serializable
data class BatchResponse(
    @SerialName("start_slot") val startSlot: Long,
    @SerialName("slot_duration") val slotDuration: Int,
    @SerialName("b_ids") val bIds: List<String>,
)

/**
 * Network Response for Contact Resolution.
 * Maps an anonymous BID to a human-readable identity.
 */
@Serializable
data class ResolveResponse(
    @SerialName("display_name") val displayName: String,
)
