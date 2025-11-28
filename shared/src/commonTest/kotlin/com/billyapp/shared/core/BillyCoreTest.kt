package com.billyapp.shared.core

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
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BillyCoreTest {
    // --- FAKES ---

    class FakeSecureRepo : SecureRepository {
        val queue = mutableListOf<Bid>()
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
            return QueuedItem(0L, queue[0])
        }

        override fun deleteByKey(k: Long) {
            if (queue.isNotEmpty()) queue.removeAt(0)
        }
    }

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

    // Fake Network Data Source
    class FakeNetworkDataSource(
        private val resolveResponse: ResolveResponse? = null,
        private val batchResponse: BatchResponse? = null,
        private val shouldThrow: Boolean = false,
    ) : NetworkDataSource {
        override suspend fun resolveContact(bidHex: String): ResolveResponse {
            if (shouldThrow) throw Exception("Network Error")
            return resolveResponse ?: throw Exception("Mock resolve not set")
        }

        override suspend fun downloadBatch(): BatchResponse {
            if (shouldThrow) throw Exception("Network Error")
            return batchResponse ?: throw Exception("Mock batch not set")
        }

        // [FIX] Dummy implementation of Auth methods required by the interface
        override suspend fun login(
            email: String,
            password: String,
        ): NetworkDataSource.AuthResponse {
            throw NotImplementedError("Not used in BillyCore tests")
        }

        override suspend fun register(
            username: String,
            email: String,
            password: String,
        ): NetworkDataSource.AuthResponse {
            throw NotImplementedError("Not used in BillyCore tests")
        }
    }

    // --- TESTS ---

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

    @Test
    fun ingestPacket_should_ignore_duplicate_packets() =
        runTest {
            val secureRepo = FakeSecureRepo()
            val core = createTestCore(secureRepo)
            val validHex = "000102030405060708090a0b0c0d0e0f"

            core.ingestPacket(validHex)
            core.ingestPacket(validHex) // Duplicate

            assertEquals(1, secureRepo.queue.size)
        }

    // --- SYNC TESTS ---

    @Test
    fun syncQueue_should_resolve_and_dequeue_on_success() =
        runTest {
            val secureRepo = FakeSecureRepo()
            val resolvedRepo = FakeResolvedRepo()
            val validHex = "000102030405060708090a0b0c0d0e0f"
            secureRepo.queue.add(Bid(validHex))

            val response = ResolveResponse("John Doe", 1234567890L)
            val fakeApi = FakeNetworkDataSource(resolveResponse = response)

            val testDispatcher = UnconfinedTestDispatcher()
            val core = BillyCore(secureRepo, resolvedRepo, fakeApi, testDispatcher)

            core.syncQueue()

            assertEquals(0, secureRepo.queue.size, "Item should be dequeued")
            assertEquals(1, resolvedRepo.users.size)
            assertEquals("John Doe", resolvedRepo.users[0])
        }

    @Test
    fun syncQueue_should_retain_item_on_network_failure() =
        runTest {
            val secureRepo = FakeSecureRepo()
            val validHex = "000102030405060708090a0b0c0d0e0f"
            secureRepo.queue.add(Bid(validHex))

            val fakeApi = FakeNetworkDataSource(shouldThrow = true)

            val testDispatcher = UnconfinedTestDispatcher()
            val core = BillyCore(secureRepo, FakeResolvedRepo(), fakeApi, testDispatcher)

            core.syncQueue()

            assertEquals(1, secureRepo.queue.size, "Item should remain in queue")
        }

    // --- BATCH TESTS ---

    @Test
    fun ensureAdvertisingBatch_should_fetch_when_batch_below_threshold() =
        runTest {
            val secureRepo = FakeSecureRepo()
            val currentSlot = kotlinx.datetime.Clock.System.now().epochSeconds / 600L
            secureRepo.batch = emptyMap()

            val response =
                BatchResponse(
                    startSlot = currentSlot + 100,
                    slotDuration = 600,
                    bIds = listOf("000102030405060708090a0b0c0d0e0f"),
                )

            val fakeApi = FakeNetworkDataSource(batchResponse = response)

            val testDispatcher = UnconfinedTestDispatcher()
            val core = BillyCore(secureRepo, FakeResolvedRepo(), fakeApi, testDispatcher)

            core.ensureAdvertisingBatch()

            assertEquals(1, secureRepo.batch.size)
        }

    private fun createTestCore(
        secureRepo: SecureRepository = FakeSecureRepo(),
        resolvedRepo: ResolvedRepository = FakeResolvedRepo(),
    ): BillyCore {
        val testDispatcher = UnconfinedTestDispatcher()
        val dummyApi = FakeNetworkDataSource()
        return BillyCore(secureRepo, resolvedRepo, dummyApi, testDispatcher)
    }
}
