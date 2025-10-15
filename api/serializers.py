from __future__ import annotations
from rest_framework import serializers
from .models import EncounterUser
import secrets


class EncounterUserSerializer(serializers.ModelSerializer):
    id_hex = serializers.CharField(required=False)

    class Meta:
        model = EncounterUser
        fields = ["id", "username", "display_name", "id_hex", "created_at"]
        read_only_fields = ["id", "created_at"]
        extra_kwargs = {
            "id_hex": {"required": False},
        }

    def validate_id_hex(self, value: str) -> str:
        if len(value) != 16:
            raise serializers.ValidationError(
                "id_hex deve essere lungo 16 caratteri hex."
            )
        # valida che sia esadecimale
        int(value, 16)
        return value.lower()

    def create(self, validated_data):
        if "id_hex" not in validated_data or not validated_data["id_hex"]:
            validated_data["id_hex"] = secrets.token_hex(8)  # 8 bytes -> 16 hex
        return super().create(validated_data)


class ResolveRequestSerializer(serializers.Serializer):
    timestamp = serializers.IntegerField()
    cipher8_hex = serializers.RegexField(r"^[0-9a-fA-F]{16}$")


class ResolveResponseSerializer(serializers.Serializer):
    user_id = serializers.IntegerField()
    username = serializers.CharField()
    display_name = serializers.CharField()
    id_hex = serializers.CharField()
    matched_window = serializers.IntegerField()
