package com.example.shared

import kotlinx.serialization.Serializable
import kotlinx.datetime.Clock
import kotlin.random.Random



object FakeServer {
    /* ---------- USER REGISTRY ---------- */
    private val registeredUsers: Map<String, User> = mapOf(
        "alice"   to User("ecb73c72d94f1a23", "Alice", 25, "Loves hiking"),
        "bob"     to User("33f24d0c2ab9e78f", "Bob", 30, "Software developer"),
        "charlie" to User("bcb375489ee42a6d", "Charlie", 28, "Photographer"),
        "luca"    to User("90895e9d21cb8fa6", "Luca", 32, "Teacher")
    )

    /* ---------- CONSTANTS FROM PDF ---------- */
    private const val SLOT_SECONDS = 600L           // Δt_slot = 10 min (section 5.2)
    private const val BATCH_DURATION = 24 * 3600L   // T_batch = 24h (section 6.1)
    private const val KEY_VALID_SECONDS = 72 * 3600L // T_Ki = 72h (section 5.3)
    private const val KEY_GRACE_SECONDS = (72 * 3600L) / 5 // T_Ki_grazia = 14.4h

    /* ---------- MASTER KEYS (PDF section 5.3) ---------- */
    private data class MasterKey(
        val keyBytes: ByteArray, // 32 B AES-256 key
        val startT: Long,        // t^start_Ki
        val endT: Long,          // t^end_Ki
        val graceT: Long         // t^grazia_Ki
    ) {
        // KMM-compatible equality check
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as MasterKey

            if (!keyBytes.contentEquals(other.keyBytes)) return false
            if (startT != other.startT) return false
            if (endT != other.endT) return false
            if (graceT != other.graceT) return false

            return true
        }

