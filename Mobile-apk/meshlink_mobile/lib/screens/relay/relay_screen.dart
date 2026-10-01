import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../services/mesh_service.dart';
import '../../widgets/relay_status_card.dart';

class RelayScreen extends StatelessWidget {
  const RelayScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final meshService = context.watch<MeshService>();

    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        title: const Text('Mesh Relay Node', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: const Color(0xFF1E293B),
        centerTitle: true,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            RelayStatusCard(
              isRelayActive: meshService.isRelayEnabled,
              onRelayToggle: (val) => meshService.toggleRelay(val),
              received: meshService.receivedCount,
              forwarded: meshService.forwardedCount,
              duplicates: meshService.duplicateCount,
              expired: meshService.expiredCount,
            ),
            const SizedBox(height: 20),
            const Text(
              'RELAID PACKETS HISTORY',
              style: TextStyle(
                color: Colors.white70,
                fontSize: 14,
                fontWeight: FontWeight.bold,
                letterSpacing: 1.2,
              ),
            ),
            const SizedBox(height: 10),
            Expanded(
              child: meshService.relayHistory.isEmpty
                  ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.radar, size: 48, color: Colors.white24),
                          const SizedBox(height: 12),
                          const Text(
                            'Scanning for nearby BLE SOS packets...',
                            style: TextStyle(color: Colors.white38),
                          ),
                        ],
                      ),
                    )
                  : ListView.builder(
                      itemCount: meshService.relayHistory.length,
                      itemBuilder: (context, index) {
                        final log = meshService.relayHistory[index];
                        return Card(
                          color: const Color(0xFF1E293B),
                          margin: const EdgeInsets.symmetric(vertical: 4),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: ListTile(
                            leading: CircleAvatar(
                              backgroundColor: log.status.name == 'forwarded'
                                  ? Colors.green.withOpacity(0.2)
                                  : Colors.amber.withOpacity(0.2),
                              child: Icon(
                                log.status.name == 'forwarded'
                                    ? Icons.alt_route
                                    : Icons.filter_alt,
                                color: log.status.name == 'forwarded'
                                    ? Colors.greenAccent
                                    : Colors.amberAccent,
                                size: 20,
                              ),
                            ),
                            title: Text(
                              log.messageIdHex,
                              style: const TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.bold,
                                fontFamily: 'monospace',
                              ),
                            ),
                            subtitle: Text(
                              'TTL: ${log.inputTtl} → ${log.outputTtl} | HOPS: ${log.inputHops} → ${log.outputHops}',
                              style: const TextStyle(color: Colors.white60, fontSize: 12),
                            ),
                            trailing: Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 8,
                                vertical: 4,
                              ),
                              decoration: BoxDecoration(
                                color: log.status.name == 'forwarded'
                                    ? Colors.green
                                    : Colors.amber,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Text(
                                log.status.name.toUpperCase(),
                                style: const TextStyle(
                                  color: Colors.black,
                                  fontWeight: FontWeight.bold,
                                  fontSize: 10,
                                ),
                              ),
                            ),
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }
}
