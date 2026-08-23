import struct
from dataclasses import dataclass
from datetime import datetime
from typing import Optional
from .config import (
    MAGIC_HEADER,
    PACKET_TYPE_SOS,
    PROTOCOL_VERSION,
    PACKET_SIZE_BYTES,
)

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
    received_at: datetime = datetime.now()

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
    def decode(data: bytes) -> Optional[SosPacket]:
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
