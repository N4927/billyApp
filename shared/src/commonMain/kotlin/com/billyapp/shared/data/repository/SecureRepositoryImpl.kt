package com.billyapp.shared.data.repository

import com.billyapp.shared.cache.BillyDatabase
import com.billyapp.shared.domain.model.Bid
import com.billyapp.shared.domain.model.QueuedItem
import com.billyapp.shared.domain.repository.SecureRepository
import kotlinx.datetime.Clock

/**
 * Implementation of the Secure Persistence Layer using SQLDelight.
 * * Responsibilities:
 * 1. Manage the Advertising Batch (T_batch) - Read Optimized.
 * 2. Manage the Ingestion Queue (T_queue) - Write Optimized (FIFO).
 * * All modification operations are wrapped in Transactions to ensure ACID compliance.
 */
class SecureRepositoryImpl(
    private val db: BillyDatabase
) : SecureRepository {

    private val queries = db.billyDatabaseQueries
    
    private companion object {
        // Safety cap to prevent unlimited disk usage in offline scenarios
        const val QUEUE_CAPACITY_MAX = 50_000L
    }

    /**
     * Retrieves the payload to broadcast for a specific time slot.
     * Complexity: O(1) via Primary Key Index.
     */
    override fun getBatchItem(slot: Long): Bid? {
        val hex = queries.selectPayloadBySlot(slot).executeAsOneOrNull()
        return hex?.let { Bid(it) }
    }

    /**
     * Atomically replaces the entire advertising batch.
     * Used when the backend sends new cryptographic material.
     */
    override fun replaceBatch(startSlot: Long, bids: List<Bid>) {
        db.transaction {
            queries.deleteAllBatch()
            bids.forEachIndexed { index, bid ->
                queries.insertBatchItem(
                    slot_index = startSlot + index,
                    payload_hex = bid.hex
                )
            }
        }
    }

    /**
     * Returns the highest slot index currently stored.
     * Used to calculate if we need to fetch new keys.
     */
    override fun getMaxBatchSlot(): Long? {
        return queries.selectMaxSlot().executeAsOneOrNull()?.MAX
    }

    /**
     * Persists a discovered packet into the FIFO queue.
     * Enforces the Max Capacity policy by deleting the oldest item if full.
     */
    override fun enqueue(bid: Bid) {
        db.transaction {
            val count = queries.countQueue().executeAsOne()
            if (count >= QUEUE_CAPACITY_MAX) {
                queries.deleteOldestItem()
            }
            queries.insertQueueItem(
                payload_hex = bid.hex,
                created_at = Clock.System.now().toEpochMilliseconds()
            )
        }
    }

    /**
     * Reads the oldest item in the queue (FIFO Head) without removing it.
     * Used for processing items one by one with Acknowledgement.
     */
    override fun peekHead(): QueuedItem? {
        val result = queries.selectHead().executeAsOneOrNull() ?: return null
        return QueuedItem(result.id, Bid(result.payload_hex))
    }

    /**
     * Removes an item from the queue by its Primary Key.
     * Call this ONLY after successful processing (ACK).
     */
    override fun deleteByKey(k: Long) {
        queries.deleteItemByKey(k)
    }
}