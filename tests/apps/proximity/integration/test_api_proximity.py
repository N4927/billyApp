import pytest
from django.urls import reverse
from rest_framework import status
from django.utils import timezone
from datetime import timedelta


@pytest.mark.integration
@pytest.mark.django_db
class TestProximityAPIs:

    def test_download_batch(self, authenticated_client, create_master_key):
        """Testa che un utente loggato possa scaricare i 144 codici"""
        client, user = authenticated_client()

        # FIX: Creiamo la chiave esplicitamente nel passato per evitare race condition
        # Se il test runner e il DB hanno clock leggermente diversi, questo risolve tutto.
        past_time = timezone.now() - timedelta(minutes=5)
        create_master_key(start_time=past_time)

        url = reverse("download_batches")
        response = client.get(url)

        # Debug in caso di fallimento (stampiamo il dettaglio errore)
        if response.status_code != 200:
            print(f"API Error Detail: {response.data}")

        assert response.status_code == status.HTTP_200_OK
        assert len(response.data["b_ids"]) == 144
        assert "start_slot" in response.data

    def test_resolve_id_success(
        self, authenticated_client, create_master_key, crypto_helper
    ):
        """Testa la risoluzione di un ID sniffato"""
        # 1. Setup: Chi cerca (Scanner) e Chi viene cercato (Target)
        scanner_client, scanner_user = authenticated_client()

        # Creiamo un Target User (che non è lo scanner)
        from django.contrib.auth import get_user_model

        User = get_user_model()
        target_user = User.objects.create_user("target_user", "t@t.com", "password")

        # 2. Creiamo la chiave (validità sicura)
        past_time = timezone.now() - timedelta(minutes=5)
        master_key = create_master_key(start_time=past_time)

        slot = crypto_helper.get_current_slot()

        # Recuperiamo l'U_code dal profilo
        target_ucode = target_user.ble_profile.u_code
        b_id_hex = crypto_helper.encrypt_b_id(target_ucode, slot, master_key.key_bytes)

        # 3. Lo scanner invia l'ID al backend
        url = reverse("resolve_b_id")
        response = scanner_client.post(url, {"b_id": b_id_hex}, format="json")

        # 4. Assert
        assert response.status_code == status.HTTP_200_OK
        assert response.data["display_name"] == "target_user"

    def test_resolve_fails_no_keys(self, authenticated_client):
        """Testa errore 503 se non ci sono chiavi master"""
        # NOTA: Qui NON chiamiamo create_master_key, quindi il DB chiavi è vuoto.
        client, _ = authenticated_client()
        url = reverse("resolve_b_id")
        response = client.post(url, {"b_id": "a" * 32}, format="json")
        assert response.status_code == status.HTTP_503_SERVICE_UNAVAILABLE
