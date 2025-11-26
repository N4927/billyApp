import pytest
from django.core.cache import cache
from django.contrib.auth import get_user_model
from users.models import AppUser

User = get_user_model()


@pytest.mark.integration
@pytest.mark.django_db
def test_user_creation_triggers_profile_and_cache():
    # 1. Crea Auth User
    user = User.objects.create_user(username="auto_test", password="pw")

    # 2. Verifica esistenza Profilo AppUser (generato dal signal)
    assert AppUser.objects.filter(user=user).exists()
    profile = AppUser.objects.get(user=user)
    assert profile.u_code is not None

    # 3. Verifica popolamento Redis (generato dal signal)
    cache_key = f"ucode:user:{user.id}"
    cached_ucode = cache.get(cache_key)
    assert cached_ucode == profile.u_code


@pytest.mark.integration
@pytest.mark.django_db
def test_user_deletion_clears_cache():
    user = User.objects.create_user(username="del_test", password="pw")
    cache_key = f"ucode:user:{user.id}"

    # Assicurati che sia in cache
    assert cache.get(cache_key) is not None

    # Cancella utente
    user.delete()

    # Verifica pulizia cache
    assert cache.get(cache_key) is None
