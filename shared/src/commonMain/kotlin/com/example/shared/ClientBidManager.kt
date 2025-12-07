package com.example.shared

import kotlinx.datetime.Clock

/**
 * Client-side BID manager that uses server-generated batches
 * Matches PDF sections 6.1 and 6.3 - Proper rotating ID generator
 */
class ClientBidManager(private val userName: String) {
    private var currentBatch: Batch? = null
    private val SLOT_SECONDS = 600L // Δt_slot = 10 min

    /**
     * Get current rotating BID (PDF section 6.1)
     * This is the correct "rotating ID generator" that matches your architecture
     */
    fun getCurrentBid(): String? {
        val batch = currentBatch ?: return null

        val now = Clock.System.now().epochSeconds
        val currentSlot = now / SLOT_SECONDS
        val slotIndex = currentSlot - batch.firstSlotIndex

        // ✅ FIXED: Add debug logging to see what's happening
        println("🔍 [ClientBidManager] Current slot: $currentSlot, First slot: ${batch.firstSlotIndex}, Slot index: $slotIndex")
        println("🔍 [ClientBidManager] Batch size: ${batch.bids.size}, Valid range: 0 to ${batch.bids.size - 1}")

        if (slotIndex >= 0 && slotIndex < batch.bids.size) {
            val bid = batch.bids[slotIndex.toInt()]
            println("✅ [ClientBidManager] Using BID for slot $currentSlot: ${bid.take(8)}...")
            return bid
        } else {
            println("❌ [ClientBidManager] Batch expired or invalid slot:")
            println("   - Current slot: $currentSlot")
            println("   - First batch slot: ${batch.firstSlotIndex}")
            println("   - Calculated index: $slotIndex")
            println("   - Batch covers slots: ${batch.firstSlotIndex} to ${batch.firstSlotIndex + batch.bids.size - 1}")
            return null
        }
    }

    /**
     * Get current BID as ByteArray for BLE transmission
     */
    fun getCurrentBidBytes(): ByteArray? {
        val bidHex = getCurrentBid() ?: return null
        return hexStringToByteArray(bidHex)
    }

    /**
     * Refresh batch from server (PDF section 6.3.1)
     */
    fun refreshBatch(): Boolean {
        return try {
            currentBatch = FakeServer.generateBatch(userName)
            println("✅ [ClientBidManager] New batch: ${currentBatch!!.bids.size} BIDs")
            println("✅ [ClientBidManager] First slot: ${currentBatch!!.firstSlotIndex}")
            println("✅ [ClientBidManager] Current time: ${Clock.System.now().epochSeconds}")
            println("✅ [ClientBidManager] Current slot: ${Clock.System.now().epochSeconds / SLOT_SECONDS}")
            true
        } catch (e: Exception) {
            println("❌ [ClientBidManager] Failed to refresh: ${e.message}")
            false
        }
    }

    /**
     * Check if batch is valid (PDF section 6.3)
     */
    fun isBatchValid(): Boolean {
        val batch = currentBatch ?: return false
        val now = Clock.System.now().epochSeconds
        val currentSlot = now / SLOT_SECONDS
        val isValid = currentSlot in batch.firstSlotIndex until (batch.firstSlotIndex + batch.bids.size)
        println("🔍 [ClientBidManager] Batch valid check: $isValid (current slot: $currentSlot, batch covers: ${batch.firstSlotIndex} to ${batch.firstSlotIndex + batch.bids.size - 1})")
        return isValid
    }

    /**
     * Get batch status info
     */
    fun getBatchStatus(): String {
        val batch = currentBatch ?: return "No batch"
        val now = Clock.System.now().epochSeconds
        val currentSlot = now / SLOT_SECONDS
        val slotIndex = currentSlot - batch.firstSlotIndex
        val remaining = batch.bids.size - slotIndex - 1

        return "Batch: slot $slotIndex/${batch.bids.size}, $remaining remaining"
    }

    /* ---------- HELPER FUNCTIONS ---------- */
    private fun hexStringToByteArray(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}