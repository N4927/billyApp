import binascii
from rest_framework import serializers

"""
Proximity Data Serialization Module.

This module defines the Data Transfer Objects (DTOs) for the proximity domain.
It handles the validation of incoming hex-encoded identifiers and the formatting
of outgoing user resolution data.
"""

# The expected length of the hex string for a 128-bit AES block.
# 128 bits = 16 bytes. Hex encoding uses 2 characters per byte -> 32 chars.
B_ID_HEX_LENGTH: int = 32


class ResolutionRequestSerializer(serializers.Serializer):
    """
    Validates the payload for the B_ID resolution endpoint.
    Ensures the incoming identifier matches the cryptographic constraints (128-bit Hex).
    """

    b_id = serializers.CharField(
        min_length=B_ID_HEX_LENGTH,
        max_length=B_ID_HEX_LENGTH,
        help_text="128-bit Hexadecimal string representing the scanned B_ID.",
        required=True,
    )

    def validate_b_id(self, value: str) -> bytes:
        """
        Validates that the input string is a valid hexadecimal representation
        and converts it into raw bytes for the crypto engine.

        :param value: [str] The hex string input from the client.
        :return: [bytes] The raw 16-byte string.
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
    """

    display_name = serializers.CharField(
        help_text="The public display name of the identified user."
    )

    contact_timestamp = serializers.IntegerField(
        help_text="Unix timestamp representing the exact time slot when the contact occurred.",
        required=False,
    )