        // KMM-compatible hashCode
        override fun hashCode(): Int {
            var result = keyBytes.contentHashCode()
            result = 31 * result + startT.hashCode()
            result = 31 * result + endT.hashCode()
            result = 31 * result + graceT.hashCode()
            return result
        }
    }

    /* ---------- KEY RING WITH ROTATION ---------- */
    private val keyRing: List<MasterKey> = run {
        val now = Clock.System.now().epochSeconds
        // Create two overlapping keys as per PDF section 5.3.1
        listOf(
            // Previous key (in grace period)
            MasterKey(
                randomBytes(32),
                now - KEY_VALID_SECONDS - KEY_GRACE_SECONDS/2, // Started earlier
                now - KEY_GRACE_SECONDS/2,                     // Ended recently
                now + KEY_GRACE_SECONDS/2                      // In grace period
            ),
            // Current key
            MasterKey(
                randomBytes(32),
                now - KEY_VALID_SECONDS/2,                     // Started recently
                now + KEY_VALID_SECONDS/2,                     // Ends later
                now + KEY_VALID_SECONDS/2 + KEY_GRACE_SECONDS  // Grace later
            )
        )
    }

    /* ---------- HELPER FUNCTIONS ---------- */
    private fun randomBytes(size: Int): ByteArray =
        ByteArray(size) { Random.nextInt(0, 256).toByte() }

    private fun Long.toSlotIndex() = this / SLOT_SECONDS

    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        for (idx in indices) {
            val v = this[idx].toInt() and 0xFF
            out[idx * 2] = HEX_CHARS[v shr 4]
            out[idx * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return out.concatToString()
    }

    private fun Long.toBytes() =
        ByteArray(8) { i -> ((this shr (8 * (7 - i))) and 0xFF).toByte() }

    private fun ByteArray.toLong(): Long =
        (0 until 8).fold(0L) { acc, i -> (acc shl 8) or (this[i].toLong() and 0xFF) }

    private fun hexStringToByteArray(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /* ---------- BATCH GENERATION (PDF section 6.1) ---------- */
    fun generateBatch(userName: String): Batch {
        val user = registeredUsers[userName.lowercase()] ?: error("Unknown user: $userName")
        val ucode = hexStringToByteArray(user.id)
        require(ucode.size == 8) { "Ucode must be 8 B" }

        val now = Clock.System.now().epochSeconds
        val firstSlot = now.toSlotIndex()
        val slotsPerBatch = (BATCH_DURATION / SLOT_SECONDS).toInt() // 144 slots

        val bids = List(slotsPerBatch) { offset ->
            val slot = firstSlot + offset
            val slotTime = slot * SLOT_SECONDS

            // ✅ Find correct key for this slot time (PDF section 5.3.1)
            val key = keyRing.firstOrNull { slotTime in it.startT..<it.endT }
                ?: keyRing.first() // Fallback to first key

            // ✅ Encrypt Ucode || Slot (PDF section 5.1)
            val plain = ucode + slot.toBytes() // 8B + 8B = 16B
            val cipher = CryptographyManager(key.keyBytes).encryptRotatingIdentifier(plain)
            cipher.toHex() // 32 hex chars = 16 bytes
        }

        return Batch(firstSlot, bids)
    }

    /* ---------- BID RESOLUTION (PDF section 7.2.1) ---------- */
    fun resolveBid(payload: ByteArray, recvTime: Long): User? {
        if (payload.size != 16) {
            println("❌ Invalid payload size: ${payload.size} bytes")
            return null
        }

        // ✅ Get candidate keys (PDF section 7.2.1)
        val candidateKeys = keyRing.filter { recvTime in it.startT..<it.graceT }
        println("🔑 Trying ${candidateKeys.size} candidate keys for time $recvTime")

        for ((index, key) in candidateKeys.withIndex()) {
            try {
                val plain = CryptographyManager(key.keyBytes).decryptRotatingIdentifier(payload)
                val ucode = plain.copyOfRange(0, 8)
                val slot = plain.copyOfRange(8, 16).toLong()

                // ✅ Time window validation with delta (PDF section 5.2)
                val start = slot * SLOT_SECONDS
                val end = (slot + 1) * SLOT_SECONDS
                val delta = SLOT_SECONDS / 5 // δ = Δt_slot / 5

                println("🔍 Decrypted - Slot: $slot, Ucode: ${ucode.toHex()}")
                println("   Time window: ${start-delta} to ${end+delta} (current: $recvTime)")

                if (recvTime in (start - delta)..(end + delta)) {
                    val hex = ucode.toHex()
                    val user = registeredUsers.values.firstOrNull { it.id == hex }
                    if (user != null) {
                        println("✅ Resolved to user: ${user.name}")
                        return user
                    } else {
                        println("❌ User not found for Ucode: $hex")
                    }
                } else {
                    println("⏰ Time window mismatch")
                }
            } catch (e: Exception) {
                println("❌ Decryption failed with key $index: ${e.message}")
            }
        }

        println("❌ No valid resolution for BID")
        return null
    }

    /* ---------- ADDED MISSING FUNCTIONS ---------- */

    /**
     * Get user ID by name - for ClientBidManager compatibility
     */
    fun userIdFor(userName: String): String? {
        return registeredUsers[userName.lowercase()]?.id
    }

    /**
     * Resolve BID from hex string (convenience method)
     * This is the main API for clients to resolve BIDs
     */
    fun resolveBid(bidHex: String, recvTime: Long = Clock.System.now().epochSeconds): User? {
        val payload = hexStringToByteArray(bidHex)
        return resolveBid(payload, recvTime)
    }

    /**
     * Get server status for debugging and monitoring
     */
    fun getServerStatus(): String {
        val now = Clock.System.now().epochSeconds
        val activeKeys = keyRing.count { now in it.startT..<it.graceT }
        val totalUsers = registeredUsers.size
        return "Server Status: $totalUsers users, $activeKeys active keys"
    }

    /**
     * Get all registered users (for UI displays)
     */
    fun getRegisteredUsers(): List<User> = registeredUsers.values.toList()

    /**
     * Get user by name (case-insensitive)
     */
    fun getUser(userName: String): User? = registeredUsers[userName.lowercase()]

    /**
     * Check if user exists in the system
     */
    fun userExists(userName: String): Boolean = registeredUsers.containsKey(userName.lowercase())

    /**
     * Get key ring information for debugging
     */
    fun getKeyInfo(): List<String> {
        val now = Clock.System.now().epochSeconds
        return keyRing.mapIndexed { index, key ->
            val status = when {
                now in key.startT..<key.endT -> "ACTIVE"
                now in key.endT..<key.graceT -> "GRACE"
                else -> "EXPIRED"
            }
            "Key $index: $status (${key.startT} -> ${key.endT} -> ${key.graceT})"
        }
    }

    /**
     * Validate a BID without resolving to user (for testing)
     */
    fun validateBid(bidHex: String, recvTime: Long = Clock.System.now().epochSeconds): Boolean {
        val payload = hexStringToByteArray(bidHex)
        if (payload.size != 16) return false

        val candidateKeys = keyRing.filter { recvTime in it.startT..<it.graceT }

        for (key in candidateKeys) {
            try {
                val plain = CryptographyManager(key.keyBytes).decryptRotatingIdentifier(payload)
                val ucode = plain.copyOfRange(0, 8)
                val slot = plain.copyOfRange(8, 16).toLong()

                val start = slot * SLOT_SECONDS
                val end = (slot + 1) * SLOT_SECONDS
                val delta = SLOT_SECONDS / 5

                if (recvTime in (start - delta)..(end + delta)) {
                    return true // Valid BID structure and time window
                }
            } catch (e: Exception) {
                // Continue to next key
            }
        }
        return false
    }

    /**
     * Get current slot information
     */
    fun getCurrentSlotInfo(): String {
        val now = Clock.System.now().epochSeconds
        val currentSlot = now.toSlotIndex()
        val slotStart = currentSlot * SLOT_SECONDS
        val slotEnd = (currentSlot + 1) * SLOT_SECONDS
        return "Current Slot: $currentSlot ($slotStart - $slotEnd)"
    }

    /**
     * Simulate server restart (for testing key rotation)
     */
    fun simulateServerRestart() {
        // In a real server, this would reload keys from secure storage
        println("🔄 Server simulated restart - keys remain the same")
    }
}