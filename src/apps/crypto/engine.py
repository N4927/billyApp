import struct
import time
import binascii
import typing
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend

"""
Core Cryptographic Engine.

This module implements the low-level encryption and decryption logic using AES-256.
It follows strict specifications for payload structure and time-window validation.
"""

# ==============================================================================
# CONFIGURATION CONSTANTS
# ==============================================================================

# AES-256 Key Size (32 bytes)
# Standard requirement for AES-256 security level.
KEY_SIZE: int = 32

# AES Block Size (128 bits = 16 bytes)
# Fixed block size for the AES algorithm.
BLOCK_SIZE: int = 16

# Time Slot Configuration
# Duration of a single proximity rolling identifier validity.
# 600 seconds = 10 Minutes.
# Chosen to balance privacy (frequent rotation) with server load (key generation).
SLOT_DURATION_SECONDS: int = 600

# Tolerance ratio for clock drift and network latency.
# Delta = 600 / 5 = 120 seconds (2 minutes) before and after the slot.
# Allows for minor time discrepancies between client and server clocks.
TOLERANCE_RATIO: int = 5
DELTA_SECONDS: int = SLOT_DURATION_SECONDS // TOLERANCE_RATIO

# Struct Format for Payload: Big-Endian (>), Unsigned Long Long (Q), Unsigned Long Long (Q)
# Total: 8 bytes (U_code) + 8 bytes (Slot) = 16 bytes.
# Big-Endian is used for network byte order consistency across different architectures.
PAYLOAD_STRUCT_FMT: str = ">QQ"

# PostgreSQL BigInt is signed 64-bit. Python 'Q' struct is unsigned 64-bit.
# We need to handle the overflow if the random U_code exceeds 2^63-1.
# This ensures compatibility with the database schema which uses signed integers.
MAX_INT64: int = 2**63 - 1
UINT64_MODULO: int = 2**64


class BLECryptoEngine:
    """
    Encapsulates AES-256-ECB operations for Rolling Proximity Identifiers.

    Security Note:
    ECB mode is generally discouraged for large data, but acceptable here because:
    1. The payload is exactly one block (16 bytes).
    2. The data contains a high-entropy U_code and a changing time slot, ensuring uniqueness.
    """

    def __init__(self, key_bytes: bytes):
        """
        Initializes the cipher context with a specific Master Key.

        :param key_bytes: [bytes] The 32-byte AES key. Must be exactly 32 bytes.
        :raises ValueError: If the key length is incorrect.
        """
        if len(key_bytes) != KEY_SIZE:
            raise ValueError(f"AES-256 key must be {KEY_SIZE} bytes.")

        # Using default_backend() is standard practice for cryptography library.
        self.cipher = Cipher(
            algorithms.AES(key_bytes), modes.ECB(), backend=default_backend()
        )

    def encrypt_b_id(self, u_code: int, time_slot: int) -> str:
        """
        Generates an encrypted B_ID (Broadcast ID) for a specific time slot.

        Used by the Batch Service to generate future keys for the client.
        The output is a hex string suitable for JSON transmission.

        :param u_code: [int] The user's unique 64-bit seed.
        :param time_slot: [int] The sequential time slot index.
        :return: [str] Hexadecimal string (32 chars) representing the encrypted packet.
        """
        # 1. Pack data into binary format (16 bytes)
        # Ensures the data structure matches the decryption expectation.
        payload = struct.pack(PAYLOAD_STRUCT_FMT, u_code, time_slot)

        # 2. Encrypt using AES-ECB
        encryptor = self.cipher.encryptor()
        # Since payload is exactly block size, update + finalize works seamlessly.
        # No padding is required as the payload is exactly 16 bytes.
        encrypted_bytes = encryptor.update(payload) + encryptor.finalize()

        # 3. Return as Hex String
        # Decoded to utf-8 to return a standard string object.
        return binascii.hexlify(encrypted_bytes).decode("utf-8")

    def decrypt_b_id(
        self, b_id_bytes: bytes
    ) -> typing.Optional[typing.Tuple[int, int]]:
        """
        Attempts to decrypt a B_ID received from a client.

        This method handles the decryption and unpacking of the binary payload.
        It also manages the conversion from unsigned 64-bit integers (Python/Struct)
        to signed 64-bit integers (PostgreSQL).

        :param b_id_bytes: [bytes] The raw binary encrypted packet (16 bytes).
        :return: [Optional[Tuple[int, int]]] A tuple (u_code, time_slot) if successful, else None.
        """
        # Fail fast if the input length is not exactly one block.
        if len(b_id_bytes) != BLOCK_SIZE:
            return None

        try:
            decryptor = self.cipher.decryptor()
            decrypted_data = decryptor.update(b_id_bytes) + decryptor.finalize()

            # Unpack the binary data according to the defined format.
            u_code, time_slot = struct.unpack(PAYLOAD_STRUCT_FMT, decrypted_data)

            # Handle Signed/Unsigned conversion for DB compatibility.
            # If the unsigned value is larger than max signed 64-bit int,
            # wrap it around to the negative range.
            # This is necessary because PostgreSQL's BigInt is signed.
            if u_code > MAX_INT64:
                u_code = u_code - UINT64_MODULO

            return u_code, time_slot

        except Exception:
            # Security Best Practice: Catch-all exception (Padding error, Value error).
            # We return None silently to prevent Padding Oracle attacks.
            # Logging the error here might be useful for debugging but risky for security logs.
            return None

    @staticmethod
    def is_slot_valid(target_slot: int, unix_time: int = None) -> bool:
        """
        Validates if a decrypted time slot is acceptable given the current time.

        Logic:
        t_start - delta <= t_now <= t_end + delta
        This accounts for clock drift and network latency.

        :param target_slot: [int] The slot index decrypted from the packet.
        :param unix_time: [int] The current timestamp (defaults to now).
        :return: [bool] True if the packet is within the validity window.
        """
        if unix_time is None:
            unix_time = int(time.time())

        # Calculate the absolute start and end times of the slot.
        t_start = target_slot * SLOT_DURATION_SECONDS
        t_end = (target_slot + 1) * SLOT_DURATION_SECONDS

        # Apply the tolerance delta to create the validity window.
        lower_bound = t_start - DELTA_SECONDS
        upper_bound = t_end + DELTA_SECONDS

        return lower_bound <= unix_time <= upper_bound

    @staticmethod
    def get_current_slot(unix_time: int = None) -> int:
        """
        Calculates the current time slot index based on the epoch.

        This is used to determine which key or slot should be active right now.

        :param unix_time: [int] Current timestamp.
        :return: [int] The integer slot index.
        """
        if unix_time is None:
            unix_time = int(time.time())
        # Integer division to get the floor of the slot index.
        return int(unix_time // SLOT_DURATION_SECONDS)
