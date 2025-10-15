# BillyApp API

[![Build](https://img.shields.io/badge/build-Dockerized-blue)](#)
[![Tests](https://img.shields.io/badge/tests-pytest%20%2B%20coverage-brightgreen)](#)
[![Stack](https://img.shields.io/badge/stack-Django%20%7C%20DRF%20%7C%20Postgres%20%7C%20Gunicorn%20%7C%20NGINX-lightgrey)](#)

Django/DRF API to register users, list users, and resolve a **deterministic cryptographic “match”** (AES-ECB) from `username`, `id_hex`, and `timestamp`. Designed for BillyApp.

---

## 🔧 Stack & Ports

| Env  | Service  | Host → Container Port                           | Notes                          |
| ---- | -------- | ----------------------------------------------- | ------------------------------ |
| Dev  | Postgres | 5432 → 5432                                     | Volume `pgdata-dev`            |
| Dev  | Django   | `${DJANGO_SERVER_PORT}` → same (typically 8000) | Bind mount `.:/app`            |
| Prod | NGINX    | 80 → 80                                         | Reverse proxy for static + API |
| Prod | API      | (internal) → 8000                               | Gunicorn; volume `staticfiles` |
| Prod | Postgres | (internal) → 5432                               | Volume `pgdata-prod`           |

**Base URLs**

* Dev: `http://localhost:8000/api/`
* Prod (default NGINX): `http://localhost/api/`

---

## ⚡ Quick Start

1. Create **.env.dev** and **.env.prod** (fill real values—no placeholders).

<details>
<summary><strong>.env.dev</strong></summary>

```
DJANGO_SETTINGS_MODULE=
DEBUG=
DJANGO_SECRET_KEY=
ALLOWED_HOSTS=
CSRF_TRUSTED_ORIGINS=
CORS_ALLOW_ALL_ORIGINS=
CORS_ALLOWED_ORIGINS=
LANGUAGE_CODE=
TIME_ZONE=
LOG_LEVEL=
DB_NAME=
DB_USER=
DB_PASSWORD=
DB_HOST=
DB_PORT=
RID_ROTATION_SECONDS=
DJANGO_SERVER_PORT=
APP_ENV=
RUN_TESTS_ON_START=
AUTO_MAKEMIGRATIONS=
GUNICORN_BIND=
GUNICORN_WORKERS=
GUNICORN_TIMEOUT=
SECURE_SSL_REDIRECT=
SECURE_HSTS_SECONDS=
CORS_ALLOW_CREDENTIALS=
```

</details>

<details>
<summary><strong>.env.prod</strong></summary>

```
DJANGO_SETTINGS_MODULE=
DEBUG=
DJANGO_SECRET_KEY=
ALLOWED_HOSTS=
CSRF_TRUSTED_ORIGINS=
CORS_ALLOW_ALL_ORIGINS=
CORS_ALLOWED_ORIGINS=
LANGUAGE_CODE=
TIME_ZONE=
LOG_LEVEL=
DB_NAME=
DB_USER=
DB_PASSWORD=
DB_HOST=
DB_PORT=
RID_ROTATION_SECONDS=
APP_ENV=
RUN_TESTS_ON_START=
AUTO_MAKEMIGRATIONS=
GUNICORN_BIND=
GUNICORN_WORKERS=
GUNICORN_TIMEOUT=
SECURE_SSL_REDIRECT=
SECURE_HSTS_SECONDS=
CORS_ALLOW_CREDENTIALS=
```

</details>

2. Bring services up:

```bash
make dev-up     # dev environment (hot reload)
make prod-up    # production stack (NGINX + Gunicorn)
```

3. Run tests:

```bash
make dev-test
make prod-test  # uses --no-cache to avoid stale code
```

---

## 🛠 Make Commands

```bash
make dev-test
make dev-up
make dev-down

make prod-test
make prod-up
make prod-down

make ps         # show running services (dev+prod)
make logs       # tail the 'api' service logs (auto-detect dev/prod)
make cleanup    # full cleanup (down -v, remove images, prune)
```

---

## 📡 API Endpoints

**Base URL (see above)**

### 1) `POST /register/`

Registers a user. If `id_hex` is missing, a 16-hex value is generated.

```bash
curl -i -X POST http://localhost:8000/api/register/ \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","display_name":"Alice","id_hex":"1a2b3c4d5e6f7788"}'
```

### 2) `GET /users/list/`

Lists users.

```bash
curl -i http://localhost:8000/api/users/list/
```

### 3) `POST /match/`

Requires `timestamp` (int) and `cipher8_hex` (16 hex).

**Helper** to compute `cipher8_hex` with the project’s libs inside the container:

```bash
TS=1738888800
USERNAME="alice"
IDHEX="1a2b3c4d5e6f7788"

C8=$(
  docker compose --env-file .env.dev -f docker-compose.dev.yml run --rm api \
    python - <<'PY'
from api.crypto import compute_cipher8
print(compute_cipher8("alice","1a2b3c4d5e6f7788",1738888800).hex())
PY
)

curl -i -X POST http://localhost:8000/api/match/ \
  -H "Content-Type: application/json" \
  -d "{\"timestamp\": ${TS}, \"cipher8_hex\": \"${C8}\"}"
```

> In prod, use `http://localhost/api/` (or your host). If you enable `SECURE_SSL_REDIRECT=True` without real TLS, HTTP clients will get a 301 redirect.

---

## 🔐 Deterministic Crypto

* **Key**: `username.lower().encode()` zero-padded to 16 bytes (`[:16]`).
* **Plaintext (16B)**: `id_hex` (8B big-endian) + `time_window` (8B big-endian), where `time_window = timestamp // RID_ROTATION_SECONDS`.
* **Cipher**: AES-ECB. Take the **first 8 bytes** of the encrypted block → `cipher8_hex` (16 hex).
* Same `(username, id_hex, timestamp)` ⇒ same output.

---

## 🗃 Database & Migrations

**Postgres 16** with model:

* `EncounterUser(id, username[unique], display_name, id_hex[unique], created_at)`

On startup:

* Wait for DB → optional `makemigrations` (`AUTO_MAKEMIGRATIONS=1`) → `migrate`
* Prod adds: `collectstatic` + `check --deploy`

---

## ✅ Tests & Coverage

* `pytest`, `pytest-django`, `pytest-cov`
* Artifacts inside container:

  * HTML: `coverage/htmlcov/index.html`
  * XML:  `coverage/coverage.xml`
  * JUnit: `coverage/junit.xml`

```bash
make dev-test
make prod-test
```

---

## 🧭 System Design

### Dev

```mermaid
flowchart LR
  A[Browser\nhttp://localhost:8000/api/] -->|HTTP| B[Django runserver\n(api container)]
  B -->|SQL| C[(Postgres 16\nDB container)]
  B <-->|Bind mount| D[Source code\n.:/app]
  B --> E[EntryPoint\ncheck→(makemigrations?)→migrate]
```

### Prod

```mermaid
flowchart LR
  A[Client\nhttp://localhost/api/] -->|HTTP :80| N[NGINX]
  N -->|/static/*| S[(staticfiles volume)]
  N -->|/api/* proxy_pass| G[Gunicorn\n(api:8000)]
  G -->|SQL| P[(Postgres 16\nDB container)]
  G --> E[EntryPoint\ncheck→migrate→collectstatic→check --deploy]
```

---

## 🧰 Troubleshooting

* **301 during prod-test**: with `SECURE_SSL_REDIRECT=True` and no TLS in front, HTTP requests are redirected. Tests handle this by sending a “secure” request.

* **Stale code**: `make prod-test` forces `--no-cache`. For a hard reset:

  ```bash
  make cleanup && make prod-test
  ```

* **Missing envs**: the entrypoint requires mandatory env vars; complete `.env.*` before starting services.

---

## 📜 License

MIT — `LICENSE.txt`.
