import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Duplicate Detection Logic Test', () {
    final Set<int> seenMessageIds = {};

    bool processIncomingPacket(int messageId) {
      if (seenMessageIds.contains(messageId)) {
        // DUPLICATE DISCARDED
        return false;
      }
      seenMessageIds.add(messageId);
      return true;
    }

    test('first arrival is processed, second duplicate arrival is discarded', () {
      seenMessageIds.clear();
      const messageId = 0xA3F7C2E1;

      expect(processIncomingPacket(messageId), isTrue); // First time: Processed
      expect(processIncomingPacket(messageId), isFalse); // Second time: Duplicate Discarded
      expect(processIncomingPacket(messageId), isFalse); // Third time: Duplicate Discarded
    });

    test('different messageIds are processed independently', () {
      seenMessageIds.clear();
      const msgA = 0xA3F7C2E1;
      const msgB = 0xB81C22D1;
      const msgC = 0xC92D33E1;

      expect(processIncomingPacket(msgA), isTrue);
      expect(processIncomingPacket(msgB), isTrue);
      expect(processIncomingPacket(msgC), isTrue);
      expect(processIncomingPacket(msgA), isFalse); // Duplicate
    });
  });
}
