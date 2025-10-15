from django.contrib import admin
from .models import EncounterUser


@admin.register(EncounterUser)
class EncounterUserAdmin(admin.ModelAdmin):
    list_display = ("id", "username", "display_name", "id_hex", "created_at")
    search_fields = ("username", "display_name", "id_hex")
    list_filter = ("created_at",)
    ordering = ("-id",)
    readonly_fields = ("created_at",)
