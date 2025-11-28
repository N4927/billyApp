package com.billyapp.shared.data.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.billyapp.shared.cache.BillyDatabase
import com.billyapp.shared.domain.model.Bid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SecureRepositoryTest {
    private lateinit var repository: SecureRepositoryImpl
    private lateinit var driver: JdbcSqliteDriver

    @Before
    fun setup() {
        // Create in-memory DB
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // Create schema (tables)
        BillyDatabase.Schema.create(driver)
        val database = BillyDatabase(driver)
        repository = SecureRepositoryImpl(database)
    }

    @Test
    fun testEnqueueAndFIFO() {
        val bid1 = Bid("11111111111111111111111111111111")
        val bid2 = Bid("22222222222222222222222222222222")

        repository.enqueue(bid1)
        repository.enqueue(bid2)

        // Peek must return the oldest item (FIFO)
        val head = repository.peekHead()
        assertNotNull(head)
        assertEquals(bid1.hex, head?.bid?.hex)

        // Delete (ACK)
        repository.deleteByKey(head!!.id)

        // Peek must return the second item
        val next = repository.peekHead()
        assertNotNull(next)
        assertEquals(bid2.hex, next?.bid?.hex)
    }

    @Test
    fun testBatchReplacement() {
        val bid = Bid("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
        repository.replaceBatch(100, listOf(bid))

        val retrieved = repository.getBatchItem(100)
        assertEquals(bid.hex, retrieved?.hex)

        val empty = repository.getBatchItem(101)
        assertNull(empty)
    }

    @Test
    fun testGetMaxBatchSlot() {
        val bid1 = Bid("11111111111111111111111111111111")
        val bid2 = Bid("22222222222222222222222222222222")

        // Insert batch starting at 100 with 2 items -> slots 100, 101
        repository.replaceBatch(100, listOf(bid1, bid2))

        val maxSlot = repository.getMaxBatchSlot()
        assertEquals(101L, maxSlot)

        // Test empty
        repository.replaceBatch(200, emptyList())
        val emptyMax = repository.getMaxBatchSlot()
        assertNull(emptyMax)
    }

    @Test
    fun testQueueCapacityEviction() {
        // Re-init repo with small capacity
        repository = SecureRepositoryImpl(BillyDatabase(driver), maxQueueCapacity = 2)

        val bid1 = Bid("11111111111111111111111111111111")
        val bid2 = Bid("22222222222222222222222222222222")
        val bid3 = Bid("33333333333333333333333333333333")

        repository.enqueue(bid1)
        repository.enqueue(bid2)
        // Queue is full (2 items)

        repository.enqueue(bid3)
        // Should evict bid1 (oldest)

        val head = repository.peekHead()
        assertNotNull(head)
        assertEquals(bid2.hex, head?.bid?.hex) // Head should be bid2 now
    }
}
