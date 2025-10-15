from __future__ import annotations
import os
from pathlib import Path
from typing import Any, Final


def require_env(name: str) -> str:
    v = os.getenv(name)
    if v is None or v == "":
        raise RuntimeError(f"Missing required environment variable: {name}")
    return v


def require_bool(name: str) -> bool:
    v = require_env(name).lower()
    if v not in {"true", "false", "1", "0", "yes", "no", "on", "off"}:
        raise RuntimeError(f"Invalid boolean for {name}: {v}")
    return v in {"true", "1", "yes", "on"}


def require_int(name: str) -> int:
    v = require_env(name)
    try:
        return int(v)
    except ValueError:
        raise RuntimeError(f"Invalid int for {name}: {v}")


def require_list(name: str, allow_empty: bool = False) -> list[str]:
    raw = os.getenv(name)
    if raw is None:
        if allow_empty:
            return []
        raise RuntimeError(f"Missing required environment variable: {name}")
    if raw.strip() == "":
        return [] if allow_empty else []
    return [item.strip() for item in raw.split(",") if item.strip()]


BASE_DIR = Path(__file__).resolve().parent.parent

SECRET_KEY = require_env("DJANGO_SECRET_KEY")
DEBUG = require_bool("DEBUG")

ALLOWED_HOSTS = require_list("ALLOWED_HOSTS")
CSRF_TRUSTED_ORIGINS = require_list("CSRF_TRUSTED_ORIGINS", allow_empty=True)

INSTALLED_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "rest_framework",
    "corsheaders",
    "api",
]

MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "corsheaders.middleware.CorsMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "core.urls"

TEMPLATES: list[dict[str, Any]] = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ]
        },
    }
]

WSGI_APPLICATION = "core.wsgi.application"

DATABASES: dict[str, dict[str, Any]] = {
    "default": {
        "ENGINE": "django.db.backends.postgresql",
        "NAME": require_env("DB_NAME"),
        "USER": require_env("DB_USER"),
        "PASSWORD": require_env("DB_PASSWORD"),
        "HOST": require_env("DB_HOST"),
        "PORT": require_env("DB_PORT"),
    }
}

AUTH_PASSWORD_VALIDATORS = [
    {
        "NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"
    },
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator"},
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]

LANGUAGE_CODE = require_env("LANGUAGE_CODE")
TIME_ZONE = require_env("TIME_ZONE")
USE_I18N = True
USE_TZ = True

STATIC_URL = "/static/"
STATICFILES_DIRS: list[str] = []
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

CORS_ALLOW_ALL_ORIGINS = require_bool("CORS_ALLOW_ALL_ORIGINS")
CORS_ALLOWED_ORIGINS = require_list("CORS_ALLOWED_ORIGINS", allow_empty=True)
CORS_ALLOW_CREDENTIALS = True

REST_FRAMEWORK = {
    "DEFAULT_RENDERER_CLASSES": [
        "rest_framework.renderers.JSONRenderer",
        "rest_framework.renderers.BrowsableAPIRenderer",
    ],
    "DEFAULT_PARSER_CLASSES": [
        "rest_framework.parsers.JSONParser",
        "rest_framework.parsers.FormParser",
        "rest_framework.parsers.MultiPartParser",
    ],
}

RID_ROTATION_SECONDS: Final[int] = require_int("RID_ROTATION_SECONDS")

LOG_LEVEL = require_env("LOG_LEVEL")
LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "handlers": {"console": {"class": "logging.StreamHandler"}},
    "root": {"handlers": ["console"], "level": LOG_LEVEL},
}
