import pytest
from src.packet_codec import SosPacket
from src.packet_validator import PacketValidator

def test_valid_packet():
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
    is_valid, reason = PacketValidator.validate(sos)
    assert is_valid is True
    assert reason == "VALID"

def test_invalid_coordinates():
    sos = SosPacket(
        message_id=0xA3F7C2E1,
        sender_id_hash=0xA1B2C3,
        sender_id_str="DEV-A1B2C3",
        latitude=95.0, # Invalid lat > 90
        longitude=90.4125,
        timestamp=1776846612,
        ttl=5,
        hop_count=0,
        battery=82,
        severity=2,
        crc16=0
    )
    is_valid, reason = PacketValidator.validate(sos)
    assert is_valid is False
    assert "latitude" in reason
