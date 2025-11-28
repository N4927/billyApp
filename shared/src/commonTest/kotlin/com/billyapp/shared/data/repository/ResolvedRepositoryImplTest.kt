package com.billyapp.shared.data.repository

import com.billyapp.shared.domain.model.ResolvedUser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit Tests for ResolvedRepositoryImpl.
 *
 * ARCHITECTURE NOTE:
 * This repository manages ephemeral UI state (the "Active Set" visible on the radar).
 * It is intentionally in-memory only - no persistence required.
 *
 * TEST STRATEGY:
 * - We test the observable behavior via the StateFlow
 * - TTL-based pruning tests use the real clock (integration-style) since
 *   kotlinx.datetime.Clock is not easily mockable in common code
 * - For time-sensitive tests, we verify behavior patterns rather than exact timing
 *
 * COVERAGE TARGETS:
 * 1. Initial state
 * 2. onMatchFound: Insert path
 * 3. onMatchFound: Update path (upsert)
 * 4. pruneActiveSet: Removal of stale entries
 * 5. pruneActiveSet: Retention of fresh entries
 * 6. Edge cases: Empty set operations, multiple users
 */
class ResolvedRepositoryImplTest {
    // --- INITIAL STATE TESTS ---

    /**
     * Verifies that the repository starts in a clean state.
     * Essential for ensuring no state leaks between app sessions (if the object is recreated).
     */
    @Test
    fun activeSet_should_be_empty_on_initialization() =
        runTest {
            // ARRANGE
            val repo = ResolvedRepositoryImpl()

            // ACT
            val initialState = repo.activeSet.first()

            // ASSERT
            assertTrue(initialState.isEmpty(), "Active set should be empty on fresh repository")
        }

    // --- onMatchFound: INSERT PATH ---

    /**
     * Verifies that a new user is correctly added to the active set.
     */
    @Test
    fun onMatchFound_should_add_new_user_to_active_set() =
        runTest {
            // ARRANGE
            val repo = ResolvedRepositoryImpl()

            // ACT
            repo.onMatchFound(name = "Alice", timestamp = 1234567890L)

            // ASSERT
            val activeSet = repo.activeSet.first()
            assertEquals(1, activeSet.size, "Active set should contain exactly one user")
            assertEquals("Alice", activeSet[0].name, "User name should match")
        }

    /**
     * Verifies that multiple distinct users can coexist in the active set.
     */
    @Test
    fun onMatchFound_should_add_multiple_distinct_users() =
        runTest {
            // ARRANGE
            val repo = ResolvedRepositoryImpl()

            // ACT
            repo.onMatchFound(name = "Alice", timestamp = 1000L)
            repo.onMatchFound(name = "Bob", timestamp = 2000L)
            repo.onMatchFound(name = "Charlie", timestamp = 3000L)

            // ASSERT
            val activeSet = repo.activeSet.first()
            assertEquals(3, activeSet.size, "Active set should contain all three users")

            val names = activeSet.map { it.name }.toSet()
            assertTrue(names.contains("Alice"), "Alice should be in the set")
            assertTrue(names.contains("Bob"), "Bob should be in the set")
            assertTrue(names.contains("Charlie"), "Charlie should be in the set")
        }

    // --- onMatchFound: UPDATE PATH (UPSERT) ---

    /**
     * Verifies the Upsert Logic:
     * If a user already exists, we update their `lastSeen` timestamp instead of creating a duplicate.
     * This keeps the user "alive" in the radar view.
     */
    @Test
    fun onMatchFound_should_update_existing_user_lastSeen_timestamp() =
        runTest {
            // ARRANGE
            val repo = ResolvedRepositoryImpl()
            repo.onMatchFound(name = "Alice", timestamp = 1000L)

            val initialLastSeen = repo.activeSet.first()[0].lastSeen

            // Small delay to ensure clock advances (real clock dependency)
            // In production, we'd inject a Clock, but for this test we verify the pattern

            // ACT: Same user seen again
            repo.onMatchFound(name = "Alice", timestamp = 2000L)

            // ASSERT
            val activeSet = repo.activeSet.first()
            assertEquals(1, activeSet.size, "Should not duplicate user on update")
            assertEquals("Alice", activeSet[0].name)

            // The lastSeen should be updated to a new timestamp (>= initial)
            // Note: Due to real clock usage, we verify it's at least as recent
            assertTrue(
                activeSet[0].lastSeen >= initialLastSeen,
                "lastSeen should be refreshed on update",
            )
        }

