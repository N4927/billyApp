# BillyApp (iOS + Android) con risoluzione lato Server

Questa repository contiene le implementazioni per **iOS (Swift)** e **Android (Kotlin)** della logica di pubblicazione e scansione di identificatori rotanti via **Bluetooth Low Energy (BLE)**.  
La parte di **risoluzione degli ID** (associazione ID → nome utente) è spostata completamente sul **server** per garantire sicurezza e scalabilità.

---

## 🔹 Funzionamento generale

1. Ogni dispositivo (Android/iOS) genera un **identificatore rotante (ID)** con `RotatingIdGenerator`.  
   - L’ID cambia ogni `Constants.ROTATION_SECONDS` secondi.  
   - Viene trasmesso come **Service Data** BLE sotto un `SERVICE_UUID` fisso.  

2. Gli altri dispositivi nelle vicinanze effettuano lo **scanning** e ricevono:  
   - l’`idHex` (stringa esadecimale)  
   - il `timestamp` (secondi UNIX dell’avvistamento).  

3. L’app invia questi dati al **server** tramite `POST /api/resolve`.  

4. Il server restituisce il **nome utente associato** (se riconosciuto), oppure `null`.  

5. L’app registra l’incontro in memoria (o database), aggiornando un contatore delle volte in cui lo stesso nome è stato visto.  

---

## 🔹 API del Server

Il server è il **punto centrale** della logica.  
Deve esporre almeno un endpoint REST per risolvere gli ID.

### Endpoint principale: Risoluzione
```
POST /api/resolve
Content-Type: application/json
```

### Request
```json
{
  "id": "aabbccddeeff00112233",
  "timestamp": 1706200000
}
```

- `id`: stringa esadecimale dell’ID BLE ricevuto.  
- `timestamp`: tempo (epoch secondi) in cui è stato rilevato.  

### Response
```json
{
  "name": "Person1"
}
```

oppure, se sconosciuto:
```json
{
  "name": null
}
```

---

## 🔹 Logica del Server

Il server deve:  
1. **Gestire un database utenti** → ogni utente ha una chiave segreta (`userSecret`).  
2. **Rigenerare ID attesi** usando lo stesso algoritmo di `RotatingIdGenerator` (con `userSecret`, `salt`, `bucket`).  
3. **Confrontare** l’ID ricevuto con gli ID calcolati per il periodo `timestamp ± window`.  
4. Se c’è match → restituisce il `name` associato all’utente.  
5. Se non trova match → restituisce `name = null`.  

> 🔒 Le chiavi segrete restano solo lato server.  
> I client (Android/iOS) non hanno accesso ai `userSecret`.

---

## 🔹 Schema di Rotating ID

- Algoritmo: SHA-256 di (`userSecret + salt + bucket`).  
- `bucket = floor(epochSeconds / rotationSeconds)`.  
- Output = primi 16 byte dell’hash.  
- `serviceUUID` è fisso (es. `0000FEAA-0000-1000-8000-00805F9B34FB`).  

---

## 🔹 Esempi di Implementazione Server

### Kotlin + Ktor (scheletro)
```kotlin
post("/api/resolve") {
    val req = call.receive<ResolveRequest>()
    val user = database.find { matchId(req.id, req.timestamp, it.secret) }
    if (user != null) {
        call.respond(ResolveResponse(user.name))
    } else {
        call.respond(ResolveResponse(null))
    }
}
```

### Python + FastAPI (scheletro)
```python
@app.post("/api/resolve")
def resolve(req: ResolveRequest):
    for user in users_db:
        if match_id(req.id, req.timestamp, user.secret):
            return {"name": user.name}
    return {"name": None}
```

---

## 🔹 Sicurezza

- Usare **HTTPS** obbligatorio.  
- Possibile aggiungere un token API o OAuth per autenticare le richieste.  
- Logging controllato: non salvare direttamente gli `idHex` se non necessario.  

---

## 🔹 Dati salvati lato client

- iOS → array `encounters` (`Encounter(name, count)`) pubblicato via `@Published`.  
- Android → `ConcurrentHashMap<String, Encounter>` con chiave = `name`.  

Ogni volta che viene visto lo stesso `name`, il `count` viene incrementato.  

---

## ✅ In sintesi

- iOS/Android:  
  - generano ID → trasmettono con BLE  
  - scansionano ID vicini → inviano al server per risoluzione  
  - salvano nome + count  

- Server:  
  - custodisce i `userSecret`  
  - rigenera ID validi per i tempi recenti  
  - risponde con `name` o `null`  

Questo approccio garantisce che la **logica sensibile** (chiavi, matching) resti sul server e non sui dispositivi.
