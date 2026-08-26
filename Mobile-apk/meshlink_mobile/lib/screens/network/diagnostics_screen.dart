import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../services/mesh_service.dart';
import '../../services/sos_service.dart';
import '../../native/ble_platform_service.dart';

class DiagnosticsScreen extends StatefulWidget {
  const DiagnosticsScreen({super.key});

  @override
  State<DiagnosticsScreen> createState() => _DiagnosticsScreenState();
}

class _DiagnosticsScreenState extends State<DiagnosticsScreen> {
  Map<String, dynamic> _nativeDiagnostics = {};

  @override
  void initState() {
    super.initState();
    _fetchDiagnostics();
  }

  Future<void> _fetchDiagnostics() async {
    final diag = await BlePlatformService().getDiagnostics();
    if (mounted) {
      setState(() => _nativeDiagnostics = diag);
    }
  }

  @override
  Widget build(BuildContext context) {
    final meshService = context.watch<MeshService>();
    final sosService = context.watch<SosService>();

    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        title: const Text('Developer Diagnostics', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: const Color(0xFF1E293B),
        centerTitle: true,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            onPressed: _fetchDiagnostics,
          ),
          IconButton(
            icon: const Icon(Icons.delete_sweep),
            onPressed: meshService.clearLogs,
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: const Color(0xFF1E293B),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                children: [
                  _buildDiagRow('Device ID', sosService.senderIdStr),
                  _buildDiagRow(
                    'BLE Adapter',
                    (_nativeDiagnostics['bluetoothEnabled'] ?? false) ? 'Enabled' : 'Disabled',
                  ),
                  _buildDiagRow(
                    'BLE Scanner',
                    (_nativeDiagnostics['isScanning'] ?? false) ? 'Active' : 'Stopped',
                  ),
                  _buildDiagRow(
                    'BLE Advertiser',
                    (_nativeDiagnostics['isAdvertising'] ?? false) ? 'Broadcasting' : 'Idle',
                  ),
                  _buildDiagRow('Received / Forwarded', '${meshService.receivedCount} / ${meshService.forwardedCount}'),
                  _buildDiagRow('Duplicates / Expired', '${meshService.duplicateCount} / ${meshService.expiredCount}'),
                ],
              ),
            ),
            const SizedBox(height: 16),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text(
                  'LIVE MESH LOGS',
                  style: TextStyle(
                    color: Colors.white70,
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    letterSpacing: 1.2,
                  ),
                ),
                Text(
                  '${meshService.logs.length} lines',
                  style: const TextStyle(color: Colors.white38, fontSize: 12),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Expanded(
              child: Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.black,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: Colors.cyanAccent.withOpacity(0.3)),
                ),
                child: meshService.logs.isEmpty
                    ? const Center(
                        child: Text(
                          'No logs captured yet...',
                          style: TextStyle(color: Colors.greenAccent, fontFamily: 'monospace'),
                        ),
                      )
                    : ListView.builder(
                        itemCount: meshService.logs.length,
                        itemBuilder: (context, index) {
                          final log = meshService.logs[index];
                          Color logColor = Colors.greenAccent;
                          if (log.contains('[ERROR]')) logColor = Colors.redAccent;
                          if (log.contains('[DUPLICATE]')) logColor = Colors.amberAccent;
                          if (log.contains('[RELAY]')) logColor = Colors.cyanAccent;

                          return Padding(
                            padding: const EdgeInsets.symmetric(vertical: 2.0),
                            child: Text(
                              log,
                              style: TextStyle(
                                color: logColor,
                                fontSize: 11,
                                fontFamily: 'monospace',
                              ),
                            ),
                          );
                        },
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDiagRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.white70, fontSize: 13)),
          Text(
            value,
            style: const TextStyle(
              color: Colors.white,
              fontWeight: FontWeight.bold,
              fontSize: 13,
              fontFamily: 'monospace',
            ),
          ),
        ],
      ),
    );
  }
}
