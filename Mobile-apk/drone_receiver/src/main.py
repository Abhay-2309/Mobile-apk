import argparse
import asyncio
import sys
import time
from datetime import datetime
from .packet_codec import PacketCodec, SosPacket
from .packet_validator import PacketValidator
from .message_store import MessageStore
from .ble_scanner import DroneBleScanner

def print_sos_receipt(packet: SosPacket):
    """Prints the exact drone SOS receipt specified in Section 27 of prompt."""
    timestamp_str = packet.received_at.strftime("%Y-%m-%dT%H:%M:%S")
    print(f"""
========================================
        MESHLINK RESCUE DRONE
========================================

SOS RECEIVED

Message ID : {packet.message_id_hex}
Sender     : {packet.sender_id_str}

Latitude   : {packet.latitude:.4f}
Longitude  : {packet.longitude:.4f}

TTL        : {packet.ttl}
Hop Count  : {packet.hop_count}

Battery    : {packet.battery}%
Severity   : {packet.severity_str}

Received At:
{timestamp_str}

========================================
""")

def process_incoming_packet(packet: SosPacket, rssi: int, store: MessageStore):
    # Step 1: Validate packet
    is_valid, reason = PacketValidator.validate(packet)
    if not is_valid:
        print(f"[REJECTED] Packet {packet.message_id_hex} invalid: {reason}")
        return

    # Step 2: Check duplicate
    if store.is_duplicate(packet.message_id):
        print(f"""
[DUPLICATE]
{packet.message_id_hex}
Already received
""")
        return

    # Step 3: Store & print receipt
    store.add_message(packet)
    print_sos_receipt(packet)

def run_simulator_mode(store: MessageStore):
    """Simulates multi-hop BLE packets being received by the drone (Section 29)."""
    print("[SIMULATOR MODE ACTIVE] Generating simulated multi-hop SOS packet chain...\n")

    # Sample Victim SOS originated at Victim Phone
    victim_sos = SosPacket(
        message_id=0xA3F7C2E1,
        sender_id_hash=0xA1B2C3,
        sender_id_str="DEV-A1B2C3",
        latitude=23.8103,
        longitude=90.4125,
        timestamp=int(time.time()),
        ttl=2,       # TTL decremented from 5 -> 4 -> 3 -> 2 after 3 relays
        hop_count=3, # Hop count incremented 0 -> 1 -> 2 -> 3
        battery=82,
        severity=2,
        crc16=0,
    )

    encoded_frame = PacketCodec.encode(victim_sos)
    print(f"[SIMULATOR] Ingesting encoded binary payload ({len(encoded_frame)} bytes)...")

    decoded_packet = PacketCodec.decode(encoded_frame)
    if decoded_packet:
        process_incoming_packet(decoded_packet, rssi=-65, store=store)

    # Test Duplicate Packet Injection
    print("\n[SIMULATOR] Injecting duplicate packet over alternate mesh path...")
    if decoded_packet:
        process_incoming_packet(decoded_packet, rssi=-70, store=store)

async def run_real_mode(store: MessageStore):
    """Runs real BLE scanner using Bleak (Section 29)."""
    def on_packet(packet: SosPacket, rssi: int):
        process_incoming_packet(packet, rssi, store)

    scanner = DroneBleScanner(callback=on_packet)
    print("[REAL BLE MODE ACTIVE] Listening for MeshLink SOS advertisements on Linux/Raspberry Pi BLE...")
    await scanner.start()

    try:
        while True:
            await asyncio.sleep(1)
    except KeyboardInterrupt:
        print("\nStopping BLE Receiver...")
        await scanner.stop()

def main():
    parser = argparse.ArgumentParser(description="MeshLink Rescue - Raspberry Pi Drone Receiver")
    parser.add_argument(
        "--mode",
        choices=["real", "simulator"],
        default="simulator",
        help="Select execution mode: 'real' (Bleak BLE hardware scanner) or 'simulator' (Binary payload test generator)",
    )
    args = parser.parse_args()

    store = MessageStore()

    if args.mode == "simulator":
        run_simulator_mode(store)
    else:
        asyncio.run(run_real_mode(store))

if __name__ == "__main__":
    main()
