import 'package:flutter/material.dart';
import '../models/sos_message.dart';

class PacketStatusCard extends StatelessWidget {
  final SosMessage sos;
  final bool isBroadcasting;

  const PacketStatusCard({
    super.key,
    required this.sos,
    required this.isBroadcasting,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 6,
      color: Colors.red.shade900.withOpacity(0.4),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: Colors.redAccent.withOpacity(0.8), width: 1.5),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'SOS ACTIVE',
                  style: TextStyle(
                    color: Colors.redAccent,
                    fontWeight: FontWeight.bold,
                    fontSize: 18,
                    letterSpacing: 1.2,
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: isBroadcasting ? Colors.green : Colors.amber,
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Text(
                    isBroadcasting ? 'BROADCASTING' : 'PAUSED',
                    style: const TextStyle(
                      color: Colors.black,
                      fontWeight: FontWeight.bold,
                      fontSize: 11,
                    ),
                  ),
                ),
              ],
            ),
            const Divider(color: Colors.white24, height: 20),
            _buildRow('Message ID', sos.messageIdHex),
            _buildRow('Sender ID', sos.senderIdStr),
            _buildRow('Coordinates', '${sos.latitude.toStringAsFixed(4)}, ${sos.longitude.toStringAsFixed(4)}'),
            _buildRow('TTL', '${sos.ttl}'),
            _buildRow('Hop Count', '${sos.hopCount}'),
            _buildRow('Severity', sos.severityLabel),
          ],
        ),
      ),
    );
  }

  Widget _buildRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.white70, fontSize: 14)),
          Text(
            value,
            style: const TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.bold,
              fontSize: 14,
              fontFamily: 'monospace',
            ),
          ),
        ],
      ),
    );
  }
}
