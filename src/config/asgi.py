"""
ASGI Configuration (Asynchronous Server Gateway Interface).

This module exposes the ASGI callable as a module-level variable named ``application``.
It serves as the entry point for asynchronous web servers (e.g., Daphne, Uvicorn)
to interface with the Django application, enabling support for WebSockets and async views.

For more information on this file, see
https://docs.djangoproject.com/en/5.2/howto/deployment/asgi/
"""

import os

from django.core.asgi import get_asgi_application

# [Configuration] Set the default settings module for the ASGI application.
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")

# Initialize the ASGI application.
application = get_asgi_application()
