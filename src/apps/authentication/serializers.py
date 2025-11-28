import typing
from rest_framework import serializers
from django.contrib.auth import get_user_model
from django.contrib.auth.models import AbstractBaseUser
from rest_framework.validators import UniqueValidator
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer
from rest_framework_simplejwt.exceptions import InvalidToken, AuthenticationFailed

"""
Authentication Serialization Layer.

This module defines the Data Transfer Objects (DTOs) for auth operations.
It enforces data integrity (uniqueness), security policies (password length),
and sanitization (lowercase normalization) before data touches the database.
"""

# Type alias for the User model to satisfy type checkers
UserType = get_user_model()

# Configuration constants
# 8 characters is the minimum recommended by NIST for basic password security.
MIN_PASSWORD_LENGTH: int = 8
# 3 characters allows for short but meaningful usernames (e.g., 'tom').
USERNAME_MIN_LENGTH: int = 3
# 30 characters limits database storage and UI rendering issues.
USERNAME_MAX_LENGTH: int = 30


class RegisterSerializer(serializers.ModelSerializer):
    """
    Validates and processes registration payloads.
    Enforces unique Email and Username constraints.
    """

    email = serializers.EmailField(
        required=True,
        validators=[
            UniqueValidator(
                queryset=UserType.objects.all(), message="Email already exists"
            )
        ],
    )

    username = serializers.CharField(
        required=True,
        min_length=USERNAME_MIN_LENGTH,
        max_length=USERNAME_MAX_LENGTH,
        validators=[
            UniqueValidator(
                queryset=UserType.objects.all(), message="Username already exists"
            )
        ],
    )

    # Write-only to ensure the hashed password is never returned in the API response.
    # This prevents accidental leakage of sensitive credentials.
    password = serializers.CharField(write_only=True, min_length=MIN_PASSWORD_LENGTH)

    class Meta:
        model = UserType
        fields = ("username", "email", "password")

    def validate_email(self, value: str) -> str:
        """
        Normalizes the email address to lowercase.

        This ensures that 'User@Example.com' and 'user@example.com' are treated
        as the same identity, preventing duplicate accounts.

        :param value: [str] The raw email input.
        :return: [str] Sanitized email.
        """
        return value.lower()

    def validate_username(self, value: str) -> str:
        """
        Normalizes the username and enforces alphanumeric constraints.

        :param value: [str] The raw username input.
        :return: [str] Sanitized username.
        :raises ValidationError: If invalid characters are detected.
        """
        normalized_value = value.lower()
        if not normalized_value.isalnum() and "_" not in normalized_value:
            raise serializers.ValidationError(
                "Username can only contain letters, numbers and underscores."
            )
        return normalized_value

    def create(self, validated_data: typing.Dict[str, typing.Any]) -> AbstractBaseUser:
        """
        Persists the new user instance.

        Delegates to the User Manager's `create_user` method to ensure
        proper password hashing.

        :param validated_data: [Dict] Cleaned data from the request.
        :return: [AbstractBaseUser] The created User instance.
        """
        return UserType.objects.create_user(
            username=validated_data["username"],
            email=validated_data["email"],
            password=validated_data["password"],
        )


class EmailTokenObtainSerializer(TokenObtainPairSerializer):
    """
    Custom Authentication Serializer.

    Overrides the default behavior to validate credentials using Email
    instead of Username, aligning with modern corporate UX patterns.
    """

    username_field = UserType.EMAIL_FIELD

    def validate(self, attrs: typing.Dict[str, typing.Any]) -> typing.Dict[str, str]:
        """
        Verifies the provided credentials against the database.

        Logic Flow:
        1. Sanitize inputs.
        2. Locate user by email.
        3. Verify password hash.
        4. Check account status (is_active).
        5. Issue tokens.

        :param attrs: [Dict] Raw input dictionary containing 'email' and 'password'.
        :return: [Dict] JSON payload containing access and refresh tokens.
        :raises InvalidToken: If inputs are missing.
        :raises AuthenticationFailed: If credentials are wrong or account is disabled.
        """
        # 1. input Sanitization
        email = attrs.get("email", "").lower()
        password = attrs.get("password")

        if not email or not password:
            raise InvalidToken('Must include "email" and "password".')

        # 2. User Lookup
        # We perform a case-insensitive match implicit via the sanitization above
        user = UserType.objects.filter(email=email).first()

        # 3. Verification (Guard Clauses for cleaner flow)
        if user is None:
            # We use a generic error message to prevent User Enumeration attacks.
            raise AuthenticationFailed(
                "No active account found with the given credentials"
            )

        if not user.check_password(password):
            # We use a generic error message to prevent User Enumeration attacks.
            raise AuthenticationFailed(
                "No active account found with the given credentials"
            )

        if not user.is_active:
            raise AuthenticationFailed("User account is inactive")

        # 4. Token Generation
        # Set the user for the parent class logic
        self.user = user
        refresh = self.get_token(self.user)

        return {
            "refresh": str(refresh),
            "access": str(refresh.access_token),
            # Optional: Useful for client-side UI to display the profile immediately
            "username": user.username,
            "user_id": user.id,
        }
