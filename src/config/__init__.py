"""
Config Package Initialization.

This module ensures that the Celery application is loaded when Django starts.
This is essential for the @shared_task decorator to work correctly.
"""

from .celery import app as celery_app

# Expose the Celery application to the package level.
__all__ = ("celery_app",)
