from django.urls import path
from .views import create_user, list_users, match


urlpatterns = [
    path("register/", create_user, name="register"),
    path("users/list/", list_users, name="users-list"),
    path("match/", match, name="match"),
]
