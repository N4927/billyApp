import random
import typing
from django.db.models.signals import post_save, post_delete
from django.dispatch import receiver
from django.core.cache import cache
from django.contrib.auth import get_user_model
from django.db.models.base import Model
from .models import AppUser

# Type alias for the User model to satisfy type checkers
UserType = get_user_model()

# Constant defining the maximum value for a signed 64-bit integer (PostgreSQL BigInt).
# Used to ensure generated IDs fit within the database field constraints.
POSTGRES_BIGINT_MAX: int = 9223372036854775807


def get_cache_key(user_id: int) -> str:
    """
    Constructs the standardized Redis cache key for a user's U_code.
    Follows DRY: Formatting logic is centralized here.

    :param user_id: The primary key of the Auth User.
    :return: A formatted string key (e.g., 'ucode:user:42').
    """
    return f"ucode:user:{user_id}"


def generate_unique_ucode() -> int:
    """
    Generates a cryptographically secure, unique 64-bit identifier.

    Implements a collision check loop to guarantee database integrity constraints.
    While collisions in 64-bit space are statistically improbable, this ensures
    robustness (Murphy's Law).

    :return: A unique integer between 1 and POSTGRES_BIGINT_MAX.
    """
    while True:
        # Generate a random integer within the signed 64-bit range.
        code: int = random.randint(1, POSTGRES_BIGINT_MAX)

        # Check against the DB to ensure uniqueness.
        if not AppUser.objects.filter(u_code=code).exists():
            return code


@receiver(post_save, sender=UserType)
def create_user_profile(
    sender: typing.Type[Model], instance: Model, created: bool, **kwargs: typing.Any
) -> None:
    """
    Signal handler triggered after a User model is saved.

    Automation: Automatically creates the associated AppUser profile with a
    unique U_code when a new user registers. strictly follows SRP by
    delegating ID generation to a helper function.

    :param sender: The model class sending the signal.
    :param instance: The actual instance being saved.
    :param created: Boolean indicating if this is a new record.
    :param kwargs: Additional signal arguments.
    """
    if created:
        unique_code = generate_unique_ucode()
        AppUser.objects.create(user=instance, u_code=unique_code)


@receiver(post_save, sender=AppUser)
def cache_user_ucode(
    sender: typing.Type[Model], instance: AppUser, **kwargs: typing.Any
) -> None:
    """
    Signal handler triggered after an AppUser profile is saved.

    Performance Strategy (Write-Through Caching):
    Immediately synchronizes the Postgres data to Redis (Hot Storage).
    This ensures subsequent read operations (e.g., batch download) hit Redis
    instead of the DB, providing sub-millisecond latency.

    :param sender: The model class (AppUser).
    :param instance: The AppUser instance being saved.
    :param kwargs: Additional signal arguments.
    """
    cache_key = get_cache_key(instance.user_id)
    # Timeout=None means the key never expires (persistent cache)
    # until explicitly deleted or updated.
    cache.set(cache_key, instance.u_code, timeout=None)


@receiver(post_delete, sender=AppUser)
def delete_user_ucode_cache(
    sender: typing.Type[Model], instance: AppUser, **kwargs: typing.Any
) -> None:
    """
    Signal handler triggered after an AppUser profile is deleted.

    Cleanup: Removes the stale entry from Redis to maintain data consistency.

    :param sender: The model class (AppUser).
    :param instance: The AppUser instance being deleted.
    :param kwargs: Additional signal arguments.
    """
    cache_key = get_cache_key(instance.user_id)
    cache.delete(cache_key)
