class MeshException implements Exception {
  final String message;
  final String? code;
  final dynamic details;

  MeshException(this.message, {this.code, this.details});

  @override
  String toString() => 'MeshException[$code]: $message';
}

class InvalidPacketException extends MeshException {
  InvalidPacketException(String reason)
      : super('Invalid SOS Packet: $reason', code: 'INVALID_PACKET');
}

class DuplicatePacketException extends MeshException {
  final String messageIdHex;
  DuplicatePacketException(this.messageIdHex)
      : super('Duplicate SOS Discarded: $messageIdHex', code: 'DUPLICATE_PACKET');
}
