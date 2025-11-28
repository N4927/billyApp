package com.billyapp.shared.domain.repository

import com.billyapp.shared.domain.model.Bid
import com.billyapp.shared.domain.model.QueuedItem
import com.billyapp.shared.domain.model.ResolvedUser
import kotlinx.coroutines.flow.StateFlow

/**
 * SecureRepository (Infrastructure/Persistence Port).
 * * Manages Raw Data (T_batch, T_queue) on disk.
 * All implementations must ensure ACID properties via SQLite Transactions.
 */
interface SecureRepository {
    // --- Batch Management (Read-Optimized) ---
    
    /** Retrieves the Bid for a specific time slot. O(1) Lookup. */
    fun getBatchItem(slot: Long): Bid?
    
    /** Atomically replaces the advertising batch. */
    fun replaceBatch(startSlot: Long, bids: List<Bid>)
    
    /** Returns the highest slot index stored (to calculate coverage). */
    fun getMaxBatchSlot(): Long?

    // --- Ingestion Queue (Write-Optimized FIFO) ---
    
    /** Persists a packet. Must enforce Max Capacity (Q_max) logic. */
    fun enqueue(bid: Bid)
    
    /** * Reads the Head of Line (oldest item) without removing it. 
     * Used for 1:1 processing with ACK.
     */
    fun peekHead(): QueuedItem?
    
    /** * ACK: Permanently removes the item by its Primary Key.
     */
    fun deleteByKey(k: Long)
}

/**
 * ResolvedRepository (Application State Port).
 * * Manages the Volatile State (RAM) visible to the user.
 * Responsible for aggregation and Time-To-Live (TTL) pruning.
 */
interface ResolvedRepository {
    // Observable state for UI (SwiftUI/Compose)
    val activeSet: StateFlow<List<ResolvedUser>>

    /**
     * Updates the set with a match from the backend.
     * @param timestamp: Unix timestamp of when the contact occurred.
     */
    fun onMatchFound(name: String, timestamp: Long)
    
    /**
     * Removes users who haven't been seen for > tau_vis.
     */
    fun pruneActiveSet()
}