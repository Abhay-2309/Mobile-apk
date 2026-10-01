class MeshConstants {
  static const String appName = 'MeshLink Rescue';
  
  // Protocol Specifications
  static const int magicHeader = 0x4D; // 'M'
  static const int protocolVersion = 0x01;
  static const int packetTypeSos = 0x01;
  static const int defaultTtl = 5;
  static const int defaultHopCount = 0;
  static const int manufacturerId = 0x4D4C; // "ML"
  static const int packetSizeBytes = 28;

  // Platform Channel Names
  static const String channelName = 'com.meshlink.ble/channel';
  static const String eventChannelName = 'com.meshlink.ble/events';

  // Local Storage Keys
  static const String keyDeviceId = 'meshlink_device_id';
  static const String keyRelayEnabled = 'meshlink_relay_enabled';
  
  // Severity Levels
  static const int severityLow = 0;
  static const int severityMedium = 1;
  static const int severityCritical = 2;

  static String severityToString(int severity) {
    switch (severity) {
      case severityLow:
        return 'LOW';
      case severityMedium:
        return 'MEDIUM';
      case severityCritical:
      default:
        return 'CRITICAL';
    }
  }
}
