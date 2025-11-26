import pytest
import os
from crypto.engine import BLECryptoEngine


@pytest.mark.unit
def test_engine_encrypt_decrypt_cycle():
    """Verifica che ciò che cifriamo possa essere decifrato"""
    key = os.urandom(32)
    engine = BLECryptoEngine(key)

    u_code_in = 987654321
    slot_in = 5000

    # 1. Encrypt
    b_id_hex = engine.encrypt_b_id(u_code_in, slot_in)

    # 2. Convert hex to bytes for decrypt
    import binascii

    b_id_bytes = binascii.unhexlify(b_id_hex)

    # 3. Decrypt
    u_code_out, slot_out = engine.decrypt_b_id(b_id_bytes)

    assert u_code_in == u_code_out
    assert slot_in == slot_out


@pytest.mark.unit
def test_slot_validation():
    """Verifica la finestra temporale"""
    slot = 1000
    # Slot duration 600s. Start 600,000. End 600,600. Delta 120.
    # Valid: 599,880 -> 600,720

    assert BLECryptoEngine.is_slot_valid(slot, unix_time=600300) is True
    assert BLECryptoEngine.is_slot_valid(slot, unix_time=599879) is False  # Too early
    assert BLECryptoEngine.is_slot_valid(slot, unix_time=600721) is False  # Too late
