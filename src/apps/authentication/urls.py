import typing
from django.urls import path
from django.urls.resolvers import URLPattern
from rest_framework_simplejwt.views import TokenRefreshView
from .views import RegisterView, EmailTokenObtainPairView

"""
Authentication URL Configuration.

This module maps the entry points for the identity management system.
It adheres to RESTful conventions for resource creation (register) and
session management (token).
"""

urlpatterns: typing.List[URLPattern] = [
    # Endpoint: POST /api/v1/auth/register/
    # Public access for creating new account identities.
    path("register/", RegisterView.as_view(), name="auth_register"),
    # Endpoint: POST /api/v1/auth/token/
    # Login mechanism: Exchanges Email/Password for Access/Refresh JWT pair.
    path("token/", EmailTokenObtainPairView.as_view(), name="token_obtain_pair"),
    # Endpoint: POST /api/v1/auth/token/refresh/
    # Session extension: Exchanges valid Refresh token for new Access token.
    path("token/refresh/", TokenRefreshView.as_view(), name="token_refresh"),
]
