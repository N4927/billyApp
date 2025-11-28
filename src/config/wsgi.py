"""
WSGI Configuration (Web Server Gateway Interface).

This module exposes the WSGI callable as a module-level variable named ``application``.
It serves as the standard entry point for synchronous web servers (e.g., Gunicorn, uWSGI)
to interface with the Django application.

For more information on this file, see
https://docs.djangoproject.com/en/5.2/howto/deployment/wsgi/
"""

import os

from django.core.wsgi import get_wsgi_application

# [Configuration] Set the default settings module for the WSGI application.
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")

# Initialize the WSGI application.
application = get_wsgi_application()
