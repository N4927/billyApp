from django.db import models
from django.conf import settings

"""
Modulo di definizione del Modello di Dominio Utente.

Implementa il pattern "Profile" per estendere il modello utente standard di Django (Auth)
senza ereditarlo direttamente (Composition Over Inheritance - COI).
Questo modello gestisce i dati specifici del dominio BLE/Proximity.
"""


class AppUser(models.Model):
    """
    Rappresenta il profilo esteso dell'utente per le funzionalità di Proximity.
    Mantiene una relazione 1:1 con l'utente di autenticazione e custodisce l'identificativo
    univoco segreto (U_code).
    """

    # Link 1:1 con l'utente di autenticazione (Identity).
    # La cancellazione dell'utente Auth comporta la cancellazione a cascata del profilo.
    user = models.OneToOneField(
        settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="ble_profile"
    )

    # Identificativo univoco a 64 bit utilizzato per la generazione dei B_id.
    # db_index=True è fondamentale per garantire lookup O(log N) durante la risoluzione.
    u_code = models.BigIntegerField(
        unique=True,
        db_index=True,
        help_text="64-bit Unique Identifier used for BLE seed generation",
    )

    # Timestamp di creazione per audit e debugging.
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        verbose_name = "App User Profile"
        verbose_name_plural = "App User Profiles"

    def __str__(self) -> str:
        """
        Restituisce una rappresentazione stringa dell'oggetto.

        :return: Lo username dell'utente associato.
        """
        # Accessing self.user might trigger a DB query if not selected_related.
        # In admin panels, usually handled by the queryset manager.
        return f"{self.user.username}"
