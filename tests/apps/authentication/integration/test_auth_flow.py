import pytest
from django.urls import reverse
from rest_framework import status
from users.models import AppUser


@pytest.mark.integration
@pytest.mark.django_db
def test_registration_normalization(api_client):
    """
    Verifies that user registration inputs are normalized for consistency.

    Business Rule:
        - Usernames and emails must be stored in a canonical format (lowercase)
        - This prevents duplicate accounts due to case sensitivity issues (e.g., 'User' vs 'user').

    Steps:
        1. Submit registration data with mixed-case strings.
        2. Assert successful creation (HTTP 201).
        3. Query the database to confirm the stored data is lowercased.
    """
    url = reverse("auth_register")
    data = {
        "username": "MixedCaseUser",
        "email": "TEST@Example.com",
        "password": "StrongPassword123!",
    }

    response = api_client.post(url, data)
    assert response.status_code == status.HTTP_201_CREATED

    # Verify lowercase storage in the database.
    # Note: We query by the expected lowercase username to prove the transformation happened.
    profile = AppUser.objects.get(user__username="mixedcaseuser")
    assert profile.user.email == "test@example.com"


@pytest.mark.integration
@pytest.mark.django_db
def test_login_with_email(api_client, create_user):
    """
    Verifies the authentication flow using Email instead of Username.

    Business Rule:
        - Users should be able to log in using either their username or email address.
        - This improves user experience (UX) as emails are easier to remember.

    Steps:
        1. Create a test user with known credentials.
        2. Attempt login using the email address as the identifier.
        3. Assert successful authentication (HTTP 200) and token issuance.
    """
    # Create user (fixture uses standard create_user factory)
    user = create_user(email="login@test.com", password="password123")

    url = reverse("token_obtain_pair")
    data = {"email": "login@test.com", "password": "password123"}  # Login via Email

    response = api_client.post(url, data)
    assert response.status_code == status.HTTP_200_OK

    # Validate that a JWT access token is returned.
    assert "access" in response.data
    # Confirm the token belongs to the correct user.
    assert response.data["username"] == user.username
