from django.contrib import admin
from .models import MasterKey


@admin.register(MasterKey)
class MasterKeyAdmin(admin.ModelAdmin):
    list_display = ("start_time", "end_time", "created_at")
    exclude = ("key_bytes",)