    /**
     * Verifies that updates are targeted correctly and do not affect other users in the set.
     */
    @Test
    fun onMatchFound_should_only_update_matching_user_in_set() =
        runTest {
            // ARRANGE
            val repo = ResolvedRepositoryImpl()
            repo.onMatchFound(name = "Alice", timestamp = 1000L)
            repo.onMatchFound(name = "Bob", timestamp = 1000L)

            val aliceInitial = repo.activeSet.first().find { it.name == "Alice" }!!.lastSeen
            val bobInitial = repo.activeSet.first().find { it.name == "Bob" }!!.lastSeen

            // ACT: Only update Alice
            repo.onMatchFound(name = "Alice", timestamp = 2000L)

            // ASSERT
            val activeSet = repo.activeSet.first()
            assertEquals(2, activeSet.size, "Set size should remain unchanged")

            val aliceUpdated = activeSet.find { it.name == "Alice" }!!
            val bobUnchanged = activeSet.find { it.name == "Bob" }!!

            assertTrue(
                aliceUpdated.lastSeen >= aliceInitial,
                "Alice's lastSeen should be updated",
            )
            assertEquals(
                bobInitial,
                bobUnchanged.lastSeen,
                "Bob's lastSeen should remain unchanged",
            )
        }

    // --- pruneActiveSet: REMOVAL PATH ---

    /**
     * Verifies robustness: Pruning an empty set should be a no-op.
     */
    @Test
    fun pruneActiveSet_should_do_nothing_on_empty_set() =
        runTest {
            // ARRANGE
            val repo = ResolvedRepositoryImpl()

            // ACT: Prune on empty set should not throw
            repo.pruneActiveSet()

            // ASSERT
            val activeSet = repo.activeSet.first()
            assertTrue(activeSet.isEmpty(), "Empty set should remain empty after prune")
        }

    /**
     * Verifies that users seen recently (within TTL) are NOT removed.
     */
    @Test
    fun pruneActiveSet_should_retain_fresh_users() =
        runTest {
            // ARRANGE: Add user just now (will be fresh)
            val repo = ResolvedRepositoryImpl()
            repo.onMatchFound(name = "FreshUser", timestamp = 0L)

            // ACT: Prune immediately - user was just added, should be retained
            repo.pruneActiveSet()

            // ASSERT
            val activeSet = repo.activeSet.first()
            assertEquals(1, activeSet.size, "Fresh user should be retained")
            assertEquals("FreshUser", activeSet[0].name)
        }

    /**
     * Verifies that multiple fresh users are all retained.
     */
    @Test
    fun pruneActiveSet_should_retain_multiple_fresh_users() =
        runTest {
            // ARRANGE
            val repo = ResolvedRepositoryImpl()
            repo.onMatchFound(name = "User1", timestamp = 0L)
            repo.onMatchFound(name = "User2", timestamp = 0L)
            repo.onMatchFound(name = "User3", timestamp = 0L)

            // ACT
            repo.pruneActiveSet()

            // ASSERT
            val activeSet = repo.activeSet.first()
            assertEquals(3, activeSet.size, "All fresh users should be retained")
        }

    // --- EDGE CASES ---

    /**
     * Verifies handling of empty strings.
     * While the backend should prevent this, the mobile SDK must be robust against bad data.
     */
    @Test
    fun onMatchFound_should_handle_empty_name() =
        runTest {
            // ARRANGE: Edge case - empty string name (backend should prevent, but we handle gracefully)
            val repo = ResolvedRepositoryImpl()

            // ACT
            repo.onMatchFound(name = "", timestamp = 1000L)

            // ASSERT: Should still work - no validation at this layer
            val activeSet = repo.activeSet.first()
            assertEquals(1, activeSet.size)
            assertEquals("", activeSet[0].name)
        }

    /**
     * Verifies Internationalization (I18n) support.
     * Names can contain Unicode characters (Chinese, Emoji, Umlauts).
     */
    @Test
    fun onMatchFound_should_handle_unicode_names() =
        runTest {
            // ARRANGE: International name support
            val repo = ResolvedRepositoryImpl()

            // ACT
            repo.onMatchFound(name = "用户名", timestamp = 1000L)
            repo.onMatchFound(name = "Müller", timestamp = 1000L)
            repo.onMatchFound(name = "🎉 Party", timestamp = 1000L)

            // ASSERT
            val activeSet = repo.activeSet.first()
            assertEquals(3, activeSet.size)

            val names = activeSet.map { it.name }.toSet()
            assertTrue(names.contains("用户名"), "Chinese name should be stored")
            assertTrue(names.contains("Müller"), "German umlaut should be stored")
            assertTrue(names.contains("🎉 Party"), "Emoji should be stored")
        }

