#!/usr/bin/env python
"""
Django Management Command Entry Point.

This script is the primary administrative interface for the Django project.
It bootstraps the Python environment, loads the settings configuration,
and executes command-line utilities (e.g., runserver, migrate, test).

Usage:
    python manage.py <command> [options]
"""
import os
import sys


def main():
    """
    Main execution entry point.

    Sets the default Django settings module and delegates control to
    Django's internal command-line utility.
    """
    # [Configuration] Point to the production-ready settings module by default.
    os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")
    try:
        from django.core.management import execute_from_command_line
    except ImportError as exc:
        raise ImportError(
            "Couldn't import Django. Are you sure it's installed and "
            "available on your PYTHONPATH environment variable? Did you "
            "forget to activate a virtual environment?"
        ) from exc

    # Execute the requested management command.
    execute_from_command_line(sys.argv)


if __name__ == "__main__":
    main()
