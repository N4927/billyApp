import typing
from django.urls import path
from django.urls.resolvers import URLPattern
from .views import ResolveProximityView, DownloadBatchView

"""
URL Configuration for the Proximity Domain.

This module maps the HTTP endpoints to the specific Views that handle
the BLE tracing logic. It exposes the interfaces for both the transmitting
flow (Batches) and the receiving flow (Resolution).
"""

urlpatterns: typing.List[URLPattern] = [
    # Endpoint for Receiving Clients.
    # Resolves a specific anonymous B_ID into a User Profile.
    path("resolve/", ResolveProximityView.as_view(), name="resolve_b_id"),
    # Endpoint for Transmitting Clients.
    # Downloads the daily cryptographic material for advertising.
    path("batches/", DownloadBatchView.as_view(), name="download_batches"),
]
