import pytest
import os
from crypto.engine import BLECryptoEngine


@pytest.mark.unit
def test_engine_encrypt_decrypt_cycle():
    """
    Verifies the symmetric encryption/decryption cycle of the BLE Crypto Engine.

    Invariant:
        - Decrypt(Encrypt(Data)) == Data
        - The engine must correctly handle the transformation between integer inputs
          and the encrypted byte stream.

    Steps:
        1. Initialize the engine with a random 256-bit key.
        2. Encrypt a known User Code and Time Slot.
        3. Decrypt the resulting ciphertext.
        4. Assert that the output matches the original input.
    """
    key = os.urandom(32)
    engine = BLECryptoEngine(key)

    u_code_in = 987654321
    slot_in = 5000

    # 1. Encrypt: Generates a hex-encoded ciphertext.
    b_id_hex = engine.encrypt_b_id(u_code_in, slot_in)

    # 2. Convert hex to bytes for decryption (simulating the receiver side).
    import binascii

    b_id_bytes = binascii.unhexlify(b_id_hex)

    # 3. Decrypt: Extracts the original payload.
    u_code_out, slot_out = engine.decrypt_b_id(b_id_bytes)

    # 4. Verification
    assert u_code_in == u_code_out
    assert slot_in == slot_out


@pytest.mark.unit
def test_slot_validation():
    """
    Verifies the time window validation logic for Time-Based One-Time Passwords (TOTP).

    Business Rule:
        - A token is valid only within a specific time window around its creation.
        - This prevents replay attacks using old tokens.
        - The window allows for slight clock drift between client and server.

    Scenario:
        - Slot Duration: 600s (10 minutes)
        - Target Slot: 1000 (Time = 600,000s)
        - Valid Window: +/- 1 slot (or configured buffer).
    """
    slot = 1000
    # Slot duration 600s. Start 600,000. End 600,600. Delta 120.
    # Valid window: 599,880 -> 600,720

    # Case 1: Valid - Time is exactly in the middle of the slot.
    assert BLECryptoEngine.is_slot_valid(slot, unix_time=600300) is True

    # Case 2: Invalid - Time is before the allowed window (Too Early).
    assert BLECryptoEngine.is_slot_valid(slot, unix_time=599879) is False

    # Case 3: Invalid - Time is after the allowed window (Expired).
    assert BLECryptoEngine.is_slot_valid(slot, unix_time=600721) is False
