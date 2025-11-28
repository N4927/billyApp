import binascii
from rest_framework import serializers

"""
Proximity Data Serialization Module.

This module defines the Data Transfer Objects (DTOs) for the proximity domain.
It handles the validation of incoming hex-encoded identifiers and the formatting
of outgoing user resolution data.

Architecture Note:
    - These serializers act as the "Anti-Corruption Layer" for incoming data.
    - They ensure that only valid, sanitized data reaches the Domain Layer.
"""

# The expected length of the hex string for a 128-bit AES block.
# 128 bits = 16 bytes. Hex encoding uses 2 characters per byte -> 32 chars.
B_ID_HEX_LENGTH: int = 32


class ResolutionRequestSerializer(serializers.Serializer):
    """
    Validates the payload for the B_ID resolution endpoint.

    Business Rule:
        - The B_ID must be a strictly formatted 128-bit Hexadecimal string.
        - This corresponds to the AES-256 block size used by the Crypto Engine.
    """

    b_id = serializers.CharField(
        min_length=B_ID_HEX_LENGTH,
        max_length=B_ID_HEX_LENGTH,
        help_text="128-bit Hexadecimal string representing the scanned B_ID (e.g., 'a1b2...').",
        required=True,
    )

    def validate_b_id(self, value: str) -> bytes:
        """
        Validates that the input string is a valid hexadecimal representation
        and converts it into raw bytes for the crypto engine.

        :param value: [str] The hex string input from the client.
        :return: [bytes] The raw 16-byte string ready for AES decryption.
        :raises ValidationError: If the string contains non-hex characters.
        """
        try:
            # Attempt to convert hex string to binary data.
            # binascii is faster and stricter than int(val, 16).
            return binascii.unhexlify(value)
        except (binascii.Error, ValueError):
            raise serializers.ValidationError(
                "Invalid Hex format. String must contain only 0-9 and a-f characters."
            )


class ResolutionResponseSerializer(serializers.Serializer):
    """
    Formats the response for a successful B_ID resolution.
    This serves as the contract for the API Documentation (Swagger).

    Privacy Note:
        - We intentionally DO NOT return the contact timestamp or location data.
        - Only the public display name is revealed to the scanner.
    """

    display_name = serializers.CharField(
        help_text="The public display name of the identified user."
    )
