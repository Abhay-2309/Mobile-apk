enum RelayStatus {
  received,
  forwarded,
  duplicateIgnored,
  expiredTtl,
  invalidPacket,
}

class RelayResult {
  final String messageIdHex;
  final RelayStatus status;
  final int inputTtl;
  final int outputTtl;
  final int inputHops;
  final int outputHops;
  final DateTime timestamp;
  final String? details;

  RelayResult({
    required this.messageIdHex,
    required this.status,
    required this.inputTtl,
    required this.outputTtl,
    required this.inputHops,
    required this.outputHops,
    DateTime? timestamp,
    this.details,
  }) : timestamp = timestamp ?? DateTime.now();
}
