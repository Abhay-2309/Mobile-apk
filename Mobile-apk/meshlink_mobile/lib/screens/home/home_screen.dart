import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../../services/mesh_service.dart';
import '../sos/sos_screen.dart';
import '../relay/relay_screen.dart';
import '../network/diagnostics_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _currentIndex = 0;

  final List<Widget> _screens = const [
    SosScreen(),
    RelayScreen(),
    DiagnosticsScreen(),
  ];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      // Auto-start mesh background scanner on app launch
      context.read<MeshService>().startMesh();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A),
      body: IndexedStack(
        index: _currentIndex,
        children: _screens,
      ),
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: (index) => setState(() => _currentIndex = index),
        backgroundColor: const Color(0xFF1E293B),
        selectedItemColor: Colors.redAccent,
        unselectedItemColor: Colors.grey,
        type: BottomNavigationBarType.fixed,
        items: const [
          BottomNavigationBarItem(
            icon: Icon(Icons.sos_rounded),
            label: 'Victim SOS',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.router_rounded),
            label: 'Mesh Relay',
          ),
          BottomNavigationBarItem(
            icon: Icon(Icons.developer_board_rounded),
            label: 'Diagnostics',
          ),
        ],
      ),
    );
  }
}
