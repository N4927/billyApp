# ==========================================
# STAGE 1: BUILDER (Build & Dependencies)
# ==========================================
FROM python:3.11-slim-bullseye as builder

# Prevent generation of .pyc files and buffer output
# PYTHONDONTWRITEBYTECODE: Keeps the image clean.
# PYTHONUNBUFFERED: Ensures logs are streamed directly to the container output.
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# Install system dependencies for compilation (gcc, libpq-dev for postgres)
# These are needed only for building wheels, not for running the app.
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Update pip and install Poetry
RUN pip install --upgrade pip && pip install poetry

# Configure Poetry to create the venv inside the project folder
# This makes it easier to copy the environment to the next stage.
RUN poetry config virtualenvs.in-project true

# Copy dependency definition files
COPY pyproject.toml poetry.lock ./

# Install ONLY production dependencies (no dev tools in the final container)
# --only main: Excludes dev dependencies like pytest, black, etc.
RUN poetry install --no-interaction --no-ansi --no-root --only main

# ==========================================
# STAGE 2: RUNNER (Lightweight Runtime)
# ==========================================
FROM python:3.11-slim-bullseye as runner

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    # Add the virtualenv to the system PATH
    PATH="/app/.venv/bin:$PATH" \
    PYTHONPATH="/app/src"

WORKDIR /app

# Install only necessary runtime libraries (libpq5 for postgres driver)
# No compilers (gcc) or poetry here -> Security and lightweight image.
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq5 \
    && rm -rf /var/lib/apt/lists/*

# Create a non-root user for security (Corporate Best Practice)
# Running as root is a security risk; if the container is compromised, the host might be too.
RUN groupadd -g 999 appuser && \
    useradd -r -u 999 -g appuser appuser

# Copy the Virtual Environment from the Builder stage
COPY --from=builder /app/.venv /app/.venv

# Copy the source code
COPY src/ src/

# Create folders for static and media files and assign permissions to the user
RUN mkdir -p /app/src/staticfiles && \
    mkdir -p /app/src/media && \
    chown -R appuser:appuser /app

# Switch to the limited user
USER appuser

# Expose the port
EXPOSE 8000

# Default startup command (Production ready)
# Note: ensure_keys is included for auto-healing (bootstrapping crypto keys).
CMD ["sh", "-c", "python src/manage.py collectstatic --noinput && python src/manage.py migrate && python src/manage.py ensure_keys && gunicorn config.wsgi:application --bind 0.0.0.0:8000 --workers 4 --threads 4 --chdir src"]
