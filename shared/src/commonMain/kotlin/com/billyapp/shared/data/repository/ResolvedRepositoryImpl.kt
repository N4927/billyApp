package com.billyapp.shared.data.repository

import com.billyapp.shared.domain.model.ResolvedUser
import com.billyapp.shared.domain.repository.ResolvedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock

/**
 * In-Memory implementation of the application state.
 *
 * Responsibilities:
 * 1. Holds the "Active Set" (A) of users currently visible in the radar.
 * 2. Applies Time-To-Live (TTL) logic to remove stale users.
 *
 * ARCHITECTURE NOTE:
 * This repository does not persist data to disk. The UI state is transient and
 * designed to be ephemeral. If the app is killed, the radar resets.
 */
class ResolvedRepositoryImpl : ResolvedRepository {
    // Reactive state source of truth for the UI
    // Backed by a MutableStateFlow for thread-safe updates.
    private val _activeSet = MutableStateFlow<List<ResolvedUser>>(emptyList())

    /**
     * Exposes the current list of visible users as a read-only stream.
     * The UI layer should observe this flow to update the radar view.
     */
    override val activeSet: StateFlow<List<ResolvedUser>> = _activeSet.asStateFlow()

    private companion object {
        // Tau_vis = 200 seconds.
        // Users are removed from the radar if not seen for > 3 minutes approx.
        // This constant defines the "Persistence of Vision" for the radar.
        const val VISIBILITY_TTL_SECONDS = 200L
    }

    /**
     * Updates the active set with a new or existing match.
     *
     * Logic: Upsert based on Name.
     * - If user exists: Update their `lastSeen` timestamp (keep them alive).
     * - If user is new: Add them to the set.
     *
     * @param name The display name of the resolved user.
     * @param timestamp The timestamp from the backend (currently unused in favor of local time).
     */
    override fun onMatchFound(
        name: String,
        timestamp: Long,
    ) {
        // We use System time for UI TTL tracking to ensure consistency with the local device clock
        // regardless of server time drift.
        val now = Clock.System.now()

        _activeSet.update { currentSet ->
            val existingUser = currentSet.find { it.name == name }

            if (existingUser != null) {
                // Update: Refresh the 'lastSeen' timestamp to keep them on screen
                // We map over the list to replace the immutable item.
                val updatedUser = existingUser.copy(lastSeen = now)
                currentSet.map { if (it.name == name) updatedUser else it }
            } else {
                // Insert: New contact discovered
                currentSet + ResolvedUser(name, now)
            }
        }
    }

    /**
     * Removes users who have exceeded the visibility TTL.
     *
     * This should be called periodically by the Core orchestration (e.g., after every sync)
     * to ensure the list doesn't grow indefinitely with stale data.
     */
    override fun pruneActiveSet() {
        val now = Clock.System.now()

        _activeSet.update { currentSet ->
            // Filter: Keep only users seen within the last VISIBILITY_TTL_SECONDS
            currentSet.filter { user ->
                (now - user.lastSeen).inWholeSeconds < VISIBILITY_TTL_SECONDS
            }
        }
    }
}
