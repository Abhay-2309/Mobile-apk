import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../services/sos_service.dart';
import '../../services/mesh_service.dart';
import '../../widgets/sos_button.dart';

class SosMainScreen extends StatelessWidget {
  const SosMainScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final sosService = context.watch<SosService>();
    final meshService = context.watch<MeshService>();

    final isBroadcasting = sosService.isBroadcasting;
    final isMeshRunning = meshService.isMeshRunning;
    final nearbyPeerCount = meshService.nearbyPeerCount;

    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      body: SafeArea(
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Spacer(flex: 2),

              // App title
              const Text(
                'MESHLINK RESCUE',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 22,
                  fontWeight: FontWeight.bold,
                  letterSpacing: 3.0,
                ),
              ),
              const SizedBox(height: 6),
              const Text(
                'Offline Emergency BLE Mesh',
                style: TextStyle(
                  color: Colors.white38,
                  fontSize: 13,
                  letterSpacing: 1.0,
                ),
              ),

              const Spacer(flex: 2),

              // SOS Button
              SosButton(
                isBroadcasting: isBroadcasting,
                onPressed: () async {
                  if (sosService.isLoading) return;

                  if (isBroadcasting) {
                    await sosService.stopSos();
                  } else {
                    await sosService.triggerSos();
                  }
                },
              ),

              const SizedBox(height: 32),

              // Status text
              if (sosService.isLoading)
                const Text(
                  'Getting location...',
                  style: TextStyle(
                    color: Colors.white60,
                    fontSize: 14,
                  ),
                )
              else if (isBroadcasting)
                Column(
                  children: [
                    const Text(
                      'SOS IS ACTIVE',
                      style: TextStyle(
                        color: Colors.redAccent,
                        fontSize: 16,
                        fontWeight: FontWeight.bold,
                        letterSpacing: 1.5,
                      ),
                    ),
                    const SizedBox(height: 4),
                    const Text(
                      'Your distress signal is being broadcast\nvia the BLE mesh network',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        color: Colors.white54,
                        fontSize: 13,
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextButton(
                      onPressed: () async {
                        await sosService.stopSos();
                      },
                      child: const Text(
                        'CANCEL SOS',
                        style: TextStyle(
                          color: Colors.white38,
                          fontSize: 13,
                          letterSpacing: 1.0,
                        ),
                      ),
                    ),
                  ],
                )
              else
                const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 40.0),
                  child: Text(
                    'Press the button in an emergency.\nYour GPS location will be broadcast\nvia offline BLE mesh.',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Colors.white38,
                      fontSize: 13,
                      height: 1.5,
                    ),
                  ),
                ),

              const Spacer(flex: 3),

              // Mesh status indicator with peer count
              Padding(
                padding: const EdgeInsets.only(bottom: 24.0),
                child: Column(
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Container(
                          width: 8,
                          height: 8,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: isMeshRunning ? Colors.greenAccent : Colors.redAccent,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          isMeshRunning ? 'Mesh Active' : 'Mesh Inactive',
                          style: TextStyle(
                            color: isMeshRunning
                                ? Colors.greenAccent.withOpacity(0.7)
                                : Colors.redAccent.withOpacity(0.7),
                            fontSize: 12,
                            letterSpacing: 0.5,
                          ),
                        ),
                      ],
                    ),
                    if (isMeshRunning) ...[
                      const SizedBox(height: 4),
                      Text(
                        nearbyPeerCount == 1
                            ? '1 nearby device'
                            : '$nearbyPeerCount nearby devices',
                        style: TextStyle(
                          color: Colors.white.withOpacity(0.4),
                          fontSize: 11,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
