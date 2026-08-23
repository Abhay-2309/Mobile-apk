# MeshLink Rescue — Disaster-Resilient Offline BLE Mesh System

**MeshLink Rescue** is a zero-infrastructure, disaster-resilient communication system designed to relay SOS messages from victim mobile devices through intermediary relay devices to a rescue drone / Raspberry Pi receiver without requiring cellular, Wi-Fi, MQTT, or cloud infrastructure.

---

## 1. End-to-End Architecture & Multi-Hop Flow

```text
Flutter Victim
      ↓ BLE
Relay Phone
      ↓ BLE
Relay Phone
      ↓ BLE
Rescue Drone
      ↓
Raspberry Pi BLE Receiver
```

### Multi-Hop Relay Chain Example

```text
PHONE A (Victim)
  ↓ BLE (MessageId: 0xA3F7C2E1, TTL: 5, HOPS: 0)
PHONE B (Relay 1)
  ↓ BLE (MessageId: 0xA3F7C2E1, TTL: 4, HOPS: 1)
PHONE C (Relay 2)
  ↓ BLE (MessageId: 0xA3F7C2E1, TTL: 3, HOPS: 2)
RASPBERRY PI / DRONE RECEIVER
  → Decodes packet, validates CRC, checks duplicates, prints formatted SOS receipt!
```

---

## 2. Project Codebase Structure

```text
Testing_part/
│
├── meshlink_mobile/                   # Flutter + Kotlin Native BLE Mobile Application
│   ├── lib/
│   │   ├── main.dart                  # App entry point
│   │   ├── app.dart                   # MaterialApp + Provider setup
│   │   ├── core/
│   │   │   ├── constants/mesh_constants.dart
│   │   │   ├── errors/mesh_exception.dart
│   │   │   └── utils/crc16.dart
│   │   ├── models/
│   │   │   ├── sos_message.dart
│   │   │   ├── relay_result.dart
│   │   │   └── mesh_device.dart
│   │   ├── services/
│   │   │   ├── location_service.dart  # High-accuracy GPS + last-known fallback
│   │   │   ├── battery_service.dart   # Reads device battery level
│   │   │   ├── sos_service.dart       # Victim SOS creation & broadcasting
│   │   │   ├── mesh_service.dart      # Relay coordination & deduplication
│   │   │   ├── packet_service.dart    # 28-byte binary frame codec (Dart)
│   │   │   └── local_message_store.dart # SQLite database persistence
│   │   ├── native/
│   │   │   └── ble_platform_service.dart # MethodChannel / EventChannel bridge
│   │   ├── screens/
│   │   │   ├── splash/splash_screen.dart
│   │   │   ├── home/home_screen.dart
│   │   │   ├── sos/sos_screen.dart     # Victim UI mode
│   │   │   ├── relay/relay_screen.dart # Relay UI mode
│   │   │   └── network/diagnostics_screen.dart # Developer diagnostics screen
│   │   └── widgets/
│   │       ├── sos_button.dart
│   │       ├── mesh_status_card.dart
│   │       ├── packet_status_card.dart
│   │       └── relay_status_card.dart
│   │
│   ├── android/app/src/main/
│   │   ├── AndroidManifest.xml        # BLE Scan/Advertise & Location permissions
│   │   └── kotlin/com/meshlink/
│   │       ├── MainActivity.kt        # MethodChannel & EventChannel handlers
│   │       └── ble/
│   │           ├── BleAdvertiser.kt   # Native BluetoothLeAdvertiser
│   │           ├── BleScanner.kt      # Native BluetoothLeScanner
│   │           ├── BlePacketCodec.kt  # 28-byte codec & CRC16 (Kotlin)
│   │           └── BleMeshManager.kt  # Simultaneous scanner + advertiser
│   └── test/
│       ├── packet_codec_test.dart
│       ├── sos_service_test.dart
│       ├── duplicate_detection_test.dart
│       └── mesh_simulator_integration_test.dart # Multi-hop chain test
│
├── drone_receiver/                    # Python 3 Drone / Raspberry Pi Receiver
│   ├── src/
│   │   ├── main.py                    # Formatted SOS receipt & CLI runner
│   │   ├── ble_scanner.py             # Bleak BLE scanner for Linux/Raspberry Pi
│   │   ├── packet_codec.py            # 28-byte codec & CRC16 (Python)
│   │   ├── packet_validator.py        # Strict bounds & CRC validation
│   │   ├── message_store.py           # Deduplication store & JSON log
│   │   └── config.py                  # Constants
│   ├── tests/
│   │   └── test_drone_receiver.py     # Python unit tests
│   ├── requirements.txt
│   ├── Dockerfile
│   └── README.md
│
├── PACKET_SPECIFICATION.md            # Detailed field definitions & protocol rules
├── BLE_ENCODING_SPECIFICATION.md       # 28-byte binary layout & CRC16 spec
└── README.md                          # Main project guide
```

