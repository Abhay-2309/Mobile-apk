import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:meshlink_mobile/models/sos_message.dart';
import 'package:meshlink_mobile/services/packet_service.dart';

void main() {
  group('PacketCodec Unit Tests', () {
    final packetService = PacketService();

    test('encode and decode preserves exact data', () {
      final sos = SosMessage(
        messageId: 0xA3F7C2E1,
        senderIdHash: 0x12345678,
        senderIdStr: 'DEV-123456',
        latitude: 23.8103,
        longitude: 90.4125,
        timestamp: 1776846612,
        ttl: 5,
        hopCount: 0,
        battery: 82,
        severity: 2,
      );

      final encoded = packetService.encodePacket(sos);
      expect(encoded.length, equals(28));

      final decoded = packetService.decodePacket(encoded);
      expect(decoded, isNotNull);
      expect(decoded!.messageId, equals(0xA3F7C2E1));
      expect(decoded.senderIdHash, equals(0x12345678));
      expect((decoded.latitude - 23.8103).abs(), lessThan(0.0001));
      expect((decoded.longitude - 90.4125).abs(), lessThan(0.0001));
      expect(decoded.timestamp, equals(1776846612));
      expect(decoded.ttl, equals(5));
      expect(decoded.hopCount, equals(0));
      expect(decoded.battery, equals(82));
      expect(decoded.severity, equals(2));
    });

    test('rejects corrupted CRC bytes', () {
      final sos = SosMessage(
        messageId: 0xA3F7C2E1,
        senderIdHash: 0x12345678,
        senderIdStr: 'DEV-123456',
        latitude: 23.8103,
        longitude: 90.4125,
        timestamp: 1776846612,
        ttl: 5,
        hopCount: 0,
        battery: 82,
        severity: 2,
      );

      final encoded = packetService.encodePacket(sos);
      final corrupted = Uint8List.fromList(encoded);
      corrupted[10] ^= 0xFF; // Corrupt latitude byte

      final decoded = packetService.decodePacket(corrupted);
      expect(decoded, isNull);
    });

    test('rejects invalid magic header', () {
      final sos = SosMessage(
        messageId: 0xA3F7C2E1,
        senderIdHash: 0x12345678,
        senderIdStr: 'DEV-123456',
        latitude: 23.8103,
        longitude: 90.4125,
        timestamp: 1776846612,
        ttl: 5,
        hopCount: 0,
        battery: 82,
        severity: 2,
      );

      final encoded = packetService.encodePacket(sos);
      final corrupted = Uint8List.fromList(encoded);
      corrupted[0] = 0x00; // Invalid magic byte

      final decoded = packetService.decodePacket(corrupted);
      expect(decoded, isNull);
    });
  });
}
