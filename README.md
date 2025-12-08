
# 📱 BillyApp Android Client — Technical Documentation

BillyApp Android is the official proximity-aware mobile client implementing:
- BLE scanning and advertising  
- Encrypted proximity identifiers (B_ID)  
- KMM Shared Core ingestion + resolution  
- Foreground BLE services  
- Jetpack Compose UI  
- Real-time reactive flows  

---

# 🧱 Project Structure

```
com.example.billyapp
├─ bluetooth/
│  ├─ BluetoothCentralService.kt
│  ├─ BluetoothPeripheralService.kt
│  └─ BleConstants.kt
│
├─ proximity/
│  ├─ ProximityViewModel.kt
│  └─ KmmProximityDataSource.kt
│
├─ kmm/
│  ├─ KmmEnvironment.kt
│  └─ AndroidTokenStorage.kt
│
├─ core/
│  ├─ User.kt
│  ├─ UserManager.kt
│  ├─ ChatViewModel.kt
│  └─ CryptographyManager.kt
│
├─ ui/
│  └─ screens/
│     ├─ HomeScreen.kt
│     ├─ ChatsScreen.kt
│     ├─ ChatScreen.kt
│     ├─ ProfileScreen.kt
│     └─ ProfileSetupScreen.kt
│
└─ MainActivity.kt
```

---

# 🔄 Proximity Pipeline

## 1. Advertising (Peripheral)
- Retrieves active BID from KMM  
- Packs B_ID → BLE advertisement service data  
- Foreground service ensures stable operation  

## 2. Scanning (Central)
- Scans for BLE packets containing B_ID  
- Extracts 16-byte encrypted payload  
- Converts to hex  
- Sends to KMM:  
```kotlin
core.ingestPacket(bidHex)
core.syncQueue()
```

## 3. KMM Core
- Stores encounters in SQLDelight
- Sends B_IDs to server via ingest/resolve
- Updates `ResolvedRepository.activeSet`

## 4. Android UI
- Observes resolved users via  
  ```kotlin
  KmmEnvironment.resolvedUsersFlow
  ```
- Renders in HomeScreen  

---

# 🧩 KMM Integration

Place AAR in:

```
app/libs/shared-release.aar
```

Add dependencies:

```kotlin
implementation(files("libs/shared-release.aar"))
implementation("io.ktor:ktor-client-core:2.3.12")
implementation("io.ktor:ktor-client-android:2.3.12")
implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.4.1")
```

Initialize:

```kotlin
KmmEnvironment.init(this)
```

APIs used:

```kotlin
core.getCurrentBid()
core.ingestPacket(bidHex)
core.syncQueue()
core.ensureAdvertisingBatch()
```

---

# 📡 BLE Layer

## BluetoothPeripheralService
- Advertises encrypted B_ID  
- Handles rotation & restarts  
- Foreground mode  

## BluetoothCentralService
- Scans continuously  
- Extracts service data  
- Forwards B_ID to KMM  

---

# 🧭 UI Overview

### HomeScreen
- People nearby  
- Online/offline toggle  

### ChatsScreen
- Active chat list  

### ChatScreen
- Messaging  

### ProfileScreen
- User details  

### MainActivity
- BLE permissions  
- BLE service orchestration  
- Sets navigation  

---

# 🐞 Debugging Commands

Advertising:
```
adb logcat | grep BluetoothPeripheralService
```

Scanning:
```
adb logcat | grep BluetoothCentralService
```

Ingestion:
```
adb logcat | grep ingestPacket
```

Resolution:
```
adb logcat | grep activeSet
```

UI updates:
```
adb logcat | grep ProximityViewModel
```

---

# 🧪 Real-World Testing

1. Install app on two devices  
2. Enable Bluetooth + grant permissions  
3. Move devices within ~1–3 meters  
4. Expected behavior:
   - Phone A advertises BID  
   - Phone B scans → ingestPacket + syncQueue  
   - Server resolves identity  
   - HomeScreen displays user name  

---

# 🚀 Summary

BillyApp Android provides:
✔ BLE dual-role (scanner + advertiser)  
✔ Anonymous proximity via shared KMM module  
✔ Automatic ingestion + resolution pipeline  
✔ Real-time UI updates via StateFlow  
✔ Clean MVVM & Compose architecture  

Android = BLE + UI  
KMM = crypto + ingestion + resolution  

This ensures full parity between iOS and Android proximity behavior.
🟦 GAP-Only BLE Design (No GATT Used at This Stage)

BillyApp Android currently operates exclusively using GAP (Generic Access Profile) for its proximity protocol.

✔ GAP Used

The app relies entirely on:

BLE Advertising (Peripheral role)

BLE Scanning (Central role)

Service UUID + Service Data for transmitting the encrypted anonymous B_ID payload

This means all proximity interactions happen without establishing BLE connections.


