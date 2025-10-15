from typing import Tuple
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

from django.conf import settings as core_settings


def _to_u64_bytes(value: int) -> bytes:
    return value.to_bytes(8, byteorder="big", signed=False)


def derive_key_from_username(username: str) -> bytes:
    key = username.lower().encode("utf-8")
    if len(key) < 16:
        key = key + b"\x00" * (16 - len(key))
    return key[:16]


def build_plaintext(id_hex: str, timestamp: int) -> Tuple[bytes, int]:
    time_window = timestamp // core_settings.RID_ROTATION_SECONDS
    id_bytes = int(id_hex, 16).to_bytes(8, byteorder="big")
    window_bytes = _to_u64_bytes(time_window)
    return id_bytes + window_bytes, time_window


def compute_cipher8(username: str, id_hex: str, timestamp: int) -> bytes:
    key = derive_key_from_username(username)
    plaintext, _ = build_plaintext(id_hex, timestamp)

    if len(key) != 16:
        raise ValueError()

    if len(plaintext) != 16:
        raise ValueError()

    cipher = Cipher(algorithms.AES(key), modes.ECB())
    encryptor = cipher.encryptor()
    enc = encryptor.update(plaintext) + encryptor.finalize()
    return enc[:8]
