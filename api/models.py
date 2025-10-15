from django.db import models


# Create your models here.
class EncounterUser(models.Model):
    id = models.BigAutoField(primary_key=True)
    username = models.CharField(max_length=150, unique=True)
    display_name = models.CharField(max_length=150)
    # 8 bytes -> 16 hex chars
    id_hex = models.CharField(max_length=16, unique=True)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.username} ({self.id_hex})"
