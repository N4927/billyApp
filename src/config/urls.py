import typing
from django.contrib import admin
from django.urls import path, include
from django.urls.resolvers import URLResolver, URLPattern
from drf_spectacular.views import SpectacularAPIView, SpectacularSwaggerView

# Defines a type alias for URL patterns
URLEntity = typing.Union[URLResolver, URLPattern]


def get_urlpatterns() -> typing.List[URLEntity]:
    """
    Constructs and returns the list of URL patterns for the application.

    Organizes routes into logical groups: Administration, Business Logic (Auth, Proximity),
    and Documentation (Schema, Swagger UI).

    :return: List of [URLEntity] objects representing the routing table.
    """
    return [
        # --- Administration ---
        path("admin/", admin.site.urls),
        # --- Business Logic ---
        # Maps authentication endpoints (Login, Register, Tokens).
        path("api/v1/auth/", include("authentication.urls")),
        # Maps proximity business logic endpoints (Batch, Resolve).
        path("api/v1/proximity/", include("proximity.urls")),
        # --- Documentation ---
        # Generates the OpenAPI 3.0 schema file (YAML/JSON).
        path("api/schema/", SpectacularAPIView.as_view(), name="schema"),
        # Renders the interactive Swagger UI based on the schema.
        path(
            "api/docs/",
            SpectacularSwaggerView.as_view(url_name="schema"),
            name="swagger-ui",
        ),
    ]


# Exposes the URL patterns to the Django application.
# Django expects a module-level variable named 'urlpatterns'.
urlpatterns: typing.List[URLEntity] = get_urlpatterns()
