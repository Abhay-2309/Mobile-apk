import 'package:flutter/material.dart';

class RelayStatusCard extends StatelessWidget {
  final bool isRelayActive;
  final ValueChanged<bool> onRelayToggle;
  final int received;
  final int forwarded;
  final int duplicates;
  final int expired;

  const RelayStatusCard({
    super.key,
    required this.isRelayActive,
    required this.onRelayToggle,
    required this.received,
    required this.forwarded,
    required this.duplicates,
    required this.expired,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      elevation: 4,
      color: Colors.grey.shade900,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.router_rounded,
                      color: isRelayActive ? Colors.cyanAccent : Colors.grey,
                    ),
                    const SizedBox(width: 8),
                    const Text(
                      'MESH RELAY',
                      style: TextStyle(
                        color: Colors.white,
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                Switch(
                  value: isRelayActive,
                  onChanged: onRelayToggle,
                  activeColor: Colors.cyanAccent,
                ),
              ],
            ),
            const Divider(color: Colors.white24, height: 20),
            GridView.count(
              crossAxisCount: 2,
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              childAspectRatio: 2.2,
              crossAxisSpacing: 10,
              mainAxisSpacing: 10,
              children: [
                _buildStatTile('Messages Received', '$received', Colors.blue),
                _buildStatTile('Messages Forwarded', '$forwarded', Colors.green),
                _buildStatTile('Duplicates Ignored', '$duplicates', Colors.amber),
                _buildStatTile('Messages Expired', '$expired', Colors.redAccent),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatTile(String title, String value, Color color) {
    return Container(
      padding: const EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: color.withOpacity(0.15),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withOpacity(0.4)),
      ),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: const TextStyle(color: Colors.white70, fontSize: 11),
          ),
          const SizedBox(height: 2),
          Text(
            value,
            style: TextStyle(
              color: color,
              fontSize: 18,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }
}
