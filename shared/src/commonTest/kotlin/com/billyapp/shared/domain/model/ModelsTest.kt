package com.billyapp.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Unit Tests for Domain Models.
 *
 * TEST STRATEGY:
 * - Focuses on "Domain Invariants" enforced by the Value Classes (e.g., Bid).
 * - Verifies that invalid data cannot be constructed (Fail Fast).
 * - Checks normalization logic (e.g., Hex casing).
 */
class ModelsTest {
    /**
     * Verifies that a valid 32-character hex string is accepted.
     */
    @Test
    fun bid_should_accept_valid_hex_string() {
        val validHex = "aabbcc11223344556677889900aabbcc"
        val bid = Bid(validHex)
        assertEquals(validHex, bid.hex)
    }

    /**
     * Verifies Normalization Logic.
     * The system must treat "AABB" and "aabb" as identical.
     * We enforce lowercase storage to simplify equality checks later.
     */
    @Test
    fun bid_should_normalize_uppercase_to_lowercase() {
        val upperHex = "AABBCC11223344556677889900AABBCC"
        val expected = "aabbcc11223344556677889900aabbcc"
        val bid = Bid(upperHex)
        assertEquals(expected, bid.hex)
    }

    /**
     * Verifies Length Invariant.
     * BIDs must be exactly 16 bytes (32 hex chars).
     * Anything else indicates data corruption or a protocol mismatch.
     */
    @Test
    fun bid_should_throw_on_invalid_length() {
        // Too short
        assertFailsWith<IllegalArgumentException> {
            Bid("aabbcc")
        }
        // Too long
        assertFailsWith<IllegalArgumentException> {
            Bid("aabbcc11223344556677889900aabbccddeeff")
        }
    }

    /**
     * Verifies Character Set Invariant.
     * Only 0-9, a-f, A-F are allowed.
     */
    @Test
    fun bid_should_throw_on_invalid_characters() {
        assertFailsWith<IllegalArgumentException> {
            Bid("zzbbcc11223344556677889900aabbcc") // 'z' is not hex
        }
    }
}
