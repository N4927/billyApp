from rest_framework import generics
from rest_framework.permissions import AllowAny
from django.contrib.auth import get_user_model
from rest_framework_simplejwt.views import TokenObtainPairView
from drf_spectacular.utils import extend_schema, OpenApiResponse

# Local imports
from .serializers import RegisterSerializer, EmailTokenObtainSerializer

"""
Authentication Views / Controllers.

This module handles the HTTP request/response cycle for identity operations.
It delegates validation logic to Serializers and persistence to Models.
"""

# Get the active User model (Custom or Standard)
# This ensures compatibility if the user model is swapped in settings.py.
User = get_user_model()


class RegisterView(generics.CreateAPIView):
    """
    Handles the registration of new users.

    This view is publicly accessible to allow user onboarding.
    It utilizes the RegisterSerializer to enforce uniqueness constraints
    and password complexity.
    """

    queryset = User.objects.all()
    # AllowAny ensures that unauthenticated users can access this endpoint.
    permission_classes = (AllowAny,)
    serializer_class = RegisterSerializer

    @extend_schema(
        summary="Register new user",
        description="Creates a new user account and automatically generates the associated BLE profile.",
        responses={
            201: OpenApiResponse(description="User created successfully"),
            400: OpenApiResponse(
                description="Validation error (Duplicate email/username)"
            ),
        },
    )
    def post(self, request, *args, **kwargs):
        """
        Handle POST request for user registration.

        :param request: [Request] The HTTP request object containing user data.
        :return: [Response] HTTP 201 Created on success, or 400 Bad Request on failure.
        """
        return super().post(request, *args, **kwargs)


class EmailTokenObtainPairView(TokenObtainPairView):
    """
    Custom Login View using Email instead of Username.

    Inherits from SimpleJWT's standard view but swaps the serializer
    to support the 'Email + Password' credential pair strategy.
    """

    serializer_class = EmailTokenObtainSerializer

    @extend_schema(
        summary="Login (Obtain Token Pair)",
        description="Exchanges Email and Password for JWT Access and Refresh tokens.",
        responses={
            200: OpenApiResponse(description="Login successful, tokens returned"),
            401: OpenApiResponse(description="Invalid credentials or inactive account"),
        },
    )
    def post(self, request, *args, **kwargs):
        """
        Handle POST request for user login.

        :param request: [Request] The HTTP request object containing credentials.
        :return: [Response] HTTP 200 OK with tokens, or 401 Unauthorized.
        """
        return super().post(request, *args, **kwargs)
