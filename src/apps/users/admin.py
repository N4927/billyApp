from django.contrib import admin
from .models import AppUser


@admin.register(AppUser)
class AppUserAdmin(admin.ModelAdmin):
    list_display = ("username", "u_code", "created_at")
    readonly_fields = ("u_code",)

    def username(self, obj):
        return obj.user.username
