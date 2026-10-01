import 'dart:typed_data';
import 'package:flutter_test/flutter_test.dart';
import 'package:meshlink_mobile/models/sos_message.dart';
import 'package:meshlink_mobile/services/packet_service.dart';

class SimulatedNode {
  final String name;
  final Set<int> seenMessages = {};
  final PacketService codec = PacketService();
  SosMessage? lastProcessedMessage;
  Uint8List? lastOutboundFrame;

  SimulatedNode(this.name);

  Uint8List? receiveAndRelay(Uint8List frame) {
    final sos = codec.decodePacket(frame);
    if (sos == null) return null;

    if (seenMessages.contains(sos.messageId)) {
      // Duplicate discarded
      return null;
    }

    seenMessages.add(sos.messageId);
    lastProcessedMessage = sos;

    if (sos.ttl <= 0) {
      // TTL expired, do not relay
      return null;
    }

    final relaySos = sos.copyWith(
      ttl: sos.ttl - 1,
      hopCount: sos.hopCount + 1,
    );

    lastOutboundFrame = codec.encodePacket(relaySos);
    return lastOutboundFrame;
  }
}

void main() {
  group('Mesh Simulator End-to-End Chain Test (Section 33)', () {
    test('Phone A -> Phone B -> Phone C -> Drone multi-hop relay simulation', () {
      final phoneA = SimulatedNode('Phone A');
      final phoneB = SimulatedNode('Phone B');
      final phoneC = SimulatedNode('Phone C');
      final drone = SimulatedNode('Rescue Drone');

      final initialSos = SosMessage(
        messageId: 0xA3F7C2E1,
        senderIdHash: 0x11223344,
        senderIdStr: 'DEV-A1B2C3',
        latitude: 23.8103,
        longitude: 90.4125,
        timestamp: 1776846612,
        ttl: 5,
        hopCount: 0,
        battery: 82,
        severity: 2,
      );

      // Phone A originates packet
      final frameFromA = phoneA.codec.encodePacket(initialSos);

      // Phone B receives from A and relays
      final frameFromB = phoneB.receiveAndRelay(frameFromA);
      expect(frameFromB, isNotNull);
      expect(phoneB.lastProcessedMessage!.ttl, equals(5));
      expect(phoneB.lastProcessedMessage!.hopCount, equals(0));

      // Phone C receives from B and relays
      final frameFromC = phoneC.receiveAndRelay(frameFromB!);
      expect(frameFromC, isNotNull);
      expect(phoneC.lastProcessedMessage!.ttl, equals(4));
      expect(phoneC.lastProcessedMessage!.hopCount, equals(1));

      // Drone receives from C
      final frameFromDrone = drone.receiveAndRelay(frameFromC!);
      expect(phoneC.lastOutboundFrame, isNotNull);
      expect(drone.lastProcessedMessage!.ttl, equals(3));
      expect(drone.lastProcessedMessage!.hopCount, equals(2));

      // Verify invariant preserve rule:
      expect(drone.lastProcessedMessage!.messageId, equals(0xA3F7C2E1));
      expect(drone.lastProcessedMessage!.senderIdHash, equals(0x11223344));
      expect((drone.lastProcessedMessage!.latitude - 23.8103).abs(), lessThan(0.0001));
      expect((drone.lastProcessedMessage!.longitude - 90.4125).abs(), lessThan(0.0001));
      expect(drone.lastProcessedMessage!.battery, equals(82));
    });

    test('Duplicate packet over alternate path is suppressed', () {
      final phoneA = SimulatedNode('Phone A');
      final phoneB = SimulatedNode('Phone B');
      final phoneC = SimulatedNode('Phone C');

      final initialSos = SosMessage(
        messageId: 0xA3F7C2E1,
        senderIdHash: 0x11223344,
        senderIdStr: 'DEV-A1B2C3',
        latitude: 23.8103,
        longitude: 90.4125,
        timestamp: 1776846612,
        ttl: 5,
        hopCount: 0,
        battery: 82,
        severity: 2,
      );

      final frameFromA = phoneA.codec.encodePacket(initialSos);

      // Path 1: A -> B
      final frameFromB = phoneB.receiveAndRelay(frameFromA);
      expect(frameFromB, isNotNull);

      // Path 2: A -> C -> B (B receives same messageId again)
      final frameFromC = phoneC.receiveAndRelay(frameFromA);
      expect(frameFromC, isNotNull);

      final duplicateRelayFromB = phoneB.receiveAndRelay(frameFromC!);
      expect(duplicateRelayFromB, isNull); // Suppressed by Phone B!
    });
  });
}
