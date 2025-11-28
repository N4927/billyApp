import pytest
from django.urls import reverse
from rest_framework import status
from django.utils import timezone
from datetime import timedelta


@pytest.mark.integration
@pytest.mark.django_db
class TestProximityAPIs:

    def test_download_batch(self, authenticated_client, create_master_key):
        """Tests that a logged-in user can download the 144 codes."""
        client, user = authenticated_client()

        # FIX: Explicitly create the key in the past to avoid race conditions.
        # If the test runner and DB have slightly different clocks, this solves it.
        past_time = timezone.now() - timedelta(minutes=5)
        create_master_key(start_time=past_time)

        url = reverse("download_batches")
        response = client.get(url)

        # Debug in case of failure (print error detail)
        if response.status_code != 200:
            print(f"API Error Detail: {response.data}")

        assert response.status_code == status.HTTP_200_OK
        assert len(response.data["b_ids"]) == 144
        assert "start_slot" in response.data

    def test_resolve_id_success(
        self, authenticated_client, create_master_key, crypto_helper
    ):
        """Tests the resolution of a sniffed ID."""
        # 1. Setup: Who searches (Scanner) and Who is searched (Target)
        scanner_client, scanner_user = authenticated_client()

        # Create a Target User (who is not the scanner)
        from django.contrib.auth import get_user_model

        User = get_user_model()
        target_user = User.objects.create_user("target_user", "t@t.com", "password")

        # 2. Create the key (safe validity)
        past_time = timezone.now() - timedelta(minutes=5)
        master_key = create_master_key(start_time=past_time)

        slot = crypto_helper.get_current_slot()

        # Retrieve U_code from profile
        target_ucode = target_user.ble_profile.u_code
        b_id_hex = crypto_helper.encrypt_b_id(target_ucode, slot, master_key.key_bytes)

        # 3. The scanner sends the ID to the backend
        url = reverse("resolve_b_id")
        response = scanner_client.post(url, {"b_id": b_id_hex}, format="json")

        # 4. Assert
        assert response.status_code == status.HTTP_200_OK
        assert response.data["display_name"] == "target_user"

    def test_resolve_fails_no_keys(self, authenticated_client):
        """Tests error 503 if there are no master keys."""
        # NOTE: We do NOT call create_master_key here, so the key DB is empty.
        client, _ = authenticated_client()
        url = reverse("resolve_b_id")
        response = client.post(url, {"b_id": "a" * 32}, format="json")
        assert response.status_code == status.HTTP_503_SERVICE_UNAVAILABLE
