import pytest
from django.utils import timezone
from crypto.models import MasterKey
from crypto.tasks import rotate_keys_task
from datetime import timedelta


@pytest.mark.integration
@pytest.mark.django_db
def test_rotation_task_creates_key_if_buffer_low(create_master_key):
    # 1. Crea una chiave che sta per scadere (es. finisce tra 1 ora)
    # Nota: La logica del task guarda l'ultima chiave.
    now = timezone.now()
    # Creiamo una chiave che è iniziata 71 ore fa, quindi scade tra 1 ora.
    # La soglia è 48 ore, quindi DEVE scattare la rotazione.
    past_start = now - timedelta(hours=71)
    key = create_master_key(start_time=past_start)

    assert MasterKey.objects.count() == 1

    # 2. Esegui il task (sincronamente)
    rotate_keys_task()

    # 3. Verifica
    assert MasterKey.objects.count() == 2
    latest_key = (
        MasterKey.objects.first()
    )  # first() è la più nuova per l'ordinamento META

    # La nuova chiave deve iniziare quando finisce la vecchia
    assert latest_key.start_time == key.end_time


@pytest.mark.integration
@pytest.mark.django_db
def test_rotation_task_does_nothing_if_buffer_full(create_master_key):
    # 1. Crea una chiave nuova di zecca (scade tra 72 ore)
    # La soglia è 48 ore. 72 > 48, quindi NON deve scattare.
    create_master_key(start_time=timezone.now())

    assert MasterKey.objects.count() == 1

    # 2. Esegui task
    rotate_keys_task()

    # 3. Verifica
    assert MasterKey.objects.count() == 1
