package com.billyapp.shared.core

import com.billyapp.shared.data.network.BillyApiClient
import com.billyapp.shared.domain.model.Bid
import com.billyapp.shared.domain.repository.ResolvedRepository
import com.billyapp.shared.domain.repository.SecureRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * Core Domain Service for the Proximity Feature.
 *
 * Acts as the Orchestrator (Facade) between the raw data layer (SecureRepository),
 * the networking layer (ApiClient), and the application state (ResolvedRepository).
 *
 * ARCHITECTURE NOTE:
 * This class enforces the business rules for Advertising, Scanning, and Syncing.
 * It does NOT handle threading policies directly (except for context switching),
 * leaving the lifecycle management to the platform-specific consumers (iOS/Android).
 */
class BillyCore(
    private val secureRepo: SecureRepository,
    private val resolvedRepo: ResolvedRepository,
    private val apiClient: BillyApiClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private companion object {
        // Time slot duration (10 minutes) aligned with backend crypto policy
        const val SLOT_DURATION_SECONDS = 600L
        
        // Minimum time between processing the same packet to reduce DB I/O pressure.
        // Value is set to 1/6 of the slot duration (~100s).
        const val INGESTION_RATE_LIMIT_SECONDS = 100L
        
        // Max size of the volatile cache to prevent OOM on long-running processes.
        const val MAX_VOLATILE_CACHE_SIZE = 1000
        
        // Threshold to preemptively download new batches (24 slots = 4 hours buffer).
        const val BATCH_REFILL_THRESHOLD = 24
    }

    // Volatile cache (RAM) to act as a barrier before hitting the disk (SQLite).
    // Maps HexBid -> LastSeenTimestamp (Epoch Seconds).
    private val ingestionCache = mutableMapOf<String, Long>()

    /**
     * 1. ADVERTISING: Retrieves the cryptographic payload for the current time slot.
     *
     * @return The [Bid] to be broadcasted, or null if the batch is exhausted.
     */
    fun getCurrentBid(): Bid? {
        val nowSec = Clock.System.now().epochSeconds
        val currentSlot = nowSec / SLOT_DURATION_SECONDS
        
        // This is a fast read-optimized query (O(1)), safe to call on main thread if needed,
        // but ideally should be called from a background thread by the native layer.
        return secureRepo.getBatchItem(currentSlot)
    }

    /**
     * 2. SCANNING: Processes a discovered BLE packet.
     *
     * Applies volatile rate limiting to protect the database from high-frequency
     * writes typical of BLE scanning (hundreds of advertisements per minute).
     *
     * @param bidHex The raw hex string received from the Bluetooth stack.
     */
    fun ingestPacket(bidHex: String) {
        val nowSec = Clock.System.now().epochSeconds

        // 1. Volatile Gatekeeper (Memory)
        val lastSeen = ingestionCache[bidHex]
        if (lastSeen != null && (nowSec - lastSeen) < INGESTION_RATE_LIMIT_SECONDS) {
            return // Rate limit hit: Skip DB write
        }

        try {
            // 2. Validation (Domain Primitive)
            val bid = Bid(bidHex)

            // 3. Persistence (Disk)
            // We accept this side-effect here as it is write-optimized.
            secureRepo.enqueue(bid)

            // 4. Cache Update
            ingestionCache[bidHex] = nowSec
            
            // Simple eviction policy to prevent memory leaks in long-running services
            if (ingestionCache.size > MAX_VOLATILE_CACHE_SIZE) {
                ingestionCache.clear() 
            }
        } catch (e: IllegalArgumentException) {
            // Malformed BID received from the ether. 
            // Log locally if needed, but do not crash. 
            // This is expected noise in BLE environments.
        }
    }

    /**
     * 3. SYNC: Orchestrates the uplink of collected packets to the backend.
     *
     * This function is `suspend` to force the caller to provide a structured concurrency scope.
     * It processes only the Head of Line (FIFO) to ensure strict ordering and data integrity.
     */
    suspend fun syncQueue() = withContext(ioDispatcher) {
        // 1. Peek (Read without consume)
        val head = secureRepo.peekHead()

        if (head == null) {
            // Queue is empty, maintenance only
            resolvedRepo.pruneActiveSet()
            return@withContext
        }

        val (primaryKey, bid) = head

        try {
            // 2. Remote Resolution (Network I/O)
            val response = apiClient.resolveContact(bid.hex)

            // 3. Acknowledge & Dequeue (Atomic Transaction via Repo)
            secureRepo.deleteByKey(primaryKey)

            // 4. Update Application State (UI)
            resolvedRepo.onMatchFound(
                name = response.displayName,
                timestamp = response.contactTimestamp
            )

        } catch (e: Exception) {
            // Network/Server error strategy:
            // We DO NOT remove the item from the queue. 
            // It will be retried at the next tick (Eventual Consistency).
            // TODO: Integrate specialized Logger (e.g., Kermit or Crashlytics)
            println("SyncQueue Failure: ${e.message}") 
        }

        // Always perform housekeeping on the UI state
        resolvedRepo.pruneActiveSet()
    }

    /**
     * 4. BATCH MANAGEMENT: Ensures local cryptographic material is available.
     *
     * Checks if the local buffer of keys is running low and fetches new ones if necessary.
     */
    suspend fun ensureAdvertisingBatch() = withContext(ioDispatcher) {
        val nowSec = Clock.System.now().epochSeconds
        val currentSlot = nowSec / SLOT_DURATION_SECONDS
        val maxSlot = secureRepo.getMaxBatchSlot() ?: 0L

        // Check if we are below the safety threshold
        if ((maxSlot - currentSlot) < BATCH_REFILL_THRESHOLD) {
            try {
                // Fetch new batch
                val batch = apiClient.downloadBatch()

                // Validate & Transform
                val bids = batch.bIds.map { Bid(it) }

                // Atomic Replace (Disk I/O)
                secureRepo.replaceBatch(
                    startSlot = batch.startSlot,
                    bids = bids
                )
            } catch (e: Exception) {
                // Resiliency: If offline, we just rely on remaining keys.
                // We do not crash the app for background sync failures.
                println("Advertising Batch Sync Failed: ${e.message}")
            }
        }
    }
}