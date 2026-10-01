import 'package:flutter/material.dart';

class MeshStatusCard extends StatelessWidget {
  final bool isMeshActive;
  final bool isBleReady;
  final bool isGpsReady;
  final int batteryLevel;

  const MeshStatusCard({
    super.key,
    required this.isMeshActive,
    required this.isBleReady,
    required this.isGpsReady,
    required this.batteryLevel,
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
              children: [
                Container(
                  width: 12,
                  height: 12,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: isMeshActive ? Colors.greenAccent : Colors.redAccent,
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  isMeshActive ? 'Mesh Ready' : 'Mesh Inactive',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            const Divider(color: Colors.white24, height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: [
                _buildStatusItem(
                  icon: Icons.bluetooth,
                  label: 'BLE',
                  value: isBleReady ? 'Active' : 'Disabled',
                  isOk: isBleReady,
                ),
                _buildStatusItem(
                  icon: Icons.gps_fixed,
                  label: 'GPS',
                  value: isGpsReady ? 'Ready' : 'Searching',
                  isOk: isGpsReady,
                ),
                _buildStatusItem(
                  icon: Icons.battery_charging_full,
                  label: 'Battery',
                  value: '$batteryLevel%',
                  isOk: batteryLevel > 20,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildStatusItem({
    required IconData icon,
    required String label,
    required String value,
    required bool isOk,
  }) {
    return Column(
      children: [
        Icon(icon, color: isOk ? Colors.cyanAccent : Colors.amberAccent, size: 24),
        const SizedBox(height: 4),
        Text(
          label,
          style: const TextStyle(color: Colors.white70, fontSize: 12),
        ),
        Text(
          value,
          style: TextStyle(
            color: isOk ? Colors.white : Colors.amberAccent,
            fontWeight: FontWeight.bold,
            fontSize: 14,
          ),
        ),
      ],
    );
  }
}
