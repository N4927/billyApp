from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status
from django.views.decorators.csrf import csrf_exempt
from .models import EncounterUser
from .serializers import (
    EncounterUserSerializer,
    ResolveRequestSerializer,
    ResolveResponseSerializer,
)
from .crypto import compute_cipher8
from django.conf import settings as core_settings
from typing import cast, Any, Dict


@api_view(["POST"])
@csrf_exempt
def create_user(request):
    ser = EncounterUserSerializer(data=request.data)
    if ser.is_valid():
        user = ser.save()
        return Response(
            EncounterUserSerializer(user).data, status=status.HTTP_201_CREATED
        )
    return Response(ser.errors, status=status.HTTP_400_BAD_REQUEST)


@api_view(["GET"])
@csrf_exempt
def list_users(request):
    qs = EncounterUser.objects.all().order_by("id")
    return Response(EncounterUserSerializer(qs, many=True).data)


@api_view(["POST"])
@csrf_exempt
def match(request):
    ser = ResolveRequestSerializer(data=request.data)

    if not ser.is_valid():
        return Response(ser.errors, status=status.HTTP_400_BAD_REQUEST)

    data = cast(Dict[str, Any], ser.validated_data)

    ts = int(data["timestamp"])
    c8_target = str(data["cipher8_hex"]).lower()
    window = ts // core_settings.RID_ROTATION_SECONDS

    for u in EncounterUser.objects.all().only(
        "id", "username", "display_name", "id_hex"
    ):
        c8 = compute_cipher8(u.username, u.id_hex, ts).hex()
        if c8 == c8_target:
            out = ResolveResponseSerializer(
                {
                    "user_id": u.id,
                    "username": u.username,
                    "display_name": u.display_name,
                    "id_hex": u.id_hex,
                    "matched_window": window,
                }
            ).data
            return Response(out)

    return Response(
        {"detail": "Nessuna corrispondenza"}, status=status.HTTP_404_NOT_FOUND
    )
