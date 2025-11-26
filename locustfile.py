import random
import string
from locust import HttpUser, task, between


class BillyAppUser(HttpUser):
    # Tempo di attesa simulato tra una richiesta e l'altra (umano)
    # In un attacco DDoS questo sarebbe 0.
    wait_time = between(1, 3)

    def on_start(self):
        """
        Ciclo di vita: Eseguito quando un utente virtuale viene spawnato.
        Si registra e ottiene il token.
        """
        self.email = f"load_{self.random_string(10)}@loadtest.com"
        self.username = f"user_{self.random_string(8)}"
        self.password = "StrongP@ssw0rd!"
        self.token = None

        # 1. Registrazione
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
                # Ignoriamo errore se l'utente esiste già (rilancio del test)
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

    @task(3)  # Probabilità 3x: Scaricare Batch (Operazione frequente)
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

    @task(
        5
    )  # Probabilità 5x: Risolvere contatto (Operazione più frequente e pesante CPU)
    def resolve_contact(self):
        if not self.token:
            return

        # Generiamo un B_ID esadecimale valido (formato) ma casuale
        # Il server dovrà comunque decifrarlo (AES) per capire che non è valido o non esiste.
        # Questo stressa la CPU del server.
        fake_b_id = "".join(random.choices("0123456789abcdef", k=32))

        with self.client.post(
            "/api/v1/proximity/resolve/", json={"b_id": fake_b_id}, catch_response=True
        ) as response:
            # Ci aspettiamo 404 (User not found) o 200 (Trovato), ma non 500
            if response.status_code not in [200, 404]:
                response.failure(f"Resolve Error: {response.status_code}")

    def random_string(self, length):
        return "".join(random.choices(string.ascii_lowercase, k=length))
