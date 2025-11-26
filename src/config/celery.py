import os
from celery import Celery

"""
Celery Application Configuration Module.

This module initializes the distributed task queue application.
It acts as the entry point for all asynchronous background tasks (workers) and
periodic task scheduling (beat).

Architecture:
    Django -> Redis (Broker) -> Celery Workers
"""

# Sets the default Django settings module for the 'celery' program.
# This ensures that Celery can access the Django environment (models, ORM, settings)
# without needing to run through the manage.py command.
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")


def create_celery_app() -> Celery:
    """
    Factory function to instantiate and configure the Celery application.

    Follows the SRP (Single Responsibility Principle) by isolating the creation logic.

    :return: Configured [Celery] application instance.
    """
    # Creates the Celery application instance named 'billy_backend'.
    # This name is used to identify tasks in the broker.
    celery_app = Celery("billy_backend")

    # Configures Celery using the settings defined in the Django settings file.
    # The namespace='CELERY' means all celery-related settings in settings.py
    # must start with 'CELERY_' (e.g., CELERY_BROKER_URL).
    # Follows DRY: Configuration lives in one place (settings.py).
    celery_app.config_from_object("django.conf:settings", namespace="CELERY")

    # Automatically discovers tasks.py files in all installed Django apps.
    # This allows plug-and-play functionality for new modules (e.g., crypto, proximity)
    # without manual registration.
    celery_app.autodiscover_tasks()

    return celery_app


# Exposes the Celery app instance.
# This object is imported by the __init__.py of the config package to ensure
# the app is loaded when Django starts.
app: Celery = create_celery_app()
