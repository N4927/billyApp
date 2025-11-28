import typing
from rest_framework.views import APIView
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework import status
from rest_framework.permissions import IsAuthenticated
from drf_spectacular.utils import extend_schema, OpenApiResponse

# Local application imports
from .serializers import ResolutionRequestSerializer, ResolutionResponseSerializer
from .services import BatchService
from crypto.services import KeyManager
from crypto.engine import BLECryptoEngine
from users.models import AppUser

"""
Proximity Business Logic Views.

This module handles the core interaction between the mobile client and the backend
regarding BLE operations:
1. Transmitting Clients: Downloading daily batches of encrypted IDs.
2. Receiving Clients: Resolving observed encrypted IDs into user profiles.
"""


class DownloadBatchView(APIView):
    """
    Handles the retrieval of ephemeral Bluetooth Low Energy (BLE) identifiers.
    """

    permission_classes = [IsAuthenticated]

    @extend_schema(
        summary="Download Daily BLE Batch",
        description="Generates and returns 144 encrypted B_IDs valid for the next 24 hours.",
        responses={
            200: OpenApiResponse(description="Batch generated successfully"),
            404: OpenApiResponse(
                description="User profile not found (Data integrity error)"
            ),
            503: OpenApiResponse(
                description="Service unavailable (Missing Master Keys)"
            ),
        },
    )
    def get(self, request: Request) -> Response:
        """
        Orchestrates the generation of the daily batch for the authenticated user.

        Retrieves the user's secret U_code from Hot Storage (Redis) to minimize DB latency,
        then utilizes the Crypto Engine to generate future keys.

        :param request: [Request] The HTTP request object containing user authentication context.
        :return: [Response] JSON payload containing start_slot, duration, and the list of B_IDs.
        """
        # Accessing request.user.id is safe here due to IsAuthenticated permission.
        u_code: typing.Optional[int] = BatchService.get_user_ucode(request.user.id)

        if not u_code:
            # This indicates a data integrity issue where an Auth User exists but no AppUser profile.
            return Response(
                {"detail": "User profile integrity error: U_code not found."},
                status=status.HTTP_404_NOT_FOUND,
            )

        try:
            # CPU-bound operation: Generates 144 AES encryptions.
            # This is synchronous but fast enough for typical loads.
            # For extremely high concurrency, consider offloading to Celery.
            batch_data = BatchService.generate_daily_batch(u_code)
            return Response(batch_data, status=status.HTTP_200_OK)
        except Exception as error:
            # Catch-all for critical crypto failures (e.g., missing keys in DB).
            # We return 503 to indicate the client should retry later.
            return Response(
                {"detail": str(error)}, status=status.HTTP_503_SERVICE_UNAVAILABLE
            )


class ResolveProximityView(APIView):
    """
    Handles the resolution of observed encrypted BLE packets.
    """

    permission_classes = [IsAuthenticated]

    @extend_schema(
        summary="Resolve BLE Contact",
        description="Decodes a single B_ID found via BLE scanning to identify the nearby user.",
        request=ResolutionRequestSerializer,
        responses={
            200: ResolutionResponseSerializer,
            400: OpenApiResponse(description="Invalid B_ID hex format"),
            404: OpenApiResponse(
                description="User not found, B_ID expired, or invalid key"
            ),
            503: OpenApiResponse(description="Service unavailable (No active keys)"),
        },
    )
    def post(self, request: Request) -> Response:
        """
        Processes a resolution request for a specific B_ID.

        Algorithm (O(k) where k is number of active keys):
        1. Validates input format.
        2. Retrieves active master keys (Rotation + Grace Period).
        3. Attempts AES decryption with each key.
        4. Validates the time window (Anti-Replay).
        5. Lookups the user profile in the database.

        :param request: [Request] HTTP request containing the 'b_id' payload.
        :return: [Response] JSON containing the identified user's display name and contact timestamp.
        """
        # 1. Input Validation
        serializer = ResolutionRequestSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        b_id_bytes: bytes = serializer.validated_data["b_id"]

        # 2. Key Retrieval
        # We need all keys that could possibly have encrypted this packet.
        keys = KeyManager.get_candidate_keys()
        if not keys:
            return Response(
                {"detail": "Service Unavailable: No active master keys found."},
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )

        found_user: typing.Optional[AppUser] = None

        # 3. Decryption Loop
        # We iterate through candidate keys (max 2 usually: current + previous).
        # This handles the edge case where a key rotation happened recently.
        for key in keys:
            crypto_engine = BLECryptoEngine(key.key_bytes)
            result = crypto_engine.decrypt_b_id(b_id_bytes)

            if result:
                u_code, s_slot = result

                # 4. Time Window Validation (Anti-Replay)
                # Ensures the packet is not a replay of an old valid packet.
                if crypto_engine.is_slot_valid(s_slot):
                    try:
                        # 5. User Lookup
                        # Uses select_related to fetch the auth User model in a single query.
                        # This avoids the N+1 query problem when accessing user.username later.
                        found_user = AppUser.objects.select_related("user").get(
                            u_code=u_code
                        )

                        break
                    except AppUser.DoesNotExist:
                        # Decrypted successfully but U_code does not exist in DB.
                        # Could happen if a user was deleted but their B_IDs are still broadcasted.
                        continue

        # 6. Response Construction
        if found_user:
            # Determine the best display name available
            display_name = found_user.user.first_name or found_user.user.username

            # We use the serializer to ensure the response structure matches the API contract.
            response_data = {
                "display_name": display_name,
                # Optional: If you updated the serializer to include timestamp, add it here
                # "contact_timestamp": contact_timestamp
            }

            return Response(response_data, status=status.HTTP_200_OK)

        # Default security response: 404 Not Found.
        # We do not differentiate between "Bad Key", "Expired", or "Unknown User"
        # to prevent Oracle Attacks (Timing or Error based).
        return Response(
            {"detail": "Resolution failed"}, status=status.HTTP_404_NOT_FOUND
        )
