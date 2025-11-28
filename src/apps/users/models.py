from django.db import models
from django.conf import settings

"""
User Domain Model Definition.

Implements the "Profile" pattern to extend the standard Django User model (Auth)
without directly inheriting from it (Composition Over Inheritance - COI).
This model manages the data specific to the BLE/Proximity domain.
"""


class AppUser(models.Model):
    """
    Represents the extended user profile for Proximity functionalities.
    Maintains a 1:1 relationship with the authentication user and guards the
    unique secret identifier (U_code).
    """

    # Link 1:1 with the authentication user (Identity).
    # Deleting the Auth user triggers a cascading deletion of the profile.
    user = models.OneToOneField(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="ble_profile"
    )

    # Unique 64-bit identifier used for B_id generation.
    # db_index=True is fundamental to ensure O(log N) lookups during resolution.
    u_code = models.BigIntegerField(
        unique=True,
        db_index=True,
        help_text="64-bit Unique Identifier used for BLE seed generation",
    )

    # Creation timestamp for audit and debugging purposes.
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = "App User Profile"
        verbose_name_plural = "App User Profiles"

    def __str__(self) -> str:
        """
        Returns a string representation of the object.

        :return: The username of the associated user.
        """
        # Accessing self.user might trigger a DB query if not selected_related.
        # In admin panels, usually handled by the queryset manager.
        return f"{self.user.username}"
