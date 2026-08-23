import 'package:flutter_test/flutter_test.dart';
import 'package:meshlink_mobile/models/sos_message.dart';

void main() {
  group('SosMessage & Protocol Rules Unit Tests', () {
    test('Rule 4 & Rule 7: Relay decrements TTL and increments hop count', () {
      final initialSos = SosMessage(
        messageId: 0xA3F7C2E1,
        senderIdHash: 0x12345678,
        senderIdStr: 'DEV-VICTIM',
        latitude: 23.8103,
        longitude: 90.4125,
        timestamp: 1000,
        ttl: 5,
        hopCount: 0,
        battery: 85,
        severity: 2,
      );

      // Hop 1 (Relay B)
      final hop1 = initialSos.copyWith(
        ttl: initialSos.ttl - 1,
        hopCount: initialSos.hopCount + 1,
      );
      expect(hop1.ttl, equals(4));
      expect(hop1.hopCount, equals(1));
      expect(hop1.messageId, equals(0xA3F7C2E1)); // Preserve identity
      expect(hop1.senderIdStr, equals('DEV-VICTIM')); // Preserve victim ID

      // Hop 2 (Relay C)
      final hop2 = hop1.copyWith(
        ttl: hop1.ttl - 1,
        hopCount: hop1.hopCount + 1,
      );
      expect(hop2.ttl, equals(3));
      expect(hop2.hopCount, equals(2));

      // Hop 5 (TTL becomes 0)
      var current = initialSos;
      for (int i = 0; i < 5; i++) {
        current = current.copyWith(
          ttl: current.ttl - 1,
          hopCount: current.hopCount + 1,
        );
      }
      expect(current.ttl, equals(0));
      expect(current.hopCount, equals(5));
    });
  });
}
