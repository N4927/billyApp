import typing
from django.core.cache import cache

from users.models import AppUser
from crypto.services import KeyManager
from crypto.engine import BLECryptoEngine, SLOT_DURATION_SECONDS

"""
Proximity Service Layer.

This module encapsulates the business logic for the Proximity domain.
It acts as the orchestrator between the Persistence Layer (DB/Redis) and
the Cryptographic Layer, ensuring controllers (Views) remain thin and focused
on HTTP concerns (SRP).
"""

# Constant defining the number of slots in a 24-hour period (24h * 6 slots/h).
# Extracted to avoid magic numbers (DRY/OCP).
# 144 slots * 10 minutes = 1440 minutes = 24 hours.
DAILY_BATCH_SIZE: int = 144


class BatchService:
    """
    Service responsible for managing the generation and retrieval of
    Rolling Proximity Identifiers (B_IDs).
    """

    @staticmethod
    def get_user_ucode(user_id: int) -> typing.Optional[int]:
        """
        Retrieves the user's unique 64-bit seed code (U_code).

        Implements the 'Cache-Aside' (or Lazy Loading) pattern:
        1. Check Hot Storage (Redis) for sub-millisecond access.
        2. If miss, fetch from Cold Storage (Postgres).
        3. Populate Cache for future requests.

        This strategy minimizes database load during high-concurrency scenarios.

        :param user_id: [int] The primary key of the authenticated user.
        :return: [Optional[int]] The 64-bit U_code or None if the profile is missing.
        """
        # Construct the namespaced cache key (DRY: Key format should match signals.py)
        # Namespacing prevents key collisions in a shared Redis instance.
        key: str = f"ucode:user:{user_id}"

        # 1. Fast Path: Redis Lookup
        u_code: typing.Optional[int] = cache.get(key)

        if u_code is None:
            # 2. Slow Path: Database Lookup
            try:
                u_code = AppUser.objects.get(user_id=user_id).u_code
                # 3. Cache Population (Write-Through logic is handled by signals, this is a failsafe)
                # Timeout is set to None to persist indefinitely until invalidation.
                # This assumes that U_code is immutable or rarely changed.
                cache.set(key, u_code, timeout=None)
            except AppUser.DoesNotExist:
                return None

        return u_code

    @staticmethod
    def generate_daily_batch(u_code: int) -> typing.Dict[str, typing.Any]:
        """
        Generates the sequence of encrypted identifiers for the current time window.

        Orchestrates the crypto engine to produce 144 keys starting from the current
        time slot. This operation is CPU-intensive.

        :param u_code: [int] The user's secret 64-bit seed.
        :return: [Dict] A dictionary containing metadata (start_slot) and the list of B_IDs.
        :raises Exception: If no active Master Keys are available in the system.
        """
        # Retrieve active keys from the Crypto Domain Service
        keys = KeyManager.get_candidate_keys()
        if not keys:
            # Critical failure: Without keys, the system cannot function.
            raise Exception("System Error: No active Master Keys found for encryption.")

        # Use the most recent key (Current Key) for generating new batches
        # The first key in the queryset is the one with the most recent start_time.
        current_key = keys.first()
        engine = BLECryptoEngine(current_key.key_bytes)

        # Calculate the anchor point in time
        # This ensures the batch starts from "now" relative to the server's clock.
        start_slot: int = engine.get_current_slot()

        # Generate the batch using List Comprehension.
        # Performance Note: List comprehensions are faster than for-loops with .append()
        # in Python due to optimized bytecode generation.
        batch: typing.List[str] = [
            engine.encrypt_b_id(u_code, start_slot + i) for i in range(DAILY_BATCH_SIZE)
        ]

        return {
            "start_slot": start_slot,
            "slot_duration": SLOT_DURATION_SECONDS,
            "b_ids": batch,
        }
