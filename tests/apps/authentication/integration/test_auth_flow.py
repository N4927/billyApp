import pytest
from django.urls import reverse
from rest_framework import status
from users.models import AppUser


@pytest.mark.integration
@pytest.mark.django_db
def test_registration_normalization(api_client):
    url = reverse("auth_register")
    data = {
        "username": "MixedCaseUser",
        "email": "TEST@Example.com",
        "password": "StrongPassword123!",
    }

    response = api_client.post(url, data)
    assert response.status_code == status.HTTP_201_CREATED

    # Verifica salvataggio in minuscolo
    profile = AppUser.objects.get(user__username="mixedcaseuser")
    assert profile.user.email == "test@example.com"


@pytest.mark.integration
@pytest.mark.django_db
def test_login_with_email(api_client, create_user):
    # Crea utente (la fixture usa create_user standard)
    user = create_user(email="login@test.com", password="password123")

    url = reverse("token_obtain_pair")
    data = {"email": "login@test.com", "password": "password123"}  # Login via Email

    response = api_client.post(url, data)
    assert response.status_code == status.HTTP_200_OK
    assert "access" in response.data
    assert response.data["username"] == user.username
