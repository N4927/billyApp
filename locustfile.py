import random
import string
from locust import HttpUser, task, between


class BillyAppUser(HttpUser):
    # Simulated wait time between requests (human behavior).
    # In a DDoS attack scenario, this would be 0.
    wait_time = between(1, 3)

    def on_start(self):
        """
        Lifecycle Hook: Executed when a virtual user is spawned.
        Handles registration and token acquisition (Login).
        """
        self.email = f"load_{self.random_string(10)}@loadtest.com"
        self.username = f"user_{self.random_string(8)}"
        self.password = "StrongP@ssw0rd!"
        self.token = None

        # 1. Registration
        with self.client.post(
            "/api/v1/auth/register/",
            json={
                "email": self.email,
                "username": self.username,
                "password": self.password,
            },
            catch_response=True,
        ) as response:
            if response.status_code != 201:
                # Ignore error if user already exists (allows test re-runs)
                if "already exists" not in response.text:
                    response.failure(f"Reg Failed: {response.text}")
                    return

        # 2. Login
        with self.client.post(
            "/api/v1/auth/token/",
            json={"email": self.email, "password": self.password},
            catch_response=True,
        ) as response:
            if response.status_code == 200:
                self.token = response.json()["access"]
                self.client.headers.update({"Authorization": f"Bearer {self.token}"})
            else:
                response.failure(f"Login Failed: {response.text}")

    @task(3)  # Probability 3x: Download Batch (Frequent operation)
    def download_daily_batch(self):
        if not self.token:
            return

        with self.client.get(
            "/api/v1/proximity/batches/", catch_response=True
        ) as response:
            if response.status_code != 200:
                response.failure(
                    f"Batch Error: {response.status_code} - {response.text}"
                )

    @task(5)  # Probability 5x: Resolve Contact (Most frequent and CPU-heavy operation)
    def resolve_contact(self):
        if not self.token:
            return

        # Generate a valid hex format B_ID but with random content.
        # The server must still perform AES decryption to determine it's invalid or unknown.
        # This effectively stresses the server's CPU.
        fake_b_id = "".join(random.choices("0123456789abcdef", k=32))

        with self.client.post(
            "/api/v1/proximity/resolve/", json={"b_id": fake_b_id}, catch_response=True
        ) as response:
            # We expect 404 (User not found) or 200 (Found), but never 500
            if response.status_code not in [200, 404]:
                response.failure(f"Resolve Error: {response.status_code}")

    def random_string(self, length):
        return "".join(random.choices(string.ascii_lowercase, k=length))
