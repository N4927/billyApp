package com.billyapp.shared.core

import co.touchlab.kermit.Logger
import com.billyapp.shared.domain.model.Bid
import com.billyapp.shared.domain.repository.NetworkDataSource
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
    private val apiClient: NetworkDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.System,
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
     * Calculates the current time slot based on the epoch seconds and retrieves
     * the corresponding pre-downloaded BID from the secure repository.
     *
     * @return The [Bid] to be broadcasted, or null if the batch is exhausted or not yet synced.
     */
    fun getCurrentBid(): Bid? {
        val nowSec = clock.now().epochSeconds
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
     * Algorithm:
     * 1. Check In-Memory Cache (O(1)) for recent ingestion.
     * 2. If new or stale, validate the Hex string.
     * 3. Persist to Disk Queue (SQLite) for eventual syncing.
     * 4. Update Cache.
     *
     * @param bidHex The raw hex string received from the Bluetooth stack.
     */
    fun ingestPacket(bidHex: String) {
        val nowSec = clock.now().epochSeconds

        // 1. Volatile Gatekeeper (Memory)
        // Prevents "Write Amplification" where the same device is scanned 10x/sec.
        val lastSeen = ingestionCache[bidHex]
        if (lastSeen != null && (nowSec - lastSeen) < INGESTION_RATE_LIMIT_SECONDS) {
            Logger.withTag("BillyCore").d { "Ingest Skipped (Rate Limit): $bidHex" }
            return // Rate limit hit: Skip DB write
        }

        try {
            // 2. Validation (Domain Primitive)
            // Ensures we don't pollute the DB with garbage data.
            val bid = Bid(bidHex)

            // 3. Persistence (Disk)
            // We accept this side-effect here as it is write-optimized.
            secureRepo.enqueue(bid)
            Logger.withTag("BillyCore").d { "Ingest Enqueued: $bidHex" }

            // 4. Cache Update
            ingestionCache[bidHex] = nowSec

            // Simple eviction policy to prevent memory leaks in long-running services
            // When cache is full, we clear it entirely. This is cheaper (O(1)) than LRU (O(N)).
            if (ingestionCache.size > MAX_VOLATILE_CACHE_SIZE) {
                ingestionCache.clear()
                Logger.withTag("BillyCore").d { "Ingestion Cache Cleared (Size Limit)" }
            }
        } catch (e: IllegalArgumentException) {
            // Malformed BID received from the ether.
            // Log locally if needed, but do not crash.
            // This is expected noise in BLE environments.
            Logger.withTag("BillyCore").w { "Ingest Failed (Malformed): $bidHex" }
        }
    }

    /**
     * 3. SYNC: Orchestrates the uplink of collected packets to the backend.
     *
     * This function is `suspend` to force the caller to provide a structured concurrency scope.
     * It processes only the Head of Line (FIFO) to ensure strict ordering and data integrity.
     *
     * Transactional Logic:
     * 1. Peek item.
     * 2. Call API.
     * 3. On Success: Delete item + Update UI.
     * 4. On Failure: Keep item (Retry later).
     */
    suspend fun syncQueue() =
        withContext(ioDispatcher) {
            // 1. Peek (Read without consume)
            // We don't dequeue yet to ensure "At-Least-Once" delivery semantics.
            val head = secureRepo.peekHead()

            if (head == null) {
                // Queue is empty, maintenance only
                resolvedRepo.pruneActiveSet()
                return@withContext
            }

            val (primaryKey, bid) = head
            Logger.withTag("BillyCore").d { "Sync Processing: $primaryKey -> ${bid.hex}" }

            // 2. Remote Resolution (Network I/O)
            when (val result = apiClient.resolveContact(bid.hex)) {
                is Result.Success -> {
                    val response = result.data
                    // 3. Acknowledge & Dequeue (Atomic Transaction via Repo)
                    // Only remove from disk if the network call succeeded.
                    secureRepo.deleteByKey(primaryKey)
                    Logger.withTag("BillyCore").d { "Sync Success: Resolved to '${response.displayName}'" }

                    // 4. Update Application State (UI)
                    resolvedRepo.onMatchFound(
                        name = response.displayName,
                        timestamp = clock.now().epochSeconds,
                    )
                }
                is Result.Failure -> {
                    // Network/Server error strategy:
                    // We DO NOT remove the item from the queue.
                    // It will be retried at the next tick (Eventual Consistency).
                    Logger.withTag("BillyCore").e { "Sync Failed: ${result.error}" }
                }
            }

            // Always perform housekeeping on the UI state
            // This ensures that even if network is down, old users fade from the radar.
            resolvedRepo.pruneActiveSet()
        }

    /**
     * 4. BATCH MANAGEMENT: Ensures local cryptographic material is available.
     *
     * Checks if the local buffer of keys is running low and fetches new ones if necessary.
     * This ensures the device can continue advertising even if offline for extended periods.
     */
    suspend fun ensureAdvertisingBatch() =
        withContext(ioDispatcher) {
            val nowSec = clock.now().epochSeconds
            val currentSlot = nowSec / SLOT_DURATION_SECONDS
            val maxSlot = secureRepo.getMaxBatchSlot() ?: 0L

            // Check if we are below the safety threshold
            // If we have fewer than BATCH_REFILL_THRESHOLD slots left, trigger a refill.
            if ((maxSlot - currentSlot) < BATCH_REFILL_THRESHOLD.toLong()) {
                Logger.withTag("BillyCore").d { "Batch Low (Current: $currentSlot, Max: $maxSlot). Refilling..." }

                // Fetch new batch
                when (val result = apiClient.downloadBatch()) {
                    is Result.Success -> {
                        val batch = result.data
                        try {
                            // Validate & Transform
                            val bids = batch.bIds.map { Bid(it) }

                            // Atomic Replace (Disk I/O)
                            // We replace the batch to ensure we have a contiguous block of keys.
                            secureRepo.replaceBatch(
                                startSlot = batch.startSlot,
                                bids = bids,
                            )
                            Logger.withTag("BillyCore").d { "Batch Refill Success: ${bids.size} keys added." }
                        } catch (e: IllegalArgumentException) {
                            // Data Integrity Error (Backend sent bad data)
                            Logger.withTag("BillyCore").e(e) { "Batch Refill Failed: Invalid Data" }
                        }
                    }
                    is Result.Failure -> {
                        // Resiliency: If offline, we just rely on remaining keys.
                        // We do not crash the app for background sync failures.
                        Logger.withTag("BillyCore").e { "Batch Refill Failed: ${result.error}" }
                    }
                }
            }
        }
}
