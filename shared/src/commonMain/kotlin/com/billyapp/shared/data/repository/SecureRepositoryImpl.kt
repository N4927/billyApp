package com.billyapp.shared.data.repository

import com.billyapp.shared.cache.BillyDatabase
import com.billyapp.shared.domain.model.Bid
import com.billyapp.shared.domain.model.QueuedItem
import com.billyapp.shared.domain.repository.SecureRepository
import kotlinx.datetime.Clock

/**
 * Implementation of the Secure Persistence Layer using SQLDelight.
 *
 * Responsibilities:
 * 1. Manage the Advertising Batch (T_batch) - Read Optimized.
 * 2. Manage the Ingestion Queue (T_queue) - Write Optimized (FIFO).
 *
 * ARCHITECTURE NOTE:
 * This class acts as the "Infrastructure Adapter" for the SecureRepository Port.
 * It abstracts the SQL complexity from the Domain Layer.
 * All modification operations are wrapped in Transactions to ensure ACID compliance,
 * preventing data corruption during app crashes or power loss.
 */
class SecureRepositoryImpl(
    private val db: BillyDatabase,
) : SecureRepository {
    private val queries = db.billyDatabaseQueries

    private companion object {
        // Safety cap to prevent unlimited disk usage in offline scenarios.
        // 50,000 items * ~50 bytes/item ≈ 2.5 MB max storage footprint.
        // This ensures the SDK remains a "good citizen" on the user's device.
        const val QUEUE_CAPACITY_MAX = 50_000L
    }

    /**
     * Retrieves the payload to broadcast for a specific time slot.
     *
     * Complexity: O(1) via Primary Key Index.
     * This method is designed to be extremely fast as it may be called frequently
     * by the advertising loop.
     *
     * @param slot The time slot index derived from the current epoch time.
     * @return The [Bid] associated with the slot, or null if no key is available (batch exhausted).
     */
    override fun getBatchItem(slot: Long): Bid? {
        val hex = queries.selectPayloadBySlot(slot).executeAsOneOrNull()
        return hex?.let { Bid(it) }
    }

    /**
     * Atomically replaces the entire advertising batch.
     *
     * Used when the backend sends new cryptographic material.
     * The transaction ensures that we never end up in a state with partial or mixed batches.
     *
     * @param startSlot The starting time slot index for this batch.
     * @param bids The list of cryptographic keys to persist.
     */
    override fun replaceBatch(
        startSlot: Long,
        bids: List<Bid>,
    ) {
        db.transaction {
            // 1. Clear old data to free up space and prevent stale keys.
            queries.deleteAllBatch()

            // 2. Bulk Insert new keys.
            // Note: While this is O(N), N is typically small (e.g., 1000 keys for a week).
            bids.forEachIndexed { index, bid ->
                queries.insertBatchItem(
                    slot_index = startSlot + index,
                    payload_hex = bid.hex,
                )
            }
        }
    }

    /**
     * Returns the highest slot index currently stored.
     *
     * Used by the Domain Layer to calculate "Coverage Remaining" and determine
     * if a background fetch is needed to refill the batch.
     *
     * @return The max slot index, or null if the table is empty.
     */
    override fun getMaxBatchSlot(): Long? {
        return queries.selectMaxSlot().executeAsOneOrNull()?.MAX
    }

    /**
     * Persists a discovered packet into the FIFO queue.
     *
     * Enforces the Max Capacity policy (Ring Buffer behavior):
     * If the queue is full, the oldest item is dropped to make room for the new one.
     * This prioritizes recent data over old data in constrained environments.
     *
     * @param bid The discovered [Bid] to enqueue.
     */
    override fun enqueue(bid: Bid) {
        db.transaction {
            // 1. Check Capacity
            val count = queries.countQueue().executeAsOne()

            // 2. Evict if necessary (Ring Buffer Logic)
            if (count >= QUEUE_CAPACITY_MAX) {
                queries.deleteOldestItem()
            }

            // 3. Insert Tail
            queries.insertQueueItem(
                payload_hex = bid.hex,
                created_at = Clock.System.now().toEpochMilliseconds(),
            )
        }
    }

    /**
     * Reads the oldest item in the queue (FIFO Head) without removing it.
     *
     * Used for processing items one by one with Acknowledgement (Peek-Lock pattern).
     * We do not dequeue immediately to ensure "At-Least-Once" delivery guarantees.
     *
     * @return The [QueuedItem] containing the DB Primary Key and the Bid, or null if empty.
     */
    override fun peekHead(): QueuedItem? {
        val result = queries.selectHead().executeAsOneOrNull() ?: return null
        return QueuedItem(result.id, Bid(result.payload_hex))
    }

    /**
     * Removes an item from the queue by its Primary Key.
     *
     * Call this ONLY after successful processing (ACK).
     * This completes the transaction started by [peekHead].
     *
     * @param k The database Primary Key of the item to remove.
     */
    override fun deleteByKey(k: Long) {
        queries.deleteItemByKey(k)
    }
}
