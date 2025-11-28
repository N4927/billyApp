import pytest
from django.utils import timezone
from crypto.models import MasterKey
from crypto.tasks import rotate_keys_task
from datetime import timedelta


@pytest.mark.integration
@pytest.mark.django_db
def test_rotation_task_creates_key_if_buffer_low(create_master_key):
    # 1. Create a key that is about to expire (e.g., ends in 1 hour)
    # Note: The task logic looks at the latest key.
    now = timezone.now()
    # We create a key that started 71 hours ago, so it expires in 1 hour.
    # The threshold is 48 hours, so rotation MUST trigger.
    past_start = now - timedelta(hours=71)
    key = create_master_key(start_time=past_start)

    assert MasterKey.objects.count() == 1

    # 2. Execute the task (synchronously)
    rotate_keys_task()

    # 3. Verify
    assert MasterKey.objects.count() == 2
    latest_key = MasterKey.objects.first()  # first() is the newest due to META ordering

    # The new key must start exactly when the old one ends
    assert latest_key.start_time == key.end_time


@pytest.mark.integration
@pytest.mark.django_db
def test_rotation_task_does_nothing_if_buffer_full(create_master_key):
    # 1. Create a brand new key (expires in 72 hours)
    # The threshold is 48 hours. 72 > 48, so it MUST NOT trigger.
    create_master_key(start_time=timezone.now())

    assert MasterKey.objects.count() == 1

    # 2. Execute task
    rotate_keys_task()

    # 3. Verify
    assert MasterKey.objects.count() == 1
