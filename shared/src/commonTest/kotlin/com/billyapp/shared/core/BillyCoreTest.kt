package com.billyapp.shared.core

import com.billyapp.shared.domain.model.AppError
import com.billyapp.shared.domain.model.BatchResponse
import com.billyapp.shared.domain.model.Bid
import com.billyapp.shared.domain.model.QueuedItem
import com.billyapp.shared.domain.model.ResolveResponse
import com.billyapp.shared.domain.model.ResolvedUser
import com.billyapp.shared.domain.repository.NetworkDataSource
import com.billyapp.shared.domain.repository.ResolvedRepository
import com.billyapp.shared.domain.repository.SecureRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Unit Tests for the Core Domain Logic.
 *
 * TEST STRATEGY:
 * - Uses "Fake" implementations for Repositories to avoid mocking frameworks overhead
 *   and to simulate stateful behavior (like Queue persistence) more naturally.
 * - Uses `runTest` and `UnconfinedTestDispatcher` to handle coroutines synchronously.
 * - Focuses on the Orchestration Logic:
 *   1. Advertising: Slot calculation and Batch retrieval.
 *   2. Scanning: Rate limiting and Queue management.
 *   3. Sync: Transactional processing (Peek -> Network -> Delete).
 *   4. Maintenance: Batch refilling and Cache eviction.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BillyCoreTest {
    // --- FAKES ---

    /**
     * Fake Clock implementation to control time deterministically.
     * Allows us to simulate time passing for rate limiting and slot calculation tests.
     */
    class FakeClock : Clock {
        var currentTime = Instant.fromEpochMilliseconds(1000000)

        override fun now(): Instant = currentTime
    }

    /**
     * Fake SecureRepository simulating the Disk Persistence Layer.
     * Uses in-memory collections (List/Map) to mimic SQLite tables.
     */
    class FakeSecureRepo : SecureRepository {
        // Simulates the FIFO Ingestion Queue
        val queue = mutableListOf<Bid>()

        // Simulates the Key-Value Batch Table
        var batch: Map<Long, Bid> = emptyMap()

        override fun getBatchItem(slot: Long): Bid? = batch[slot]

        override fun replaceBatch(
            startSlot: Long,
            bids: List<Bid>,
        ) {
            val newBatch = mutableMapOf<Long, Bid>()
            bids.forEachIndexed { index, bid -> newBatch[startSlot + index] = bid }
            batch = newBatch
        }

        override fun getMaxBatchSlot(): Long? = batch.keys.maxOrNull()

        override fun enqueue(bid: Bid) {
            queue.add(bid)
        }

        override fun peekHead(): QueuedItem? {
            if (queue.isEmpty()) return null
            // Simulate DB Primary Key with 0L
            return QueuedItem(0L, queue[0])
        }

        override fun deleteByKey(k: Long) {
            if (queue.isNotEmpty()) queue.removeAt(0)
        }
    }

    /**
     * Fake ResolvedRepository simulating the UI State Layer.
     * Captures updates to verify that the Core correctly propagates resolved contacts.
     */
    class FakeResolvedRepo : ResolvedRepository {
        val users = mutableListOf<String>()
        override val activeSet: StateFlow<List<ResolvedUser>> = MutableStateFlow(emptyList())

        override fun onMatchFound(
            name: String,
            timestamp: Long,
        ) {
            users.add(name)
        }

        override fun pruneActiveSet() {}
    }

    /**
     * Fake NetworkDataSource simulating the API Layer.
     * Allows injecting success/failure responses to test Core resiliency.
     */
    class FakeNetworkDataSource(
        private val resolveResponse: ResolveResponse? = null,
        private val batchResponse: BatchResponse? = null,
        private val shouldThrow: Boolean = false,
    ) : NetworkDataSource {
        var downloadBatchCalled = false

        override suspend fun resolveContact(bidHex: String): Result<ResolveResponse, AppError> {
            if (shouldThrow) return Result.Failure(AppError.Network.NoInternet)
            return resolveResponse?.let { Result.Success(it) } ?: throw Exception("Mock resolve not set")
        }

        override suspend fun downloadBatch(): Result<BatchResponse, AppError> {
            downloadBatchCalled = true
            if (shouldThrow) return Result.Failure(AppError.Network.NoInternet)
            return batchResponse?.let { Result.Success(it) } ?: throw Exception("Mock batch not set")
        }

        override suspend fun login(
            email: String,
            password: String,
        ): Result<NetworkDataSource.AuthResponse, AppError> {
            throw NotImplementedError("Not used in BillyCore tests")
        }

        override suspend fun register(
            username: String,
            email: String,
            password: String,
        ): Result<NetworkDataSource.AuthResponse, AppError> {
            throw NotImplementedError("Not used in BillyCore tests")
        }
    }

    // --- TESTS ---

    /**
     * Verifies that the Core correctly maps the current epoch time to a slot index
     * and retrieves the corresponding key from the repository.
     */
    @Test
    fun getCurrentBid_should_return_bid_for_current_slot() {
        val secureRepo = FakeSecureRepo()
        val clock = FakeClock()
        // Slot duration is 600s. Time 1000000ms = 1000s. Slot = 1.
        // Let's set time to 600s (Slot 1)
        clock.currentTime = Instant.fromEpochSeconds(600)

        val bid = Bid("000102030405060708090a0b0c0d0e0f")
        secureRepo.replaceBatch(1, listOf(bid))

        val core = createTestCore(secureRepo, clock = clock)

        val result = core.getCurrentBid()
        assertEquals(bid.hex, result?.hex)
    }

    /**
     * Verifies that the Core returns null gracefully when the batch is exhausted
     * or no key exists for the current time slot.
     */
    @Test
    fun getCurrentBid_should_return_null_if_no_bid() {
        val secureRepo = FakeSecureRepo()
        val core = createTestCore(secureRepo)
        assertNull(core.getCurrentBid())
    }

    /**
     * Verifies the "Happy Path" for packet ingestion:
     * A valid hex string should be converted to a Bid and persisted to the queue.
     */
    @Test
    fun ingestPacket_should_add_valid_hex_to_queue() =
        runTest {
            val secureRepo = FakeSecureRepo()
            val core = createTestCore(secureRepo)

            val validHex = "000102030405060708090a0b0c0d0e0f"
            core.ingestPacket(validHex)

            assertEquals(1, secureRepo.queue.size)
            assertEquals(validHex, secureRepo.queue[0].hex)
        }

    /**
     * Verifies the Rate Limiting logic (Volatile Cache).
     * Ensures that duplicate packets from the same device are ignored if they arrive
     * within the `INGESTION_RATE_LIMIT_SECONDS` window.
     */
    @Test
    fun ingestPacket_should_ignore_duplicate_packets_within_rate_limit() =
        runTest {
            val secureRepo = FakeSecureRepo()
            val clock = FakeClock()
            val core = createTestCore(secureRepo, clock = clock)
            val validHex = "000102030405060708090a0b0c0d0e0f"

            // 1. First ingest: Should be accepted
            core.ingestPacket(validHex)
            assertEquals(1, secureRepo.queue.size)

            // 2. Advance time by 50s (Limit is 100s)
            clock.currentTime = clock.currentTime + 50.seconds

            // 3. Second ingest: Should be ignored (Rate Limit Hit)
            core.ingestPacket(validHex)
            assertEquals(1, secureRepo.queue.size)

            // 4. Advance time by another 60s (Total 110s > 100s)
            clock.currentTime = clock.currentTime + 60.seconds

            // 5. Third ingest: Should be accepted (Rate Limit Expired)
            core.ingestPacket(validHex)
            assertEquals(2, secureRepo.queue.size)
        }

    /**
     * Verifies the Cache Eviction Policy.
     * Ensures that the volatile cache doesn't grow indefinitely, preventing OOM errors.
     * When the cache hits `MAX_VOLATILE_CACHE_SIZE`, it should be cleared.
     */
    @Test
    fun ingestPacket_should_evict_cache_when_full() =
        runTest {
            val secureRepo = FakeSecureRepo()
            val core = createTestCore(secureRepo)

            // MAX_VOLATILE_CACHE_SIZE = 1000
            // Fill cache to capacity
            for (i in 0 until 1000) {
                val hex = i.toString(16).padStart(32, '0')
                core.ingestPacket(hex)
            }
            assertEquals(1000, secureRepo.queue.size)

            // Add one more to trigger clear
            val overflowHex = "ffffffffffffffffffffffffffffffff"
            core.ingestPacket(overflowHex)
            assertEquals(1001, secureRepo.queue.size)

            // Verify the first one can be re-ingested immediately (evicted)
            val firstHex = "00000000000000000000000000000000"
            core.ingestPacket(firstHex)
            assertEquals(1002, secureRepo.queue.size)
        }

    /**
     * Verifies Defensive Programming.
     * Malformed data from the BLE stack should be dropped silently without crashing the app.
     */
    @Test
    fun ingestPacket_should_handle_malformed_bid_gracefully() =
        runTest {
            val secureRepo = FakeSecureRepo()
            val core = createTestCore(secureRepo)

            // Should not throw exception
            core.ingestPacket("invalid-hex")

            assertEquals(0, secureRepo.queue.size)
        }

    // --- SYNC TESTS ---

    /**
     * Verifies the Transactional Sync Logic (Success Case).
     * 1. Peek item from queue.
     * 2. Call API to resolve.
     * 3. On Success: Remove item from queue AND update UI.
     */
    @Test
    fun syncQueue_should_resolve_and_dequeue_on_success() =
        runTest {
            val secureRepo = FakeSecureRepo()
            val resolvedRepo = FakeResolvedRepo()
            val validHex = "000102030405060708090a0b0c0d0e0f"
            secureRepo.queue.add(Bid(validHex))

            val response = ResolveResponse("John Doe")
            val fakeApi = FakeNetworkDataSource(resolveResponse = response)

            val core = createTestCore(secureRepo, resolvedRepo, fakeApi)

            core.syncQueue()

            assertEquals(0, secureRepo.queue.size, "Item should be dequeued after successful sync")
            assertEquals(1, resolvedRepo.users.size)
            assertEquals("John Doe", resolvedRepo.users[0])
        }

    /**
     * Verifies the Transactional Sync Logic (Failure Case).
     * If the network call fails, the item MUST remain in the queue to ensure "At-Least-Once" delivery.
     */
    @Test
    fun syncQueue_should_retain_item_on_network_failure() =
        runTest {
            val secureRepo = FakeSecureRepo()
            val validHex = "000102030405060708090a0b0c0d0e0f"
            secureRepo.queue.add(Bid(validHex))

            val fakeApi = FakeNetworkDataSource(shouldThrow = true)
            val core = createTestCore(secureRepo, api = fakeApi)

            core.syncQueue()

            assertEquals(1, secureRepo.queue.size, "Item should remain in queue for retry")
        }

    /**
     * Verifies that the sync process is robust against empty queues.
     */
    @Test
    fun syncQueue_should_do_nothing_when_empty() =
        runTest {
            val secureRepo = FakeSecureRepo()
            val core = createTestCore(secureRepo)

            // Should not crash
            core.syncQueue()
        }

    // --- BATCH TESTS ---

    /**
     * Verifies the Proactive Batch Refill logic.
     * If the local key buffer drops below `BATCH_REFILL_THRESHOLD`, the Core should trigger a download.
     */
    @Test
    fun ensureAdvertisingBatch_should_fetch_when_batch_below_threshold() =
        runTest {
            val secureRepo = FakeSecureRepo()
            val clock = FakeClock()
            // Current time 0. Current Slot 0.
            // Max Slot 0.
            // Threshold 24. (0 - 0) < 24 -> True.

            val response =
                BatchResponse(
                    startSlot = 100,
                    slotDuration = 600,
                    bIds = listOf("000102030405060708090a0b0c0d0e0f"),
                )

            val fakeApi = FakeNetworkDataSource(batchResponse = response)
            val core = createTestCore(secureRepo, api = fakeApi, clock = clock)

            core.ensureAdvertisingBatch()

            assertTrue(fakeApi.downloadBatchCalled)
            assertEquals(1, secureRepo.batch.size)
        }

    /**
     * Verifies that we don't waste bandwidth fetching keys if we already have enough.
     */
    @Test
    fun ensureAdvertisingBatch_should_not_fetch_when_batch_sufficient() =
        runTest {
            val secureRepo = FakeSecureRepo()
            val clock = FakeClock()

            // Current Slot 0.
            // We need Max Slot >= 24 to be safe.
            val bids = List(30) { Bid("000102030405060708090a0b0c0d0e0f") }
            secureRepo.replaceBatch(0, bids) // Slots 0 to 29. Max = 29.

            val fakeApi = FakeNetworkDataSource()
            val core = createTestCore(secureRepo, api = fakeApi, clock = clock)

            core.ensureAdvertisingBatch()

            assertTrue(!fakeApi.downloadBatchCalled)
        }

    /**
     * Verifies that background maintenance tasks don't crash the app on network failure.
     */
    @Test
    fun ensureAdvertisingBatch_should_handle_network_error_gracefully() =
        runTest {
            val secureRepo = FakeSecureRepo()
            val fakeApi = FakeNetworkDataSource(shouldThrow = true)
            val core = createTestCore(secureRepo, api = fakeApi)

            // Should not throw
            core.ensureAdvertisingBatch()
        }

    /**
     * Helper to construct the System Under Test (SUT) with default or injected dependencies.
     */
    private fun createTestCore(
        secureRepo: SecureRepository = FakeSecureRepo(),
        resolvedRepo: ResolvedRepository = FakeResolvedRepo(),
        api: NetworkDataSource = FakeNetworkDataSource(),
        clock: Clock = FakeClock(),
    ): BillyCore {
        val testDispatcher = UnconfinedTestDispatcher()
        return BillyCore(secureRepo, resolvedRepo, api, testDispatcher, clock)
    }
}
