import os
import sys
import typing
from datetime import timedelta
from pathlib import Path
import environ

"""
Main Project Configuration (Corporate Grade).

This module defines the execution environment, connections to external services (DB, Redis),
security pipeline, and installed application configuration.
It follows the 12-Factor App principles by using environment variables for configuration.
"""

# Initialize environment variables
env = environ.Env()
# [Security] Load .env file only in local/dev environments.
# In production, env vars should be injected by the container orchestrator (K8s/Docker).
environ.Env.read_env(os.path.join(Path(__file__).resolve().parent.parent, ".env"))

# ==============================================================================
# CORE PATHS & PROJECT SETUP
# ==============================================================================

# Base directory of the project.
BASE_DIR: Path = Path(__file__).resolve().parent.parent

# [Modular Structure] Add the 'src/apps' directory to the system path.
# This allows importing apps directly (e.g., 'from core.models' instead of 'from src.apps.core.models').
sys.path.insert(0, os.path.join(BASE_DIR, "apps"))

# ==============================================================================
# SECURITY SETTINGS
# ==============================================================================

# [Security] The secret key is critical for cryptographic signing.
# Never hardcode this in production.
SECRET_KEY: str = env(
    "SECRET_KEY",
    default="django-insecure-d5q3qy1iw#=jcoxw#qz@*687(k3vvi7syy*i$-pt3iy$39y&-y",
)

# [Security] Never enable DEBUG in production to avoid leaking stack traces and env vars.
DEBUG: bool = env.bool("DEBUG", default=True)

# Hosts allowed to serve the application. Essential to prevent Host Header attacks.
ALLOWED_HOSTS: typing.List[str] = env.list(
    "DJANGO_ALLOWED_HOSTS", default=["localhost", "127.0.0.1"]
)

# ==============================================================================
# APPLICATION DEFINITION
# ==============================================================================

# Built-in Django applications.
DJANGO_APPS: typing.List[str] = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
]

# External libraries and dependencies.
THIRD_PARTY_APPS: typing.List[str] = [
    "rest_framework",
    "rest_framework_simplejwt",
    "drf_spectacular",
]

# Internal domain-specific applications (Micro-modules).
LOCAL_APPS: typing.List[str] = [
    "authentication",  # Handles JWT, Login, Registration
    "users",  # Handles User Profile, Signals, DB Sync
    "crypto",  # Handles Encryption Engine, Key Rotation
    "proximity",  # Business Logic (Batch, Resolve)
]

# Combined list of installed apps.
INSTALLED_APPS: typing.List[str] = DJANGO_APPS + THIRD_PARTY_APPS + LOCAL_APPS

