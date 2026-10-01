import 'dart:typed_data';

/// CRC16-CCITT Implementation in Pure Dart.
/// Uses Polynomial 0x1021 with Initial Value 0xFFFF.
class Crc16 {
  static int calculate(Uint8List data) {
    int crc = 0xFFFF;
    const int polynomial = 0x1021;

    for (int byte in data) {
      for (int i = 0; i < 8; i++) {
        bool bit = ((byte >> (7 - i)) & 1) == 1;
        bool c15 = ((crc >> 15) & 1) == 1;
        crc = (crc << 1) & 0xFFFF;
        if (c15 ^ bit) {
          crc ^= polynomial;
        }
      }
    }
    return crc & 0xFFFF;
  }
}
