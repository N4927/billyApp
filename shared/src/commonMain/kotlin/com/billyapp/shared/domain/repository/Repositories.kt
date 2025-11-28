package com.billyapp.shared.domain.repository

import com.billyapp.shared.domain.model.Bid
import com.billyapp.shared.domain.model.QueuedItem
import com.billyapp.shared.domain.model.ResolvedUser
import kotlinx.coroutines.flow.StateFlow

/**
 * SecureRepository (Infrastructure/Persistence Port).
 *
 * Manages Raw Data (T_batch, T_queue) on disk.
 * All implementations must ensure ACID properties via SQLite Transactions.
 * This repository handles the "Cold Storage" of the application.
 */
interface SecureRepository {
    // --- Batch Management (Read-Optimized) ---

    /**
     * Retrieves the Bid for a specific time slot.
     * Complexity: O(1) Lookup.
     * @param slot The time slot index.
     * @return The Bid if available, null otherwise.
     */
    fun getBatchItem(slot: Long): Bid?

    /**
     * Atomically replaces the advertising batch.
     * Ensures that we switch to the new set of keys cleanly.
     */
    fun replaceBatch(
        startSlot: Long,
        bids: List<Bid>,
    )

    /**
     * Returns the highest slot index stored.
     * Used to calculate coverage and trigger background refills.
     */
    fun getMaxBatchSlot(): Long?

    // --- Ingestion Queue (Write-Optimized FIFO) ---

    /**
     * Persists a packet.
     * Must enforce Max Capacity (Q_max) logic to prevent disk overflow.
     */
    fun enqueue(bid: Bid)

    /**
     * Reads the Head of Line (oldest item) without removing it.
     * Used for 1:1 processing with ACK (Peek-Lock pattern).
     */
    fun peekHead(): QueuedItem?

    /**
     * ACK: Permanently removes the item by its Primary Key.
     * Call this only after successful processing.
     */
    fun deleteByKey(k: Long)
}

/**
 * ResolvedRepository (Application State Port).
 *
 * Manages the Volatile State (RAM) visible to the user.
 * Responsible for aggregation and Time-To-Live (TTL) pruning.
 * This repository handles the "Hot State" of the application.
 */
interface ResolvedRepository {
    /**
     * Observable state for UI (SwiftUI/Compose).
     * Emits updates whenever the active set changes.
     */
    val activeSet: StateFlow<List<ResolvedUser>>

    /**
     * Updates the set with a match from the backend.
     * @param name The display name of the user.
     * @param timestamp Unix timestamp of when the contact occurred.
     */
    fun onMatchFound(
        name: String,
        timestamp: Long,
    )

    /**
     * Removes users who haven't been seen for > tau_vis.
     * Keeps the radar view fresh and relevant.
     */
    fun pruneActiveSet()
}
