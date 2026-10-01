import json
import os
from typing import Dict, Optional
from .packet_codec import SosPacket

class MessageStore:

    def __init__(self, persistence_file: str = "drone_messages.json"):
        self.persistence_file = persistence_file
        self.seen_messages: Dict[int, SosPacket] = {}
        self._load()

    def is_duplicate(self, message_id: int) -> bool:
        return message_id in self.seen_messages

    def add_message(self, packet: SosPacket) -> bool:
        """Adds packet if not a duplicate. Returns True if added, False if duplicate."""
        if self.is_duplicate(packet.message_id):
            return False

        self.seen_messages[packet.message_id] = packet
        self._save()
        return True

    def get_message(self, message_id: int) -> Optional[SosPacket]:
        return self.seen_messages.get(message_id)

    def _save(self):
        try:
            data = {}
            for msg_id, packet in self.seen_messages.items():
                data[str(msg_id)] = {
                    "message_id": packet.message_id,
                    "message_id_hex": packet.message_id_hex,
                    "sender_id_hash": packet.sender_id_hash,
                    "sender_id_str": packet.sender_id_str,
                    "latitude": packet.latitude,
                    "longitude": packet.longitude,
                    "timestamp": packet.timestamp,
                    "ttl": packet.ttl,
                    "hop_count": packet.hop_count,
                    "battery": packet.battery,
                    "severity": packet.severity_str,
                    "received_at": packet.received_at.isoformat(),
                }
            with open(self.persistence_file, "w") as f:
                json.dump(data, f, indent=2)
        except Exception as e:
            print(f"[STORE ERROR] Failed to save messages to file: {e}")

    def _load(self):
        if not os.path.exists(self.persistence_file):
            return
        try:
            with open(self.persistence_file, "r") as f:
                data = json.load(f)
                # Store message IDs in memory for duplicate suppression
                for msg_id_str in data.keys():
                    self.seen_messages[int(msg_id_str)] = None
        except Exception as e:
            print(f"[STORE WARNING] Could not load previous state: {e}")
