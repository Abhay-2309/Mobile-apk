import '../core/constants/mesh_constants.dart';

class SosMessage {
  final int messageId; // uint32
  final int senderIdHash; // uint32
  final String senderIdStr;
  final double latitude;
  final double longitude;
  final int timestamp;
  final int ttl;
  final int hopCount;
  final int battery;
  final int severity;
  final DateTime receivedAt;

  SosMessage({
    required this.messageId,
    required this.senderIdHash,
    required this.senderIdStr,
    required this.latitude,
    required this.longitude,
    required this.timestamp,
    required this.ttl,
    required this.hopCount,
    required this.battery,
    required this.severity,
    DateTime? receivedAt,
  }) : receivedAt = receivedAt ?? DateTime.now();

  String get messageIdHex =>
      '0x${messageId.toRadixString(16).toUpperCase().padLeft(8, '0')}';

  String get severityLabel => MeshConstants.severityToString(severity);

  SosMessage copyWith({
    int? ttl,
    int? hopCount,
  }) {
    return SosMessage(
      messageId: messageId,
      senderIdHash: senderIdHash,
      senderIdStr: senderIdStr,
      latitude: latitude,
      longitude: longitude,
      timestamp: timestamp,
      ttl: ttl ?? this.ttl,
      hopCount: hopCount ?? this.hopCount,
      battery: battery,
      severity: severity,
      receivedAt: receivedAt,
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'messageId': messageId,
      'senderIdHash': senderIdHash,
      'senderIdStr': senderIdStr,
      'latitude': latitude,
      'longitude': longitude,
      'timestamp': timestamp,
      'ttl': ttl,
      'hopCount': hopCount,
      'battery': battery,
      'severity': severity,
      'receivedAt': receivedAt.toIso8601String(),
    };
  }

  factory SosMessage.fromMap(Map<String, dynamic> map) {
    return SosMessage(
      messageId: map['messageId'] as int,
      senderIdHash: map['senderIdHash'] as int,
      senderIdStr: map['senderIdStr'] as String? ?? 'DEV-UNKNOWN',
      latitude: (map['latitude'] as num).toDouble(),
      longitude: (map['longitude'] as num).toDouble(),
      timestamp: map['timestamp'] as int,
      ttl: map['ttl'] as int,
      hopCount: map['hopCount'] as int,
      battery: map['battery'] as int,
      severity: map['severity'] as int,
      receivedAt: map['receivedAt'] != null
          ? DateTime.parse(map['receivedAt'] as String)
          : DateTime.now(),
    );
  }
}
