from django.apps import AppConfig

"""
Modulo di configurazione per l'applicazione 'users'.

Questo modulo definisce la classe di configurazione che Django utilizza per
inizializzare l'applicazione, impostare i valori predefiniti e registrare
i componenti al momento dell'avvio (hook 'ready').
"""


class UsersConfig(AppConfig):
    """
    Classe di configurazione per il dominio Users.

    Responsabilità (SRP):
    1. Definire il tipo di campo chiave primaria predefinito.
    2. Identificare univocamente l'applicazione nel progetto.
    3. Registrare i Segnali (Signals) all'avvio del framework.
    """

    # Imposta BigAutoField come default per le chiavi primarie (ID).
    # Questo garantisce scalabilità futura (64-bit integer) per le tabelle
    # di questo modulo, prevenendo l'overflow degli ID a 32-bit.
    default_auto_field: str = "django.db.models.BigAutoField"

    # Il nome completo del modulo Python dell'applicazione.
    # Usato da Django per cercare le risorse (migrazioni, template, ecc.).
    name: str = "users"

    def ready(self) -> None:
        """
        Hook di inizializzazione eseguito quando Django ha completato il caricamento
        del registro delle applicazioni.

        Principio di funzionamento:
        Importiamo il modulo 'signals' qui dentro (invece che a livello globale)
        per evitare un 'AppRegistryNotReady' error. I segnali hanno bisogno che
        i modelli siano caricati prima di poter essere collegati.

        :return: None
        """
        # Side Effect: L'importazione registra i ricevitori (@receiver) definiti in signals.py
        import users.signals  # noqa: F401
