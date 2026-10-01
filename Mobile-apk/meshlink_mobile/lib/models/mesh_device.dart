class MeshDevice {
  final String deviceId;
  final String deviceName;
  final int rssi;
  final DateTime lastSeen;

  MeshDevice({
    required this.deviceId,
    required this.deviceName,
    required this.rssi,
    DateTime? lastSeen,
  }) : lastSeen = lastSeen ?? DateTime.now();
}
