import 'dart:math';
import '../core/constants/mesh_constants.dart';
import '../models/sos_message.dart';
import '../native/ble_platform_service.dart';
import 'battery_service.dart';
import 'location_service.dart';
import 'local_message_store.dart';
import 'package:flutter/foundation.dart';

class SosService extends ChangeNotifier {
  final LocationService _locationService;
  final BatteryService _batteryService;
  final LocalMessageStore _store;
  final BlePlatformService _bleService;

  SosMessage? _activeSos;
  bool _isBroadcasting = false;
  bool _isLoading = false;
  late String _senderIdStr;
  late int _senderIdHash;

  SosService({
    LocationService? locationService,
    BatteryService? batteryService,
    LocalMessageStore? store,
    BlePlatformService? bleService,
  })  : _locationService = locationService ?? LocationService(),
        _batteryService = batteryService ?? BatteryService(),
        _store = store ?? LocalMessageStore(),
        _bleService = bleService ?? BlePlatformService() {
    _initDeviceIdentity();
  }

  SosMessage? get activeSos => _activeSos;
  bool get isBroadcasting => _isBroadcasting;
  bool get isLoading => _isLoading;
  String get senderIdStr => _senderIdStr;

  void _initDeviceIdentity() {
    final rng = Random();
    final randomSuffix = rng.nextInt(0xFFFFFF).toRadixString(16).toUpperCase().padLeft(6, '0');
    _senderIdStr = 'DEV-$randomSuffix';
    _senderIdHash = rng.nextInt(0xFFFFFFFF);
  }

  /// Triggers full SOS creation flow:
  /// User presses SOS -> Location -> Battery -> Message ID -> Create Packet -> Save Local -> Start BLE Advertising
  Future<SosMessage> triggerSos({int severity = MeshConstants.severityCritical}) async {
    _isLoading = true;
    notifyListeners();

    try {
      final location = await _locationService.getCurrentLocation();
      final battery = await _batteryService.getBatteryLevel();

      // Generate messageId ONCE per logical SOS
      final Random rng = Random();
      final int messageId = rng.nextInt(0xFFFFFFFF) & 0xFFFFFFFF;

      final int timestamp = DateTime.now().millisecondsSinceEpoch ~/ 1000;

      final sos = SosMessage(
        messageId: messageId,
        senderIdHash: _senderIdHash,
        senderIdStr: _senderIdStr,
        latitude: location.latitude,
        longitude: location.longitude,
        timestamp: timestamp,
        ttl: MeshConstants.defaultTtl,
        hopCount: MeshConstants.defaultHopCount,
        battery: battery,
        severity: severity,
      );

      _activeSos = sos;
      await _store.saveMessage(sos);

      // Start BLE Advertising
      _isBroadcasting = await _bleService.broadcastSos(
        messageId: sos.messageId,
        senderIdHash: sos.senderIdHash,
        latitude: sos.latitude,
        longitude: sos.longitude,
        timestamp: sos.timestamp,
        ttl: sos.ttl,
        hopCount: sos.hopCount,
        battery: sos.battery,
        severity: sos.severity,
      );

      return sos;
    } finally {
      _isLoading = false;
      notifyListeners();
    }
  }

  Future<void> stopSos() async {
    await _bleService.stopSosBroadcast();
    _isBroadcasting = false;
    _activeSos = null;
    notifyListeners();
  }
}