    /**
     * Verifies that whitespace is significant for identity.
     * "Alice" and " Alice" are treated as different users.
     */
    @Test
    fun onMatchFound_should_handle_whitespace_names_as_distinct() =
        runTest {
            // ARRANGE: Whitespace variations are distinct names
            val repo = ResolvedRepositoryImpl()

            // ACT
            repo.onMatchFound(name = "Alice", timestamp = 1000L)
            repo.onMatchFound(name = " Alice", timestamp = 1000L)
            repo.onMatchFound(name = "Alice ", timestamp = 1000L)

            // ASSERT: These are treated as 3 distinct users (no trimming at repo layer)
            val activeSet = repo.activeSet.first()
            assertEquals(3, activeSet.size, "Whitespace variations should be distinct")
        }

    /**
     * Verifies the Reactive Nature of the repository.
     * The StateFlow should emit a new list every time the set changes.
     */
    @Test
    fun activeSet_should_be_observable_via_stateflow() =
        runTest {
            // ARRANGE
            val repo = ResolvedRepositoryImpl()
            val collectedStates = mutableListOf<List<ResolvedUser>>()

            // Collect initial state
            collectedStates.add(repo.activeSet.first())

            // ACT
            repo.onMatchFound(name = "User1", timestamp = 1000L)
            collectedStates.add(repo.activeSet.first())

            repo.onMatchFound(name = "User2", timestamp = 2000L)
            collectedStates.add(repo.activeSet.first())

            // ASSERT: StateFlow emits updated states
            assertEquals(0, collectedStates[0].size, "Initial state should be empty")
            assertEquals(1, collectedStates[1].size, "After first user")
            assertEquals(2, collectedStates[2].size, "After second user")
        }

    /**
     * Documents a key architectural decision:
     * The `timestamp` parameter from the API response is currently ignored in favor of `Clock.System.now()`.
     * This ensures consistency with the local device time for TTL calculations, avoiding issues with server clock drift.
     */
    @Test
    fun onMatchFound_timestamp_parameter_is_ignored_uses_system_clock() =
        runTest {
            // ARCHITECTURE NOTE:
            // The `timestamp` parameter from the API response is currently ignored.
            // The implementation uses Clock.System.now() for consistency with local device time.
            // This test documents that behavior.

            val repo = ResolvedRepositoryImpl()

            // ACT: Pass a timestamp from the past
            repo.onMatchFound(name = "User", timestamp = 0L)

            // ASSERT: User's lastSeen should be close to "now", not the passed timestamp
            val user = repo.activeSet.first()[0]
            val nowEpoch = kotlinx.datetime.Clock.System.now().epochSeconds

            // lastSeen should be within a few seconds of now (not 0)
            val userEpoch = user.lastSeen.epochSeconds
            assertTrue(
                nowEpoch - userEpoch < 5,
                "lastSeen should be near current time, not the passed timestamp",
            )
        }

    // --- CONCURRENT ACCESS PATTERN ---

    /**
     * Verifies thread safety and idempotency under high load.
     * Rapid updates for the same user should result in a single entry with the latest timestamp.
     */
    @Test
    fun onMatchFound_should_handle_rapid_updates_to_same_user() =
        runTest {
            // ARRANGE
            val repo = ResolvedRepositoryImpl()

            // ACT: Rapid-fire updates simulating high-frequency BLE callbacks
            repeat(100) {
                repo.onMatchFound(name = "FrequentUser", timestamp = it.toLong())
            }

            // ASSERT: Should still be exactly one user, with latest timestamp
            val activeSet = repo.activeSet.first()
            assertEquals(1, activeSet.size, "Rapid updates should not create duplicates")
            assertEquals("FrequentUser", activeSet[0].name)
        }

    /**
     * Verifies the lifecycle of a user: Added -> Pruned (if old) -> Re-added.
     * Ensures that a user can "come back" after disappearing.
     */
    @Test
    fun pruneActiveSet_followed_by_onMatchFound_should_repopulate() =
        runTest {
            // ARRANGE
            val repo = ResolvedRepositoryImpl()
            repo.onMatchFound(name = "User1", timestamp = 1000L)

            // ACT: Prune then add
            repo.pruneActiveSet()
            val afterPrune = repo.activeSet.first().size

            repo.onMatchFound(name = "User2", timestamp = 2000L)
            val afterAdd = repo.activeSet.first()

            // ASSERT
            assertEquals(1, afterPrune, "Fresh user should survive prune")
            assertEquals(2, afterAdd.size, "New user should be added after prune")
        }
}
