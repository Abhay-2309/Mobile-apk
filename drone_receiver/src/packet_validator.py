from typing import Tuple
from .packet_codec import SosPacket
from .config import MAX_TTL, MAX_HOPS, MAX_BATTERY, MAX_SEVERITY

class PacketValidator:

    @staticmethod
    def validate(packet: SosPacket) -> Tuple[bool, str]:
        """Validates structural and bounds constraints of an SosPacket."""
        if not (-90.0 <= packet.latitude <= 90.0):
            return False, f"Invalid latitude: {packet.latitude}"

        if not (-180.0 <= packet.longitude <= 180.0):
            return False, f"Invalid longitude: {packet.longitude}"

        if packet.latitude == 0.0 and packet.longitude == 0.0:
            return False, "Victim coordinates unavailable (0.0, 0.0)"

        if not (0 <= packet.ttl <= MAX_TTL):
            return False, f"Invalid TTL: {packet.ttl}"

        if not (0 <= packet.hop_count <= MAX_HOPS):
            return False, f"Invalid Hop Count: {packet.hop_count}"

        if not (0 <= packet.battery <= MAX_BATTERY):
            return False, f"Invalid battery percentage: {packet.battery}"

        if not (0 <= packet.severity <= MAX_SEVERITY):
            return False, f"Invalid severity level: {packet.severity}"

        if packet.message_id == 0:
            return False, "Invalid Message ID (0)"

        return True, "VALID"
