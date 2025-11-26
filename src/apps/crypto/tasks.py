import logging
import typing
from celery import shared_task
from django.utils import timezone
from .services import KeyManager
from .models import MasterKey

"""
Asynchronous Crypto Tasks.

This module contains Celery tasks responsible for the automated maintenance
of the cryptographic infrastructure. These tasks are intended to be scheduled
via Celery Beat.
"""

# Initialize logger for this module.
logger = logging.getLogger(__name__)

# Security Policy:
# We want to ensure a new key is generated at least 48 hours before the current one expires.
# This provides a safety buffer for system maintenance or outages.
SAFE_THRESHOLD_HOURS: int = 48


@shared_task
def rotate_keys_task() -> str:
    """
    Periodic task to ensure future key availability (Key Buffer).

    Logic:
    1. Checks the latest key in the database.
    2. Calculates remaining lifespan.
    3. If lifespan < SAFE_THRESHOLD, triggers generation of the next key.

    :return: [str] Status message describing the action taken.
    """
    logger.info("Starting Key Rotation Check...")

    # Retrieve the latest generated key (future-most key).
    # Relies on MasterKey Meta ordering ['-start_time'].
    latest_key: typing.Optional[MasterKey] = MasterKey.objects.first()

    # Bootstrap scenario: No keys exist.
    if not latest_key:
        logger.warning("System Bootstrap: No keys found. Initializing...")
        KeyManager.generate_initial_key()
        return "Initialized First Key"

    # Check buffer health.
    now = timezone.now()
    time_remaining = latest_key.end_time - now
    time_remaining_sec = time_remaining.total_seconds()

    if time_remaining_sec < (SAFE_THRESHOLD_HOURS * 3600):
        logger.info(
            f"Key Buffer Low. Latest key expires in {time_remaining}. "
            "Triggering rotation."
        )

        # Delegate logic to Service Layer (SRP).
        new_key = KeyManager.generate_next_key(latest_key)

        logger.info(
            f"Rotation Complete. New key ID {new_key.id} valid from {new_key.start_time}"
        )
        return "New Key Generated"

    logger.info("Key Buffer Healthy. No rotation needed.")
    return "Healthy"


@shared_task
def prune_expired_keys_task() -> str:
    """
    Privacy Policy Enforcement Task.

    Permanently deletes keys that have passed their grace period.
    This implements 'Forward Secrecy': even if the database is compromised
    in the future, old encrypted packets cannot be decrypted because the
    keys no longer exist.

    :return: [str] Summary of deleted keys.
    """
    logger.info("Starting Key Pruning...")

    now = timezone.now()

    # Identify keys where the grace period has strictly passed.
    expired_keys = MasterKey.objects.filter(grace_period_end__lt=now)
    count = expired_keys.count()

    if count > 0:
        # Perform hard delete.
        # Note: This is irreversible.
        expired_keys.delete()
        logger.info(
            f"Privacy Enforcement: Securely deleted {count} expired Master Keys."
        )
        return f"Deleted {count} keys"

    return "No keys to prune"
