from django.db import models

"""
Cryptographic Data Models.

This module defines the persistence layer for the cryptographic keys.
It ensures that keys are stored with appropriate metadata to manage their lifecycle
(Rotation, Expiration, Grace Period).
"""

# AES-256 requires a 256-bit key, which equals 32 bytes.
# This constant enforces the security requirement at the database level.
AES_KEY_SIZE_BYTES: int = 32


class MasterKey(models.Model):
    """
    Represents an AES-256 Master Key used for deriving Temporary Exposure Keys (TEKs).

    Lifecycle:
    1. Active: Used for encryption (start_time <= now < end_time).
    2. Grace Period: Used only for decryption (end_time <= now < grace_period_end).
    3. Expired: Pruned by background tasks (now >= grace_period_end).
    """

    # Stores the raw binary key.
    # BinaryField is used to avoid encoding issues with raw bytes.
    key_bytes = models.BinaryField(
        max_length=AES_KEY_SIZE_BYTES, help_text="Raw 32-byte binary data for AES-256."
    )

    # Indexed for fast O(1) retrieval during resolution logic.
    # start_time marks the beginning of the key's active usage.
    start_time = models.DateTimeField(db_index=True)
    # end_time marks when the key stops being used for new encryptions.
    end_time = models.DateTimeField()

    # Defines when the key is hard-deleted from the system.
    # The grace period allows for late-arriving packets to still be decrypted.
    grace_period_end = models.DateTimeField(db_index=True)

    # Audit timestamp for when the record was created.
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        # Default ordering ensures .first() returns the most future-dated key.
        # This is critical for the 'generate_next_key' logic.
        ordering = ["-start_time"]
        verbose_name = "Master Key"
        verbose_name_plural = "Master Keys"

    def __str__(self) -> str:
        """
        Returns a string representation for logging and admin interfaces.

        :return: [str] Human-readable summary of the key's active period.
        """
        return f"Key active from {self.start_time} to {self.end_time}"
