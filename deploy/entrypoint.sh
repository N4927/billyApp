#!/usr/bin/env bash
set -euo pipefail

req() { : "${!1:?Missing required env: $1}"; }

req APP_ENV
req DJANGO_SETTINGS_MODULE
req RUN_TESTS_ON_START
req AUTO_MAKEMIGRATIONS

echo "APP_ENV=${APP_ENV}  |  DJANGO_SETTINGS_MODULE=${DJANGO_SETTINGS_MODULE}"

ATTEMPTS=60
until python - <<'PY'
from django.core.management import execute_from_command_line as e
from django.db import connections
e(['manage.py','check'])
connections['default'].cursor()
print("DB OK")
PY
do
  ATTEMPTS=$((ATTEMPTS-1))
  if [ $ATTEMPTS -le 0 ]; then
    echo "Database not ready." >&2
    exit 1
  fi
  sleep 1
done

if [ "${AUTO_MAKEMIGRATIONS}" = "1" ]; then
  python manage.py makemigrations --noinput
fi

python manage.py migrate --noinput

if [ "${APP_ENV}" = "prod" ]; then
  python manage.py collectstatic --noinput
  python manage.py check --deploy
fi

if [ "$#" -gt 0 ]; then
  exec "$@"
fi

if [ "${RUN_TESTS_ON_START}" = "1" ]; then
  mkdir -p /app/coverage/htmlcov
  pytest
fi

if [ "${APP_ENV}" = "dev" ]; then
  req DJANGO_SERVER_PORT
  exec python manage.py runserver localhost:${DJANGO_SERVER_PORT}
fi

req GUNICORN_BIND
req GUNICORN_WORKERS
req GUNICORN_TIMEOUT
exec gunicorn core.wsgi:application \
  --bind "${GUNICORN_BIND}" \
  --workers "${GUNICORN_WORKERS}" \
  --timeout "${GUNICORN_TIMEOUT}" \
  --access-logfile - \
  --error-logfile -
