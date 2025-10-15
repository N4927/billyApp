from __future__ import annotations

from typing import Dict, Any, cast, Tuple, TypedDict, List

from django.test import TestCase, override_settings
from django.db import IntegrityError, transaction
from django.urls import reverse

from rest_framework import status
from rest_framework.test import APITestCase, APIClient
from rest_framework.response import Response as DRFResponse

from api.models import EncounterUser
from api.serializers import EncounterUserSerializer
from api.crypto import (
    _to_u64_bytes,
    derive_key_from_username,
    build_plaintext,
    compute_cipher8,
)
from django.conf import settings as core_settings


class UserOut(TypedDict):
    id: int
    username: str
    display_name: str
    id_hex: str
    created_at: str


class MatchOut(TypedDict):
    user_id: int
    username: str
    display_name: str
    id_hex: str
    matched_window: int


@override_settings(RID_ROTATION_SECONDS=20)
class CryptoUnitTests(TestCase):
    def test__to_u64_bytes_big_endian_and_size(self):
        v = 0x0102030405060708
        out = _to_u64_bytes(v)
        self.assertEqual(len(out), 8)
        self.assertEqual(out, b"\x01\x02\x03\x04\x05\x06\x07\x08")

    def test_derive_key_from_username_zero_pad_and_lowercase(self):
        k = derive_key_from_username("AlIcE")
        self.assertEqual(len(k), 16)
        self.assertEqual(k[:5], b"alice")
        self.assertEqual(k[5:], b"\x00" * 11)

        k2 = derive_key_from_username("x" * 40)
        self.assertEqual(len(k2), 16)
        self.assertEqual(k2, b"x" * 16)

    def test_build_plaintext_layout_and_window(self):
        id_hex = "1a2b3c4d5e6f7788"
        ts = 1738888800
        pt, win = build_plaintext(id_hex, ts)
        self.assertEqual(win, ts // core_settings.RID_ROTATION_SECONDS)
        self.assertEqual(len(pt), 16)
        self.assertEqual(pt[:8], int(id_hex, 16).to_bytes(8, "big"))
        self.assertEqual(pt[8:], (ts // 20).to_bytes(8, "big"))

    def test_compute_cipher8_returns_8_bytes_and_is_deterministic(self):
        username = "alice"
        id_hex = "1a2b3c4d5e6f7788"
        ts = 1738888800
        c1 = compute_cipher8(username, id_hex, ts)
        c2 = compute_cipher8(username, id_hex, ts)
        self.assertEqual(len(c1), 8)
        self.assertEqual(c1, c2)

    def test_compute_cipher8_raises_on_wrong_sizes(self):
        from api import crypto as crypto_mod

        def bad_build_plaintext(_id_hex: str, _ts: int) -> Tuple[bytes, int]:
            return b"\x00" * 15, 0

        orig = crypto_mod.build_plaintext
        crypto_mod.build_plaintext = bad_build_plaintext
        try:
            with self.assertRaises(ValueError):
                compute_cipher8("bob", "0011223344556677", 123)
        finally:
            crypto_mod.build_plaintext = orig


class SerializerTests(TestCase):
    def test_encounter_user_serializer_validates_and_creates(self):
        payload = {
            "username": "alice",
            "display_name": "Alice",
            "id_hex": "1a2b3c4d5e6f7788",
        }
        ser = EncounterUserSerializer(data=payload)
        self.assertTrue(ser.is_valid(), ser.errors)
        obj = cast(EncounterUser, ser.save())
        self.assertEqual(obj.username, "alice")
        self.assertEqual(obj.id_hex, payload["id_hex"])

    def test_encounter_user_serializer_generates_id_hex_when_missing(self):
        payload = {"username": "bob", "display_name": "Bob"}
        ser = EncounterUserSerializer(data=payload)
        self.assertTrue(ser.is_valid(), ser.errors)
        obj = cast(EncounterUser, ser.save())
        self.assertEqual(len(obj.id_hex), 16)
        int(obj.id_hex, 16)

    def test_encounter_user_serializer_rejects_wrong_id_hex_length(self):
        payload = {"username": "carl", "display_name": "Carl", "id_hex": "deadbeef"}
        ser = EncounterUserSerializer(data=payload)
        self.assertFalse(ser.is_valid())
        self.assertIn("id_hex", ser.errors)

    def test_encounter_user_serializer_unique_username(self):
        EncounterUser.objects.create(
            username="dupe", display_name="Dupe", id_hex="0011223344556677"
        )
        dup = EncounterUserSerializer(
            data={
                "username": "dupe",
                "display_name": "Another",
                "id_hex": "8899aabbccddeeff",
            }
        )
        self.assertFalse(dup.is_valid())
        self.assertIn("username", dup.errors)

    def test_unique_enforced_at_db_level_race_fallback(self):
        EncounterUser.objects.create(
            username="race", display_name="R", id_hex="1122334455667788"
        )
        with self.assertRaises(IntegrityError):
            with transaction.atomic():
                EncounterUser.objects.create(
                    username="race", display_name="R2", id_hex="99aabbccddeeff00"
                )


@override_settings(RID_ROTATION_SECONDS=20)
class APITests(APITestCase):
    def setUp(self):
        self.client: APIClient = APIClient()
        # forza HTTPS per evitare redirect 301 con SECURE_SSL_REDIRECT=True (prod)
        self.client.defaults["HTTP_X_FORWARDED_PROTO"] = "https"

        self.user: EncounterUser = cast(
            EncounterUser,
            EncounterUser.objects.create(
                username="alice",
                display_name="Alice",
                id_hex="1a2b3c4d5e6f7788",
            ),
        )

    def _user(self) -> EncounterUser:
        return cast(EncounterUser, EncounterUser.objects.get(pk=self.user.pk))

    def _post_json(self, path: str, data: Dict[str, Any] | None = None) -> DRFResponse:
        payload: Dict[str, Any] = {} if data is None else data
        return cast(
            DRFResponse,
            self.client.post(path, data=payload, format="json", secure=True),
        )

    def _get(self, path: str) -> DRFResponse:
        return cast(DRFResponse, self.client.get(path, secure=True))

    def _make_valid_match_payload(
        self, username: str, id_hex: str, ts: int
    ) -> Dict[str, Any]:
        c8 = compute_cipher8(username, id_hex, ts).hex()
        return {"timestamp": ts, "cipher8_hex": c8}

    def test_register_user_success(self):
        payload = {
            "username": "bob",
            "display_name": "Bob",
            "id_hex": "a1a2a3a4a5a6a7a8",
        }
        resp = self._post_json(reverse("register"), payload)
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED, resp.data)
        data = cast(UserOut, resp.data)
        self.assertEqual(data["username"], "bob")
        self.assertEqual(data["id_hex"], payload["id_hex"])
        self.assertIn("id", data)

    def test_register_user_duplicate_username(self):
        payload = {
            "username": "alice",
            "display_name": "A2",
            "id_hex": "ffffffffffffffff",
        }
        resp = self._post_json(reverse("register"), payload)
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("username", cast(Dict[str, Any], resp.data))

    def test_register_user_generates_id_hex_when_missing(self):
        resp = self._post_json(
            reverse("register"), {"username": "charlie", "display_name": "Charlie"}
        )
        self.assertEqual(resp.status_code, status.HTTP_201_CREATED, resp.data)
        data = cast(UserOut, resp.data)
        self.assertEqual(data["username"], "charlie")
        self.assertEqual(len(data["id_hex"]), 16)
        int(data["id_hex"], 16)

    def test_list_users_returns_all(self):
        EncounterUser.objects.create(
            username="bob", display_name="Bob", id_hex="a1a2a3a4a5a6a7a8"
        )
        resp = self._get(reverse("users-list"))
        self.assertEqual(resp.status_code, status.HTTP_200_OK)
        body = cast(List[UserOut], resp.data)
        self.assertGreaterEqual(len(body), 2)
        self.assertSetEqual(
            set(body[0].keys()),
            {"id", "username", "display_name", "id_hex", "created_at"},
        )

    def test_match_success(self):
        u = self._user()
        ts = 1738888800
        payload = self._make_valid_match_payload(u.username, u.id_hex, ts)
        resp = self._post_json(reverse("match"), payload)
        self.assertEqual(resp.status_code, status.HTTP_200_OK, resp.data)
        data = cast(MatchOut, resp.data)
        self.assertEqual(data["user_id"], u.pk)
        self.assertEqual(data["username"], u.username)
        self.assertEqual(data["id_hex"], u.id_hex)
        self.assertEqual(
            data["matched_window"], ts // core_settings.RID_ROTATION_SECONDS
        )

    def test_match_not_found(self):
        ts = 1738888800
        payload = self._make_valid_match_payload("bob", "a1a2a3a4a5a6a7a8", ts)
        resp = self._post_json(reverse("match"), payload)
        self.assertEqual(resp.status_code, status.HTTP_404_NOT_FOUND)
        self.assertEqual(
            cast(Dict[str, Any], resp.data)["detail"], "Nessuna corrispondenza"
        )

    def test_match_invalid_payload(self):
        resp = self._post_json(
            reverse("match"),
            {"timestamp": 1700000000, "cipher8_hex": "zzzzzzzzzzzzzzzz"},
        )
        self.assertEqual(resp.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("cipher8_hex", cast(Dict[str, Any], resp.data))

        resp2 = self._post_json(reverse("match"), {"cipher8_hex": "aabbccddeeff0011"})
        self.assertEqual(resp2.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn("timestamp", cast(Dict[str, Any], resp2.data))

    def test_match_is_case_insensitive_on_cipher_hex(self):
        u = self._user()
        ts = 1738888800
        c8_upper = compute_cipher8(u.username, u.id_hex, ts).hex().upper()
        resp = self._post_json(
            reverse("match"), {"timestamp": ts, "cipher8_hex": c8_upper}
        )
        self.assertEqual(resp.status_code, status.HTTP_200_OK, resp.data)
        self.assertEqual(cast(MatchOut, resp.data)["user_id"], u.pk)

    def test_urls_exist(self):
        r1 = self._get(reverse("users-list"))
        self.assertIn(r1.status_code, {200, 400, 405})

        r2 = self._post_json(
            reverse("register"),
            {"username": "tmp", "display_name": "Tmp", "id_hex": "0011223344556677"},
        )
        self.assertIn(r2.status_code, {200, 201, 400, 405})

        r3 = self._post_json(
            reverse("match"), {"timestamp": 0, "cipher8_hex": "0011223344556677"}
        )
        self.assertIn(r3.status_code, {200, 400, 404, 405})
