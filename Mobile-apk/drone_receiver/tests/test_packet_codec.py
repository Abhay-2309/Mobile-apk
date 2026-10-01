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

def test_decode_compact_packet():
    # Compact packet data based on specification example (New Delhi)
    # Magic=4D, VersionType=11, MessageID=A3F7C2E1, SenderIDHash=123C5A16
    # Lat(28.6139)=045DBB, Lon(77.2090)=0BC7FA, Timestamp(34464)=86A0
    # TTL(5)+Hop(0)=50, Batt(85%->12)+Sev(2)=C2, CRC8=B5
    data = bytes([
        0x4D, 0x11, 
        0xA3, 0xF7, 0xC2, 0xE1, 
        0x12, 0x3C, 0x5A, 0x16,
        0x04, 0x5D, 0xBB, 
        0x0B, 0xC7, 0xFA,
        0x86, 0xA0, 
        0x50, 
        0xC2, 
        0xB5
    ])
    
    assert len(data) == 21
    
    # Needs to match current time window for test
    # We will patch time for this test or just calculate the expected full_ts
    import time
    now = int(time.time())
    base = now - (now % 65536)
    expected_full_ts = base + 0x86A0
    if expected_full_ts > now + 300:
        expected_full_ts -= 65536
        
    decoded = PacketCodec.decode(data)
    assert decoded is not None
    assert decoded.message_id == 0xA3F7C2E1
    assert decoded.sender_id_hash == 0x123C5A16
    assert abs(decoded.latitude - 28.6139) < 0.0001
    assert abs(decoded.longitude - 77.2090) < 0.0001
    assert decoded.timestamp == expected_full_ts
    assert decoded.ttl == 5
    assert decoded.hop_count == 0
    assert decoded.battery == 80 # Quantized
    assert decoded.severity == 2
    assert decoded.crc16 == 0xB5

def test_ignore_presence_packet():
    presence = bytes([0x4D, 0x50, 0x12, 0x3C, 0x5A, 0x16])
    assert PacketCodec.is_presence_packet(presence) is True
    assert PacketCodec.decode(presence) is None

def test_compact_corrupted_crc_rejection():
    data = bytearray([
        0x4D, 0x11, 
        0xA3, 0xF7, 0xC2, 0xE1, 
        0x12, 0x3C, 0x5A, 0x16,
        0x04, 0x5D, 0xBB, 
        0x0B, 0xC7, 0xFA,
        0x86, 0xA0, 
        0x50, 
        0xC2, 
        0xB5
    ])
    data[20] ^= 0x01 # Corrupt CRC8 byte
    assert PacketCodec.decode(bytes(data)) is None
