import logging
from django.core.management.base import BaseCommand
from crypto.services import KeyManager
from crypto.models import MasterKey

logger = logging.getLogger(__name__)


class Command(BaseCommand):
    help = "Ensures that at least one Master Key exists. Creates initial key if DB is empty."

    def handle(self, *args, **options):
        # Verifica se esistono chiavi (anche scadute, per sicurezza, ma qui ci interessano le attive)
        # Se il DB è vuoto, ne crea una.
        if not MasterKey.objects.exists():
            self.stdout.write(
                self.style.WARNING("No Master Keys found. Initializing system...")
            )
            try:
                KeyManager.generate_initial_key()
                self.stdout.write(
                    self.style.SUCCESS("Initial Master Key created successfully.")
                )
            except Exception as e:
                self.stdout.write(self.style.ERROR(f"Failed to create key: {e}"))
                # In produzione, se questo fallisce, il container deve morire
                import sys

                sys.exit(1)
        else:
            self.stdout.write(
                self.style.SUCCESS(
                    "Master Keys already exist. Skipping initialization."
                )
            )
