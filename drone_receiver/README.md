# MeshLink Rescue — Raspberry Pi Drone Receiver

The **Drone Receiver** is a Python 3 daemon designed to run on a Raspberry Pi mounted on a rescue drone. It continuously scans for offline BLE advertisements matching the MeshLink manufacturer format (`0x4D4C`), decodes the binary SOS frame, validates coordinates and data integrity, suppresses duplicate packet arrivals, and displays formatted SOS receipts.

---

## 1. Quick Start

### Installation

```bash
cd drone_receiver
pip install -r requirements.txt
```

### Running Simulator Mode (Software Testing)

Run simulator mode to test binary frame unpacking and duplicate suppression without requiring physical Bluetooth hardware:

```bash
python3 -m src.main --mode simulator
```

### Running Real BLE Mode (Raspberry Pi / Linux Hardware)

On a Raspberry Pi running Linux with Bluetooth enabled:

```bash
python3 -m src.main --mode real
```

---

## 2. Docker Deployment on Raspberry Pi

To deploy as a Docker container on Raspberry Pi:

```bash
docker build -t meshlink-drone-receiver .
docker run --net=host --privileged -v /var/run/dbus:/var/run/dbus meshlink-drone-receiver
```

---

## 3. Formatted Drone SOS Output Example

When a valid packet is received:

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
2026-08-22T08:30:12

========================================
```

If the same packet arrives again over an alternate mesh path:

```text
[DUPLICATE]
0xA3F7C2E1
Already received
```

---

## 4. Test Suite

Run the unit tests:

```bash
python3 tests/test_drone_receiver.py
```
