import pytest
import time
from src.packet_codec import PacketCodec, SosPacket

def test_encode_decode_roundtrip():
    sos = SosPacket(
        message_id=0xA3F7C2E1,
        sender_id_hash=0xA1B2C3,
        sender_id_str="DEV-A1B2C3",
        latitude=23.8103,
        longitude=90.4125,
        timestamp=1776846612,
        ttl=5,
        hop_count=0,
        battery=82,
        severity=2,
        crc16=0
    )

    encoded = PacketCodec.encode(sos)
    assert len(encoded) == 28

    decoded = PacketCodec.decode(encoded)
    assert decoded is not None
    assert decoded.message_id == 0xA3F7C2E1
    assert decoded.sender_id_hash == 0xA1B2C3
    assert abs(decoded.latitude - 23.8103) < 0.0001
    assert abs(decoded.longitude - 90.4125) < 0.0001
    assert decoded.ttl == 5
    assert decoded.hop_count == 0
    assert decoded.battery == 82
    assert decoded.severity == 2

def test_corrupted_crc_rejection():
    sos = SosPacket(
        message_id=0xA3F7C2E1,
        sender_id_hash=0xA1B2C3,
        sender_id_str="DEV-A1B2C3",
        latitude=23.8103,
        longitude=90.4125,
        timestamp=1776846612,
        ttl=5,
        hop_count=0,
        battery=82,
        severity=2,
        crc16=0
    )

    encoded = bytearray(PacketCodec.encode(sos))
    encoded[12] ^= 0xFF # Corrupt byte

    decoded = PacketCodec.decode(bytes(encoded))
    assert decoded is None
