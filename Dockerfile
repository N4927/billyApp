# Usa un'immagine leggera e sicura di Python
FROM python:3.11-slim-bullseye

# Variabili d'ambiente per ottimizzare Python in Docker
ENV PYTHONDONTWRITEBYTECODE 1
ENV PYTHONUNBUFFERED 1
ENV PYTHONPATH "${PYTHONPATH}:/app/src"

# Workdir
WORKDIR /app

# Installa dipendenze di sistema necessarie per compilare pacchetti C (es. crypto)
RUN apt-get update \
    && apt-get install -y --no-install-recommends gcc libpq-dev \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Installa Poetry
RUN pip install poetry

# Copia i file di dipendenza
COPY pyproject.toml poetry.lock ./

# Installa le dipendenze (senza creare virtualenv, siamo già in un container)
RUN poetry config virtualenvs.create false \
    && poetry install --no-interaction --no-ansi --no-root

# Copia il codice sorgente
COPY src/ src/

COPY tests/ tests/

COPY pytest.ini .

# Espone la porta (Gunicorn userà la 8000)
EXPOSE 8000

# Comando di default (sarà sovrascritto da docker-compose o entrypoint)
CMD ["gunicorn", "config.wsgi:application", "--bind", "0.0.0.0:8000"]
