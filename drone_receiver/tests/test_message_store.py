import os
import pytest
from src.packet_codec import SosPacket
from src.message_store import MessageStore

def test_deduplication_store(tmp_path):
    test_db = os.path.join(tmp_path, "test_store.json")
    store = MessageStore(persistence_file=test_db)

    sos1 = SosPacket(
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

    # First insertion should succeed
    assert store.add_message(sos1) is True

    # Duplicate insertion should fail
    assert store.add_message(sos1) is False
    assert store.is_duplicate(0xA3F7C2E1) is True
