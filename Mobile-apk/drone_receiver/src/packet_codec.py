import struct
import time
from dataclasses import dataclass
from datetime import datetime
from typing import Optional
from .config import (
    MAGIC_HEADER,
    PACKET_TYPE_SOS,
    PROTOCOL_VERSION,
    PACKET_SIZE_BYTES,
)

COMPACT_PACKET_SIZE = 21

@dataclass
class SosPacket:
    message_id: int
    sender_id_hash: int
    sender_id_str: str
    latitude: float
    longitude: float
    timestamp: int
    ttl: int
    hop_count: int
    battery: int
    severity: int
    crc16: int
    received_at: datetime = None

    def __post_init__(self):
        if self.received_at is None:
            self.received_at = datetime.now()

    @property
    def message_id_hex(self) -> str:
        return f"0x{self.message_id:08X}"

    @property
    def severity_str(self) -> str:
        mapping = {0: "LOW", 1: "MEDIUM", 2: "CRITICAL"}
        return mapping.get(self.severity, "CRITICAL")


def calculate_crc16(data: bytes) -> int:
    """Calculates CRC16-CCITT (Polynomial 0x1021, Init 0xFFFF)."""
    crc = 0xFFFF
    polynomial = 0x1021

    for byte in data:
        for i in range(8):
            bit = ((byte >> (7 - i)) & 1) == 1
            c15 = ((crc >> 15) & 1) == 1
            crc = (crc << 1) & 0xFFFF
            if c15 ^ bit:
                crc ^= polynomial

    return crc & 0xFFFF

def calculate_crc8(data: bytes) -> int:
    """Calculates CRC8-CCITT (Polynomial 0x07, Init 0x00)."""
    crc = 0x00
    for byte in data:
        crc ^= byte
        for _ in range(8):
            if crc & 0x80:
                crc = ((crc << 1) ^ 0x07) & 0xFF
            else:
                crc = (crc << 1) & 0xFF
    return crc


class PacketCodec:

    @staticmethod
    def encode(packet: SosPacket) -> bytes:
        """Encodes an SosPacket into a 28-byte binary frame."""
        ver_and_type = ((PROTOCOL_VERSION & 0x0F) << 4) | (PACKET_TYPE_SOS & 0x0F)
        lat_int = int(round(packet.latitude * 1_000_000))
        lon_int = int(round(packet.longitude * 1_000_000))

        # First 26 bytes without CRC
        header_bytes = struct.pack(
            ">BBIIiiIBBBB",
            MAGIC_HEADER,
            ver_and_type,
            packet.message_id & 0xFFFFFFFF,
            packet.sender_id_hash & 0xFFFFFFFF,
            lat_int,
            lon_int,
            packet.timestamp & 0xFFFFFFFF,
            packet.ttl & 0xFF,
            packet.hop_count & 0xFF,
            packet.battery & 0xFF,
            packet.severity & 0xFF,
        )

        crc = calculate_crc16(header_bytes)
        crc_bytes = struct.pack(">H", crc)

        return header_bytes + crc_bytes

    @staticmethod
    def decode_legacy(data: bytes) -> Optional[SosPacket]:
        """Decodes a 28-byte binary frame into an SosPacket. Returns None if invalid/corrupt."""
        if len(data) < PACKET_SIZE_BYTES:
            return None

        data = data[:PACKET_SIZE_BYTES]

        magic = data[0]
        if magic != MAGIC_HEADER:
            return None

        ver_and_type = data[1]
        pkt_type = ver_and_type & 0x0F
        if pkt_type != PACKET_TYPE_SOS:
            return None

        expected_crc = struct.unpack(">H", data[26:28])[0]
        actual_crc = calculate_crc16(data[:26])

        if expected_crc != actual_crc:
            return None  # Checksum mismatch

        (
            _,
            _,
            message_id,
            sender_id_hash,
            lat_int,
            lon_int,
            timestamp,
            ttl,
            hop_count,
            battery,
            severity,
        ) = struct.unpack(">BBIIiiIBBBB", data[:26])

        return SosPacket(
            message_id=message_id,
            sender_id_hash=sender_id_hash,
            sender_id_str=f"DEV-{sender_id_hash:06X}",
            latitude=lat_int / 1_000_000.0,
            longitude=lon_int / 1_000_000.0,
            timestamp=timestamp,
            ttl=ttl,
            hop_count=hop_count,
            battery=battery,
            severity=severity,
            crc16=expected_crc,
        )

    @staticmethod
    def decode_compact(data: bytes) -> Optional[SosPacket]:
        """Decodes a 21-byte compact SOS packet. Returns None if invalid/corrupt."""
        if len(data) < COMPACT_PACKET_SIZE:
            return None

        # Byte 0: Magic
        if data[0] != MAGIC_HEADER:
            return None

        # Byte 1: Version + Type
        if (data[1] & 0x0F) != PACKET_TYPE_SOS:
            return None

        # CRC8 validation
        expected_crc = data[20]
        actual_crc = calculate_crc8(data[0:20])
        if expected_crc != actual_crc:
            return None

        # Bytes 2–5: Message ID (uint32 BE)
        message_id = struct.unpack(">I", data[2:6])[0]

        # Bytes 6–9: Sender ID Hash (uint32 BE)
        sender_id_hash = struct.unpack(">I", data[6:10])[0]

        # Bytes 10–12: Latitude (int24 BE)
        lat_raw = (data[10] << 16) | (data[11] << 8) | data[12]
        if lat_raw >= 0x800000:
            lat_raw -= 0x1000000
        latitude = lat_raw / 10000.0

        # Bytes 13–15: Longitude (int24 BE)
        lon_raw = (data[13] << 16) | (data[14] << 8) | data[15]
        if lon_raw >= 0x800000:
            lon_raw -= 0x1000000
        longitude = lon_raw / 10000.0

        # Bytes 16–17: Timestamp (uint16 BE)
        ts_low = (data[16] << 8) | data[17]

        # Reconstruct full timestamp
        now = int(time.time())
        base = now - (now % 65536)
        full_ts = base + ts_low
        if full_ts > now + 300:
            full_ts -= 65536

        # Byte 18: TTL (high nibble) | Hop Count (low nibble)
        ttl = data[18] >> 4
        hop_count = data[18] & 0x0F

        # Byte 19: Battery (high nibble) | Severity (low nibble)
        battery4 = data[19] >> 4
        severity = data[19] & 0x0F
        battery_percent = battery4 * 100 // 15

        return SosPacket(
            message_id=message_id,
            sender_id_hash=sender_id_hash,
            sender_id_str=f"DEV-{sender_id_hash:06X}",
            latitude=latitude,
            longitude=longitude,
            timestamp=full_ts,
            ttl=ttl,
            hop_count=hop_count,
            battery=battery_percent,
            severity=severity,
            crc16=expected_crc, # we store crc8 here for compatibility
        )

    @staticmethod
    def is_presence_packet(data: bytes) -> bool:
        """Returns True if this is a 6-byte presence packet."""
        return len(data) >= 6 and data[0] == 0x4D and data[1] == 0x50

    @staticmethod
    def decode(data: bytes) -> Optional[SosPacket]:
        """Try decoding as legacy first, then compact. Ignore presence packets."""
        if PacketCodec.is_presence_packet(data):
            return None
        
        if len(data) >= PACKET_SIZE_BYTES:
            packet = PacketCodec.decode_legacy(data)
            if packet:
                return packet
                
        if len(data) >= COMPACT_PACKET_SIZE:
            return PacketCodec.decode_compact(data)
            
        return None