---

## 3. Key Protocol Invariants

1. **One Logical SOS = One `messageId`**: Generated once when victim presses SOS. Preserved across all hops.
2. **Relay Identity Preservation**: Relay nodes NEVER replace `messageId`, `senderIdHash`, `latitude`, `longitude`, `timestamp`, `battery`, or `severity`.
3. **Hop Adjustments**: Relay nodes ONLY decrement `ttl` (`ttl = ttl - 1`) and increment `hopCount` (`hopCount = hopCount + 1`).
4. **TTL Expiration**: When `ttl <= 0`, relay forwarding stops immediately.
5. **Duplicate Suppression**: Before processing or forwarding any BLE advertisement, the app checks if `messageId` exists in its local received store. If present, it is silently discarded to prevent broadcast storms.
6. **No Internet/Cloud Required**: 100% offline BLE operation.

---

## 4. Mobile App Testing Instructions

### Requirements for Physical Device Testing
- **Physical Android Devices Required**: Android emulators DO NOT support BLE advertising (`BluetoothLeAdvertiser`). Minimum 2 Android physical devices needed for relay testing (3+ recommended for multi-hop).
- **Permissions**:
  1. Bluetooth MUST be turned ON.
  2. Location Services MUST be turned ON (required by Android OS for BLE scanning).
  3. Grant `Nearby Devices` (Bluetooth Scan/Advertise) & `Location` permissions when prompted.

### Running the App
```bash
cd meshlink_mobile
flutter pub get
flutter run
```

---

## 5. Drone / Raspberry Pi Receiver Testing Instructions

### Simulator Mode (Software Validation)
Run simulator mode to verify 28-byte binary frame decoding, coordinate extraction, and duplicate suppression on any machine:

```bash
cd drone_receiver
python3 -m src.main --mode simulator
```

Output:
```text
========================================
        MESHLINK RESCUE DRONE
========================================

SOS RECEIVED

Message ID : 0xA3F7C2E1
Sender     : DEV-A1B2C3

Latitude   : 23.8103
Longitude  : 90.4125

TTL        : 2
Hop Count  : 3

Battery    : 82%
Severity   : CRITICAL

Received At:
2026-08-22T09:24:39

========================================


[SIMULATOR] Injecting duplicate packet over alternate mesh path...

[DUPLICATE]
0xA3F7C2E1
Already received
```

### Real BLE Mode (Raspberry Pi Hardware)
On a Raspberry Pi running Linux:

```bash
cd drone_receiver
pip install -r requirements.txt
python3 -m src.main --mode real
```

---

## 6. Known Android BLE Limitations & Mitigations

1. **Simultaneous Scan + Advertise Restrictions**: Some budget Android chipset drivers throttle back-to-back BLE scan and advertise operations. MeshLink mitigates this by using burst advertising (15-second advertisement window on relay) and asynchronous background scanning.
2. **Doze Mode & Background Restrictions**: Android 12+ restricts background BLE advertising. In production deployment, `BleMeshManager` should be attached to an Android Foreground Service with notification.
3. **BLE Payload Constraints**: Standard legacy BLE advertising frames limit payload to 31 bytes total. MeshLink's deterministic 28-byte binary payload fits within standard legacy advertising frames without requiring Extended Advertising (BLE 5.0).
