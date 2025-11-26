# BillyApp Backend API

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Coverage](https://img.shields.io/badge/coverage-100%25-brightgreen)
![Python](https://img.shields.io/badge/python-3.11-blue)
![Code Style](https://img.shields.io/badge/code%20style-black-000000.svg)
![Docker](https://img.shields.io/badge/docker-ready-blue)

## 📖 Overview

**BillyApp Backend** is an Enterprise-Grade, privacy-preserving API designed to support high-volume Bluetooth Low Energy (BLE) proximity tracing.

It implements a **Rolling Proximity Identifier** system based on AES-256 encryption, ensuring that user identities are never broadcasted in cleartext. The architecture is designed for massive scalability, utilizing a **Hot/Cold storage strategy** (Redis/PostgreSQL) and asynchronous background processing.

### Key Features
* **Zero-Trust Architecture:** Stateless Authentication via JWT (JSON Web Tokens).
* **Privacy by Design:** AES-256 payload encryption with time-window validation.
* **Forward Secrecy:** Automated key rotation and pruning of expired keys.
* **High Performance:** Sub-millisecond batch generation using Redis Write-Through caching.
* **Scalability:** Containerized (Docker), Stateless API, Asynchronous Workers (Celery).

---

## 🏗 Architecture

The codebase follows a **Modular Monolith** approach using Domain Driven Design (DDD). The logic is split into decoupled applications within `src/apps/`.

### Domain Modules
| Module | Responsibility |
| :--- | :--- |
| **`authentication`** | Identity management. Handles Registration (Email/Password) and JWT issuance. |
| **`crypto`** | Cryptographic core. Manages AES-256 engine, Master Key lifecycle, and Rotation Policies. |
| **`users`** | User Profile management. Handles $U_{code}$ generation and Redis synchronization (Signals). |
| **`proximity`** | Business Logic. Orchestrates `crypto` and `users` to provide Batch Download and Contact Resolution. |

### Data Flow
1.  **Write Path (Registration):** Postgres Transaction -> Django Signal -> **Redis Cache Update**.
2.  **Read Path (Batch Download):** Client -> API -> **Redis Lookup (<1ms)** -> AES Encryption (CPU) -> Response.
3.  **Read Path (Resolution):** Client -> API -> AES Decryption -> **DB Lookup (Index Scan)** -> Response.

---

## 🚀 Getting Started

### Prerequisites
* **Docker** & **Docker Compose** (v2.0+)

### Quick Start (Local Development)

1.  **Clone the repository:**
    ```bash
    git clone <repository_url>
    cd billyApp
    ```

2.  **Build and Start the Stack:**
    This command builds the images, applies migrations, and ensures encryption keys exist.
    ```bash
    docker-compose build
    docker-compose up
    ```

3.  **Create an Admin User:**
    ```bash
    docker-compose run --rm backend python src/manage.py createsuperuser
    ```

4.  **Access the System:**
    * **API Documentation (Swagger):** [http://localhost:8000/api/docs/](http://localhost:8000/api/docs/)
    * **Admin Panel:** [http://localhost:8000/admin/](http://localhost:8000/admin/)

---

## 🛠️ Operational Commands

Since we are using Docker Compose, you can run all operations using native docker commands.

| Action | Command | Description |
| :--- | :--- | :--- |
| **Build** | `docker-compose build` | Rebuilds Docker images (run this after adding dependencies in pyproject.toml). |
| **Start** | `docker-compose up` | Starts the full stack (API, DB, Redis, Workers). Add `-d` to run in background. |
| **Stop & Reset** | `docker-compose down -v` | Stops containers and **removes volumes** (Nuclear Reset). Use this to wipe the DB. |
| **Migrate DB** | `docker-compose run --rm backend python src/manage.py migrate` | Applies pending database migrations. |
| **Make Migrations** | `docker-compose run --rm backend python src/manage.py makemigrations` | Generates new migration files based on model changes. |
| **Run Tests** | `docker-compose run --rm backend pytest` | Runs the full Test Suite (Unit + Integration). |
| **Create Admin** | `docker-compose run --rm backend python src/manage.py createsuperuser` | Creates a dashboard admin user. |
| **Shell** | `docker-compose run --rm backend python src/manage.py shell` | Opens a Python shell context within the backend container. |
| **Manual Init** | `docker-compose run --rm backend python src/manage.py ensure_keys` | Manually generates Master Keys if the system didn't auto-initialize. |

---

## 🧪 Testing & Quality Assurance

Our CI/CD pipeline enforces strict quality gates. Code coverage must remain above 80%.

### Running Tests
To run the full suite with `pytest`:
```bash
docker-compose run --rm backend pytest
````

### Code Coverage Report

To generate an HTML coverage report to identify untested lines:

```bash
docker-compose run --rm backend pytest --cov=src --cov-report=html
```

*The report will be available in the `htmlcov/index.html` directory.*

### Load Testing

We use **Locust** to simulate high-concurrency traffic.

1.  Start the stack in production mode (`DEBUG=0` in `.env`).
2.  Run:
    ```bash
    poetry run locust
    ```
3.  Open [http://localhost:8089](https://www.google.com/search?q=http://localhost:8089) and simulate 1000+ users.

-----

## 🔐 Security & Cryptography

### Master Key Lifecycle

The system uses **Master Keys** stored in the database to derive daily batches.

  * **Rotation:** A Celery Beat task (`rotate-keys-every-hour`) checks if the current key is expiring within 48h. If so, it generates a new future key.
  * **Pruning:** A daily task (`prune-keys-daily`) permanently deletes keys that have passed their grace period, enforcing **Forward Secrecy**.

### Manual Key Initialization

If the database is wiped, the API will return `503 Service Unavailable`. To fix this manually:

```bash
docker-compose run --rm backend python src/manage.py ensure_keys
```

-----

## 📂 Project Structure

```text
.
├── .github/workflows/   # CI/CD Pipelines (GitHub Actions)
├── deploy/              # NGINX & Production Configs
├── docker-compose.yml   # Local Orchestration
├── Dockerfile           # Multi-stage Production Build
├── src/                 # Application Source Code
│   ├── apps/            # Domain Modules
│   │   ├── authentication/  # JWT & Login Logic
│   │   ├── crypto/          # AES Engine & Key Rotation
│   │   ├── proximity/       # Batches & Resolution APIs
│   │   └── users/           # Profile & Signals
│   ├── config/          # Project Settings (Django)
│   └── manage.py        # Django Entry Point
└── tests/               # Mirror of src/ for Testing
    ├── apps/
    └── conftest.py      # Global Test Fixtures
```

-----

## 🌍 Environment Variables (`.env`)

| Variable | Description | Default (Dev) |
| :--- | :--- | :--- |
| `DEBUG` | Toggle debug mode. Set to `0` in Prod. | `1` |
| `SECRET_KEY` | Django cryptographic signing key. | `insecure-dev-key` |
| `DATABASE_URL` | Postgres connection string. | `postgres://...` |
| `REDIS_URL` | Redis connection string. | `redis://redis:6379/1` |
| `DJANGO_ALLOWED_HOSTS`| Comma-separated hostnames. | `localhost,127.0.0.1` |
| `THROTTLE_ANON` | Rate limit for unauthenticated requests. | `10/minute` |
| `THROTTLE_USER` | Rate limit for authenticated users. | `1000/hour` |
| `THROTTLE_BATCHES`| Rate limit for batch downloads. | `5/hour` |
| `THROTTLE_RESOLVE`| Rate limit for contact resolution. | `100/minute` |

-----

## 📦 Deployment

The project is designed for **Containerized Deployment** (AWS ECS / Kubernetes).

1.  **Build:** The CI pipeline builds the Docker image via `Dockerfile` (Multi-stage).
2.  **Push:** Image is pushed to GitHub Container Registry (GHCR).
3.  **Run:** Orchestrator pulls the image and runs it with the production `.env`.

**Static Files:** Handled efficiently by **WhiteNoise** inside the container. No external NGINX configuration required for static assets.

-----

### License

Proprietary - BillyApp Inc.
