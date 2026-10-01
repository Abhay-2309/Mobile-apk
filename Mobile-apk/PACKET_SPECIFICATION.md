# MeshLink Rescue — SOS Packet Specification (Phase 1–3)

## 1. Overview

The MeshLink Rescue SOS Packet is a compact, disaster-resilient binary protocol designed to transport distress signals over zero-infrastructure multi-hop Bluetooth Low Energy (BLE) mesh networks.

Each logical SOS message is created once when a victim presses the SOS button on their mobile phone and is relayed unchanged across intermediary relay phones until it reaches a rescue drone or ground station.

---

## 2. Field Definitions

| Field Name | Type | Size | Valid Range / Description |
|---|---|---|---|
| `magicHeader` | `uint8` | 1 Byte | Constant `0x4D` ('M' for MeshLink). Used for instant packet identification. |
| `versionAndType` | `uint8` | 1 Byte | High 4 bits: Protocol Version (`0x1`), Low 4 bits: Packet Type (`0x1` = SOS). |
| `messageId` | `uint32` | 4 Bytes | Big-endian uint32. Unique fingerprint generated ONCE per logical SOS. Preserved across all hops. |
| `senderIdHash` | `uint32` | 4 Bytes | Big-endian uint32 CRC32 hash of original victim device ID (e.g., `DEV-A1B2C3`). Preserved across all hops. |
| `latitude` | `int32` | 4 Bytes | Signed 32-bit int = `lat * 1,000,000`. Range: -90,000,000 to +90,000,000 (~0.11m accuracy). |
| `longitude` | `int32` | 4 Bytes | Signed 32-bit int = `lon * 1,000,000`. Range: -180,000,000 to +180,000,000 (~0.11m accuracy). |
| `timestamp` | `uint32` | 4 Bytes | Unix epoch timestamp in seconds when SOS was generated. Preserved across all hops. |
| `ttl` | `uint8` | 1 Byte | Time-to-Live. Initialized to `5`. Decremented by `1` at every relay node. Stops forwarding when `ttl == 0`. |
| `hopCount` | `uint8` | 1 Byte | Hop count. Initialized to `0`. Incremented by `1` at every relay node. |
| `battery` | `uint8` | 1 Byte | Victim device battery percentage (0–100%). Preserved across all hops. |
| `severity` | `uint8` | 1 Byte | Distress severity: `0=LOW`, `1=MEDIUM`, `2=CRITICAL`. Default: `2`. |
| `crc16` | `uint16` | 2 Bytes | CRC16-CCITT checksum over bytes 0–25. Big-endian uint16. |

**Total Frame Size: 28 Bytes**

---

## 3. Protocol Rules

1. **Identity Preservation**: A relay phone MUST NEVER generate a new `messageId` or replace the victim's `senderIdHash`, `latitude`, `longitude`, `timestamp`, `battery`, or `severity`.
2. **TTL Decrement**: On relay, `ttl = ttl - 1`. If incoming `ttl <= 0`, the packet MUST NOT be forwarded.
3. **Hop Count Increment**: On relay, `hopCount = hopCount + 1`.
4. **Duplicate Suppression**: Each device stores seen `messageId`s in a local database/set. If an incoming `messageId` has already been processed, it is discarded immediately without forwarding or modification.
5. **Validation**: Malformed packets or packets with invalid CRC16 checksums are dropped silently without crashing the app.