MIDDLEWARE: typing.List[str] = [
    "django.middleware.security.SecurityMiddleware",
    # [Performance] Whitenoise serves static files efficiently directly from Gunicorn.
    "whitenoise.middleware.WhiteNoiseMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF: str = "config.urls"

TEMPLATES: typing.List[typing.Dict] = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

WSGI_APPLICATION: str = "config.wsgi.application"

# ==============================================================================
# DATABASE & STORAGE
# ==============================================================================

# Database connection configuration.
# Uses 'django-environ' to parse the DATABASE_URL.
DATABASES: typing.Dict[str, typing.Dict] = {
    "default": env.db("DATABASE_URL", default="sqlite:///db.sqlite3")
}

# Default primary key field type for models.
DEFAULT_AUTO_FIELD: str = "django.db.models.BigAutoField"

# ==============================================================================
# PASSWORD VALIDATION
# ==============================================================================

AUTH_PASSWORD_VALIDATORS: typing.List[typing.Dict] = [
    {
        "NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"
    },
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator"},
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]

# ==============================================================================
# INTERNATIONALIZATION
# ==============================================================================

LANGUAGE_CODE: str = "en-us"
TIME_ZONE: str = "UTC"
USE_I18N: bool = True
USE_TZ: bool = True

# ==============================================================================
# STATIC FILES (CSS, JavaScript, Images)
# ==============================================================================

STATIC_URL: str = "static/"
# Location where collectstatic will output files for production.
STATIC_ROOT: str = os.path.join(BASE_DIR, "staticfiles")
# [Performance] Enables unique filenames (hashing) for long-term caching headers.
STATICFILES_STORAGE: str = "whitenoise.storage.CompressedManifestStaticFilesStorage"

# ==============================================================================
# API & AUTHENTICATION (DRF + JWT)
# ==============================================================================

REST_FRAMEWORK: typing.Dict[str, typing.Any] = {
    "DEFAULT_AUTHENTICATION_CLASSES": (
        "rest_framework_simplejwt.authentication.JWTAuthentication",
    ),
    "DEFAULT_PERMISSION_CLASSES": ("rest_framework.permissions.IsAuthenticated",),
    # Integration with Drf-Spectacular for OpenAPI generation.
    "DEFAULT_SCHEMA_CLASS": "drf_spectacular.openapi.AutoSchema",
    # [Security] Rate Limiting/Throttling configuration.
    "DEFAULT_THROTTLE_CLASSES": [
        "rest_framework.throttling.UserRateThrottle",
        "rest_framework.throttling.AnonRateThrottle",
        "rest_framework.throttling.ScopedRateThrottle",
    ],
    "DEFAULT_THROTTLE_RATES": {
        "anon": env("THROTTLE_ANON", default="10/minute"),
        "user": env("THROTTLE_USER", default="1000/hour"),
        "batches": env("THROTTLE_BATCHES", default="5/hour"),
        "resolve": env("THROTTLE_RESOLVE", default="100/minute"),
    },
}

SIMPLE_JWT: typing.Dict[str, typing.Any] = {
    "ACCESS_TOKEN_LIFETIME": timedelta(minutes=60),  # Short-lived for security
    "REFRESH_TOKEN_LIFETIME": timedelta(days=1),
    "ROTATE_REFRESH_TOKENS": True,  # Improves security by changing refresh token on use
    "BLACKLIST_AFTER_ROTATION": True,
    "AUTH_HEADER_TYPES": ("Bearer",),
    "USER_ID_FIELD": "id",
    "USER_ID_CLAIM": "user_id",
}

SPECTACULAR_SETTINGS: typing.Dict[str, typing.Any] = {
    "TITLE": "BillyApp API",
    "DESCRIPTION": "API Backend for BillyApp (Corporate Grade)",
    "VERSION": "1.0.0",
    "SERVE_INCLUDE_SCHEMA": False,
    "COMPONENT_SPLIT_REQUEST": True,
}

# ==============================================================================
# PERFORMANCE & ASYNC LAYER (Redis + Celery)
# ==============================================================================

# Single source of truth for Redis connection.
REDIS_URL: str = env("REDIS_URL", default="redis://redis:6379/1")

# Caching Configuration (Hot Storage)
CACHES: typing.Dict[str, typing.Dict] = {
    "default": {
        "BACKEND": "django_redis.cache.RedisCache",
        "LOCATION": REDIS_URL,
        "OPTIONS": {
            "CLIENT_CLASS": "django_redis.client.DefaultClient",
            "SOCKET_CONNECT_TIMEOUT": 5,
            "SOCKET_TIMEOUT": 5,
        },
    }
}

# Celery Configuration (Task Queue)
CELERY_BROKER_URL: str = REDIS_URL
CELERY_RESULT_BACKEND: str = REDIS_URL
CELERY_ACCEPT_CONTENT: typing.List[str] = ["json"]
CELERY_TASK_SERIALIZER: str = "json"
CELERY_RESULT_SERIALIZER: str = "json"
CELERY_TIMEZONE: str = TIME_ZONE

# Celery Beat Schedule (Periodic Tasks)
# Defines the heartbeat of the application automation.
CELERY_BEAT_SCHEDULE: typing.Dict[str, typing.Dict] = {
    # [Crypto] Ensures key availability buffer is always full.
    "rotate-keys-every-hour": {
        "task": "crypto.tasks.rotate_keys_task",
        "schedule": 3600.0,
    },
    # [Privacy] Hard delete expired keys to ensure forward secrecy.
    "prune-keys-daily": {
        "task": "crypto.tasks.prune_expired_keys_task",
        "schedule": 86400.0,
    },
}
