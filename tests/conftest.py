import pytest
import os
import time
from datetime import timedelta
from django.utils import timezone
from rest_framework.test import APIClient
from django.contrib.auth import get_user_model

from crypto.models import MasterKey
from crypto.engine import BLECryptoEngine, SLOT_DURATION_SECONDS

User = get_user_model()


@pytest.fixture
def api_client():
    return APIClient()


@pytest.fixture
def create_user():
    def _make_user(
        email="test@billyapp.com", username="testuser", password="password123"
    ):
        user = User.objects.create_user(
            username=username, email=email, password=password
        )
        return user

    return _make_user


@pytest.fixture
def create_master_key():
    def _make_key(key_bytes=None, start_time=None):
        if key_bytes is None:
            key_bytes = os.urandom(32)

        # FIX ULTRA-SICURO:
        # Creiamo la chiave valida da IERI.
        # Questo copre qualsiasi problema di fuso orario (UTC vs Local) o ritardo nel test.
        if start_time is None:
            start_time = timezone.now() - timedelta(hours=24)

        # La chiave dura 72h, quindi se inizia 24h fa, è validissima ora (e per altre 48h).
        end_time = start_time + timedelta(hours=72)
        grace_end = end_time + timedelta(hours=72 / 5)

        return MasterKey.objects.create(
            key_bytes=key_bytes,
            start_time=start_time,
            end_time=end_time,
            grace_period_end=grace_end,
        )

    return _make_key


@pytest.fixture
def authenticated_client(api_client, create_user):
    def _auth_client(user=None):
        if user is None:
            user = create_user()
        from rest_framework_simplejwt.tokens import RefreshToken

        refresh = RefreshToken.for_user(user)
        api_client.credentials(HTTP_AUTHORIZATION=f"Bearer {refresh.access_token}")
        return api_client, user

    return _auth_client


@pytest.fixture
def crypto_helper():
    class Helper:
        @staticmethod
        def encrypt_b_id(u_code, slot, key_bytes):
            engine = BLECryptoEngine(key_bytes)
            return engine.encrypt_b_id(u_code, slot)

        @staticmethod
        def get_current_slot():
            return int(time.time() // SLOT_DURATION_SECONDS)

    return Helper
