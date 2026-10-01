import 'dart:typed_data';
import '../core/constants/mesh_constants.dart';
import '../core/utils/crc16.dart';
import '../models/sos_message.dart';

class PacketService {
  /// Encodes an SosMessage object into a 28-byte binary Uint8List frame.
  Uint8List encodePacket(SosMessage sos) {
    final bd = ByteData(MeshConstants.packetSizeBytes);

    // Byte 0: Magic Header 0x4D
    bd.setUint8(0, MeshConstants.magicHeader);

    // Byte 1: Version (high 4 bits) | Type (low 4 bits)
    int verAndType = ((MeshConstants.protocolVersion & 0x0F) << 4) |
        (MeshConstants.packetTypeSos & 0x0F);
    bd.setUint8(1, verAndType);

    // Bytes 2-5: Message ID (uint32)
    bd.setUint32(2, sos.messageId & 0xFFFFFFFF, Endian.big);

    // Bytes 6-9: Sender ID Hash (uint32)
    bd.setUint32(6, sos.senderIdHash & 0xFFFFFFFF, Endian.big);

    // Bytes 10-13: Latitude (int32)
    bd.setInt32(10, (sos.latitude * 1000000).round(), Endian.big);

    // Bytes 14-17: Longitude (int32)
    bd.setInt32(14, (sos.longitude * 1000000).round(), Endian.big);

    // Bytes 18-21: Timestamp (uint32)
    bd.setUint32(18, sos.timestamp & 0xFFFFFFFF, Endian.big);

    // Byte 22: TTL
    bd.setUint8(22, sos.ttl & 0xFF);

    // Byte 23: Hop Count
    bd.setUint8(23, sos.hopCount & 0xFF);

    // Byte 24: Battery
    bd.setUint8(24, sos.battery & 0xFF);

    // Byte 25: Severity
    bd.setUint8(25, sos.severity & 0xFF);

    // Calculate CRC16 over first 26 bytes
    final bytesForCrc = bd.buffer.asUint8List(0, 26);
    int crc = Crc16.calculate(bytesForCrc);

    // Bytes 26-27: CRC16 (uint16)
    bd.setUint16(26, crc & 0xFFFF, Endian.big);

    return bd.buffer.asUint8List();
  }

  /// Decodes a 28-byte Uint8List frame into an SosMessage object. Returns null if invalid.
  SosMessage? decodePacket(Uint8List bytes) {
    if (bytes.length < MeshConstants.packetSizeBytes) {
      return null;
    }

    final bd = ByteData.sublistView(bytes, 0, MeshConstants.packetSizeBytes);

    // Validate Magic Header
    final magic = bd.getUint8(0);
    if (magic != MeshConstants.magicHeader) {
      return null;
    }

    // Validate Type
    final verAndType = bd.getUint8(1);
    final type = verAndType & 0x0F;
    if (type != MeshConstants.packetTypeSos) {
      return null;
    }

    // Read Fields
    final messageId = bd.getUint32(2, Endian.big);
    final senderIdHash = bd.getUint32(6, Endian.big);
    final latInt = bd.getInt32(10, Endian.big);
    final lonInt = bd.getInt32(14, Endian.big);
    final timestamp = bd.getUint32(18, Endian.big);
    final ttl = bd.getUint8(22);
    final hopCount = bd.getUint8(23);
    final battery = bd.getUint8(24);
    final severity = bd.getUint8(25);

    // CRC Validation
    final expectedCrc = bd.getUint16(26, Endian.big);
    final actualCrc = Crc16.calculate(bytes.sublist(0, 26));

    if (expectedCrc != actualCrc) {
      return null; // CRC check failed
    }

    // Validation rules per Prompt Section 13:
    if (latitudeOutOfRange(latInt / 1000000.0) ||
        longitudeOutOfRange(lonInt / 1000000.0)) {
      return null;
    }
    if (ttl > 10 || hopCount > 100 || battery > 100 || severity > 2) {
      return null;
    }

    return SosMessage(
      messageId: messageId,
      senderIdHash: senderIdHash,
      senderIdStr: 'DEV-${senderIdHash.toRadixString(16).toUpperCase().padLeft(6, '0')}',
      latitude: latInt / 1000000.0,
      longitude: lonInt / 1000000.0,
      timestamp: timestamp,
      ttl: ttl,
      hopCount: hopCount,
      battery: battery,
      severity: severity,
    );
  }

  bool latitudeOutOfRange(double lat) => lat < -90.0 || lat > 90.0;
  bool longitudeOutOfRange(double lon) => lon < -180.0 || lon > 180.0;
}
