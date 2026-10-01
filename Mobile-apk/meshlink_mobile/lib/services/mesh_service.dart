import 'dart:async';
import 'package:flutter/foundation.dart';
import '../native/ble_platform_service.dart';

class MeshService extends ChangeNotifier {
  final BlePlatformService _bleService;

  bool _isMeshRunning = false;
  bool _permissionsGranted = false;
  bool _bluetoothEnabled = false;
  int _nearbyPeerCount = 0;
  bool _sosActive = false;
  String? _errorMessage;

  StreamSubscription? _eventSubscription;

  MeshService({
    BlePlatformService? bleService,
  }) : _bleService = bleService ?? BlePlatformService();

  bool get isMeshRunning => _isMeshRunning;
  bool get permissionsGranted => _permissionsGranted;
  bool get bluetoothEnabled => _bluetoothEnabled;
  int get nearbyPeerCount => _nearbyPeerCount;
  bool get sosActive => _sosActive;
  String? get errorMessage => _errorMessage;

  /// Request BLE permissions. Returns true if all granted.
  Future<bool> requestPermissions() async {
    final result = await _bleService.requestPermissions();
    _permissionsGranted = result['granted'] == true;
    _bluetoothEnabled = result['bluetoothEnabled'] == true;

    if (!_permissionsGranted) {
      _errorMessage = 'Bluetooth permissions are required for the emergency mesh to work.';
    } else if (!_bluetoothEnabled) {
      _errorMessage = 'Please turn on Bluetooth to use the emergency mesh.';
    } else {
      _errorMessage = null;
    }

    notifyListeners();
    return _permissionsGranted && _bluetoothEnabled;
  }

  /// Check if Bluetooth is currently enabled.
  Future<bool> checkBluetooth() async {
    _bluetoothEnabled = await _bleService.checkBluetoothEnabled();
    if (!_bluetoothEnabled) {
      _errorMessage = 'Please turn on Bluetooth to use the emergency mesh.';
    } else {
      _errorMessage = null;
    }
    notifyListeners();
    return _bluetoothEnabled;
  }

  /// Start the BLE mesh foreground service.
  Future<bool> startMesh() async {
    _initStream();
    _isMeshRunning = await _bleService.startMesh(enableRelay: true);
    if (!_isMeshRunning) {
      _errorMessage = 'Failed to start BLE mesh. Please check Bluetooth is on and permissions are granted.';
    } else {
      _errorMessage = null;
    }
    notifyListeners();

    // Fetch initial state after a short delay
    Future.delayed(const Duration(milliseconds: 800), () async {
      await refreshMeshState();
    });

    return _isMeshRunning;
  }

  /// Stop the BLE mesh foreground service.
  Future<void> stopMesh() async {
    await _bleService.stopMesh();
    _isMeshRunning = false;
    _nearbyPeerCount = 0;
    _sosActive = false;
    notifyListeners();
  }

  /// Fetch current mesh state from native side.
  Future<void> refreshMeshState() async {
    final state = await _bleService.getMeshState();
    _applyMeshState(state);
  }

  void _initStream() {
    if (_eventSubscription != null) return;
    _eventSubscription = _bleService.eventStream.listen(
      (event) {
        final type = event['type'] as String?;
        if (type == 'MESH_STATE') {
          final data = event['data'];
          if (data is Map) {
            _applyMeshState(Map<String, dynamic>.from(data));
          }
        }
        // PACKET and LOG events are received but not processed in UI
      },
      onError: (error) {
        debugPrint('MeshService event stream error: $error');
      },
    );
  }

  void _applyMeshState(Map<String, dynamic> state) {
    if (state.isEmpty) return;

    final serviceRunning = state['serviceRunning'] as bool? ?? false;
    final scannerRunning = state['scannerRunning'] as bool? ?? false;
    final btEnabled = state['bluetoothEnabled'] as bool? ?? false;
    final peerCount = state['nearbyPeerCount'] as int? ?? 0;
    final sosActive = state['sosActive'] as bool? ?? false;
    final lastError = state['lastError'] as String?;

    _isMeshRunning = serviceRunning && scannerRunning;
    _bluetoothEnabled = btEnabled;
    _nearbyPeerCount = peerCount;
    _sosActive = sosActive;

    if (!btEnabled) {
      _errorMessage = 'Bluetooth disabled';
    } else if (lastError != null) {
      _errorMessage = lastError;
    } else {
      _errorMessage = null;
    }

    notifyListeners();
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    super.dispose();
  }
}
