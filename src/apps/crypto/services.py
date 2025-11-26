import os
import typing
from datetime import timedelta
from django.utils import timezone
from django.db.models import QuerySet
from .models import MasterKey

"""
Cryptographic Key Management Service.

This module centralizes the lifecycle management of Master Keys.
It acts as the single source of truth for key generation policies,
durations, and retrieval logic.
"""

# Configuration Constants (DRY/OCP)
# How long a key is valid for generating new B_IDs.
KEY_VALIDITY_HOURS: int = 72
# The divisor used to calculate grace period (1/5th of validity).
GRACE_PERIOD_DIVISOR: int = 5


class KeyManager:
    """
    Domain Service for handling MasterKey lifecycle operations.
    """

    @staticmethod
    def get_candidate_keys() -> QuerySet[MasterKey]:
        """
        Retrieves keys that are currently valid for decryption.

        A key is candidate if:
        1. It has started (start_time <= now).
        2. It has not passed its grace period (grace_period_end >= now).

        :return: [QuerySet] List of valid MasterKey objects ordered by most recent.
        """
        now = timezone.now()
        return MasterKey.objects.filter(
            start_time__lte=now, grace_period_end__gte=now
        ).order_by("-start_time")

    @staticmethod
    def _calculate_timings(
        start_dt: typing.Any,
    ) -> typing.Tuple[typing.Any, typing.Any]:
        """
        Internal helper to calculate end_time and grace_period_end based on a start time.

        :param start_dt: [datetime] The starting timestamp for the key.
        :return: [Tuple] (end_time, grace_period_end).
        """
        duration = timedelta(hours=KEY_VALIDITY_HOURS)
        grace = timedelta(hours=KEY_VALIDITY_HOURS / GRACE_PERIOD_DIVISOR)
        return start_dt + duration, start_dt + duration + grace

    @staticmethod
    def generate_initial_key() -> typing.Optional[MasterKey]:
        """
        Bootstraps the system by creating the first Master Key if none exist.

        :return: [MasterKey] The created key, or None if keys already exist.
        """
        if MasterKey.objects.exists():
            return None

        now = timezone.now()
        end_time, grace_end = KeyManager._calculate_timings(now)

        return MasterKey.objects.create(
            key_bytes=os.urandom(32),
            start_time=now,
            end_time=end_time,
            grace_period_end=grace_end,
        )

    @staticmethod
    def generate_next_key(reference_key: MasterKey) -> MasterKey:
        """
        Creates a new Master Key that starts exactly when the reference key ends.

        This ensures continuous coverage without gaps in the timeline.

        :param reference_key: [MasterKey] The current latest key in the chain.
        :return: [MasterKey] The newly created future key.
        """
        new_start = reference_key.end_time
        end_time, grace_end = KeyManager._calculate_timings(new_start)

        return MasterKey.objects.create(
            key_bytes=os.urandom(32),
            start_time=new_start,
            end_time=end_time,
            grace_period_end=grace_end,
        )
