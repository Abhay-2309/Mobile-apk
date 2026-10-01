import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../services/sos_service.dart';
import '../../services/battery_service.dart';
import '../../services/mesh_service.dart';
import '../../widgets/sos_button.dart';
import '../../widgets/mesh_status_card.dart';
import '../../widgets/packet_status_card.dart';

class SosScreen extends StatefulWidget {
  const SosScreen({super.key});

  @override
  State<SosScreen> createState() => _SosScreenState();
}

class _SosScreenState extends State<SosScreen> {
  int _batteryLevel = 82;

  @override
  void initState() {
    super.initState();
    _loadBattery();
  }

  Future<void> _loadBattery() async {
    final battery = await BatteryService().getBatteryLevel();
    if (mounted) {
      setState(() => _batteryLevel = battery);
    }
  }

  @override
  Widget build(BuildContext context) {
    final sosService = context.watch<SosService>();
    final meshService = context.watch<MeshService>();
    final activeSos = sosService.activeSos;
    final isBroadcasting = sosService.isBroadcasting;

    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      appBar: AppBar(
        title: const Text('MeshLink Rescue', style: TextStyle(fontWeight: FontWeight.bold)),
        backgroundColor: const Color(0xFF1E293B),
        centerTitle: true,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20.0),
        child: Column(
          children: [
            MeshStatusCard(
              isMeshActive: meshService.isMeshRunning,
              isBleReady: true,
              isGpsReady: true,
              batteryLevel: _batteryLevel,
            ),
            const SizedBox(height: 36),
            SosButton(
              isBroadcasting: isBroadcasting,
              onPressed: () async {
                if (isBroadcasting) {
                  await sosService.stopSos();
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      const SnackBar(content: Text('SOS Broadcast Paused')),
                    );
                  }
                } else {
                  final sos = await sosService.triggerSos();
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('SOS Triggered: ${sos.messageIdHex}')),
                    );
                  }
                }
              },
            ),
            const SizedBox(height: 36),
            if (activeSos != null)
              PacketStatusCard(
                sos: activeSos,
                isBroadcasting: isBroadcasting,
              )
            else
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Colors.white.withOpacity(0.05),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Text(
                  'Press SEND SOS in an emergency. Your GPS location & distress message will be continuously broadcast via offline BLE mesh.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.white60, fontSize: 13),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
