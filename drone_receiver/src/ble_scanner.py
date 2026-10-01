import logging
from typing import Callable, Optional
from .config import MANUFACTURER_ID
from .packet_codec import PacketCodec, SosPacket

logger = logging.getLogger("DroneBleScanner")

try:
    from bleak import BleakScanner
    from bleak.backends.device import BLEDevice
    from bleak.backends.scanner import AdvertisementData
    HAS_BLEAK = True
except ImportError:
    HAS_BLEAK = False

class DroneBleScanner:

    def __init__(self, callback: Callable[[SosPacket, int], None]):
        self.callback = callback
        self.scanner = None
        self.is_scanning = False

    def _detection_callback(self, device, advertisement_data):
        mfg_data = getattr(advertisement_data, 'manufacturer_data', {})
        if MANUFACTURER_ID in mfg_data:
            payload = mfg_data[MANUFACTURER_ID]
            rssi = getattr(advertisement_data, 'rssi', -70)
            packet = PacketCodec.decode(payload)
            if packet:
                self.callback(packet, rssi)

    async def start(self):
        if not HAS_BLEAK:
            raise RuntimeError("Bleak library is not installed. Please run 'pip install bleak' or use '--mode simulator'.")

        if self.is_scanning:
            return

        logger.info("Starting Bleak BLE Scanner on Raspberry Pi / Drone...")
        self.scanner = BleakScanner(detection_callback=self._detection_callback)
        await self.scanner.start()
        self.is_scanning = True

    async def stop(self):
        if not HAS_BLEAK or not self.is_scanning or not self.scanner:
            return

        logger.info("Stopping Bleak BLE Scanner...")
        await self.scanner.stop()
        self.is_scanning = False
