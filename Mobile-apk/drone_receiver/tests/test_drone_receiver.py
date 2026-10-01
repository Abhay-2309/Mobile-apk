import unittest
import os
import sys

# Ensure drone_receiver root is in sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from src.packet_codec import PacketCodec, SosPacket
from src.packet_validator import PacketValidator
from src.message_store import MessageStore

class TestPacketCodec(unittest.TestCase):
    def test_encode_decode_roundtrip(self):
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
        self.assertEqual(len(encoded), 28)

        decoded = PacketCodec.decode(encoded)
        self.assertIsNotNone(decoded)
        self.assertEqual(decoded.message_id, 0xA3F7C2E1)
        self.assertEqual(decoded.sender_id_hash, 0xA1B2C3)
        self.assertAlmostEqual(decoded.latitude, 23.8103, places=4)
        self.assertAlmostEqual(decoded.longitude, 90.4125, places=4)
        self.assertEqual(decoded.ttl, 5)
        self.assertEqual(decoded.hop_count, 0)
        self.assertEqual(decoded.battery, 82)
        self.assertEqual(decoded.severity, 2)

    def test_corrupted_crc_rejection(self):
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
        encoded[12] ^= 0xFF  # Corrupt byte

        decoded = PacketCodec.decode(bytes(encoded))
        self.assertIsNone(decoded)

class TestPacketValidator(unittest.TestCase):
    def test_valid_packet(self):
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
        self.assertTrue(is_valid)
        self.assertEqual(reason, "VALID")

    def test_invalid_coordinates(self):
        sos = SosPacket(
            message_id=0xA3F7C2E1,
            sender_id_hash=0xA1B2C3,
            sender_id_str="DEV-A1B2C3",
            latitude=95.0,  # Invalid lat > 90
            longitude=90.4125,
            timestamp=1776846612,
            ttl=5,
            hop_count=0,
            battery=82,
            severity=2,
            crc16=0
        )
        is_valid, reason = PacketValidator.validate(sos)
        self.assertFalse(is_valid)
        self.assertIn("latitude", reason)

class TestMessageStore(unittest.TestCase):
    def test_deduplication_store(self):
        test_db = "test_drone_messages.json"
        if os.path.exists(test_db):
            os.remove(test_db)

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

        # First insertion
        self.assertTrue(store.add_message(sos1))

        # Duplicate insertion
        self.assertFalse(store.add_message(sos1))
        self.assertTrue(store.is_duplicate(0xA3F7C2E1))

        if os.path.exists(test_db):
            os.remove(test_db)

if __name__ == "__main__":
    unittest.main()
