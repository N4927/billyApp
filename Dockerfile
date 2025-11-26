# ==========================================
# STAGE 1: BUILDER (Costruzione e Dipendenze)
# ==========================================
FROM python:3.11-slim-bullseye as builder

# Evita la generazione di file .pyc e buffer output
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# Installiamo dipendenze di sistema per compilare (gcc, libpq-dev per postgres)
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# Aggiorniamo pip e installiamo Poetry
RUN pip install --upgrade pip && pip install poetry

# Configuriamo Poetry per creare il venv dentro la cartella del progetto
RUN poetry config virtualenvs.in-project true

# Copiamo i file di definizione delle dipendenze
COPY pyproject.toml poetry.lock ./

# Installiamo SOLO le dipendenze di produzione (niente dev tools nel container finale)
RUN poetry install --no-interaction --no-ansi --no-root --only main

# ==========================================
# STAGE 2: RUNNER (Esecuzione Leggera)
# ==========================================
FROM python:3.11-slim-bullseye as runner

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    # Aggiungiamo il virtualenv al PATH di sistema
    PATH="/app/.venv/bin:$PATH" \
    PYTHONPATH="/app/src"

WORKDIR /app

# Installiamo solo le librerie runtime necessarie (libpq5 per driver postgres)
# Niente compilatori (gcc) o poetry qui -> Sicurezza e leggerezza
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq5 \
    && rm -rf /var/lib/apt/lists/*

# Creiamo un utente non-root per sicurezza (Best Practice Corporate)
RUN groupadd -g 999 appuser && \
    useradd -r -u 999 -g appuser appuser

# Copiamo il Virtual Environment dallo stage Builder
COPY --from=builder /app/.venv /app/.venv

# Copiamo il codice sorgente
COPY src/ src/

# Creiamo le cartelle per statici e media e assegniamo i permessi all'utente
RUN mkdir -p /app/src/staticfiles && \
    mkdir -p /app/src/media && \
    chown -R appuser:appuser /app

# Passiamo all'utente limitato
USER appuser

# Esporriamo la porta
EXPOSE 8000

# Comando di avvio di default (Production ready)
# Nota: ensure_keys è incluso per auto-healing
CMD ["sh", "-c", "python src/manage.py collectstatic --noinput && python src/manage.py migrate && python src/manage.py ensure_keys && gunicorn config.wsgi:application --bind 0.0.0.0:8000 --workers 4 --threads 4 --chdir src"]
