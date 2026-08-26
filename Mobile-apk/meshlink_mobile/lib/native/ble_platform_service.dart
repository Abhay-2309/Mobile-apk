import 'dart:async';
import 'package:flutter/services.dart';
import '../core/constants/mesh_constants.dart';

class BlePlatformService {
  static const MethodChannel _channel = MethodChannel(MeshConstants.channelName);
  static const EventChannel _eventChannel = EventChannel(MeshConstants.eventChannelName);

  Stream<Map<String, dynamic>>? _eventStream;

  Stream<Map<String, dynamic>> get eventStream {
    _eventStream ??= _eventChannel
        .receiveBroadcastStream()
        .map((dynamic event) => Map<String, dynamic>.from(event as Map));
    return _eventStream!;
  }

  /// Request BLE and location runtime permissions.
  /// Returns {granted: bool, bluetoothEnabled: bool}
  Future<Map<String, dynamic>> requestPermissions() async {
    try {
      final Map<dynamic, dynamic>? res = await _channel.invokeMethod('requestPermissions');
      return res != null ? Map<String, dynamic>.from(res) : {'granted': false, 'bluetoothEnabled': false};
    } on PlatformException catch (e) {
      print('BlePlatformService.requestPermissions failed: ${e.message}');
      return {'granted': false, 'bluetoothEnabled': false};
    }
  }

  /// Check if Bluetooth adapter is enabled.
  Future<bool> checkBluetoothEnabled() async {
    try {
      final bool? result = await _channel.invokeMethod('checkBluetoothEnabled');
      return result ?? false;
    } on PlatformException catch (e) {
      print('BlePlatformService.checkBluetoothEnabled failed: ${e.message}');
      return false;
    }
  }

  Future<bool> startMesh({bool enableRelay = true}) async {
    try {
      final bool? result = await _channel.invokeMethod('startMesh', {
        'enableRelay': enableRelay,
      });
      return result ?? false;
    } on PlatformException catch (e) {
      print('BlePlatformService.startMesh failed: ${e.message}');
      return false;
    }
  }

  Future<bool> stopMesh() async {
    try {
      final bool? result = await _channel.invokeMethod('stopMesh');
      return result ?? false;
    } on PlatformException catch (e) {
      print('BlePlatformService.stopMesh failed: ${e.message}');
      return false;
    }
  }

  Future<bool> broadcastSos({
    required int messageId,
    required int senderIdHash,
    required double latitude,
    required double longitude,
    required int timestamp,
    int ttl = 5,
    int hopCount = 0,
    required int battery,
    int severity = 2,
  }) async {
    try {
      final bool? result = await _channel.invokeMethod('broadcastSos', {
        'messageId': messageId,
        'senderIdHash': senderIdHash,
        'latitude': latitude,
        'longitude': longitude,
        'timestamp': timestamp,
        'ttl': ttl,
        'hopCount': hopCount,
        'battery': battery,
        'severity': severity,
      });
      return result ?? false;
    } on PlatformException catch (e) {
      print('BlePlatformService.broadcastSos failed: ${e.message}');
      return false;
    }
  }

  Future<bool> stopSosBroadcast() async {
    try {
      final bool? result = await _channel.invokeMethod('stopSosBroadcast');
      return result ?? false;
    } on PlatformException catch (e) {
      print('BlePlatformService.stopSosBroadcast failed: ${e.message}');
      return false;
    }
  }

  Future<Map<String, dynamic>> getDiagnostics() async {
    try {
      final Map<dynamic, dynamic>? res = await _channel.invokeMethod('getDiagnostics');
      return res != null ? Map<String, dynamic>.from(res) : {};
    } on PlatformException catch (e) {
      print('BlePlatformService.getDiagnostics failed: ${e.message}');
      return {};
    }
  }

  /// Get the current mesh state from native MeshRuntime.
  /// Returns {serviceRunning, bluetoothEnabled, scannerRunning,
  ///          advertiserRunning, nearbyPeerCount, sosActive, lastError}
  Future<Map<String, dynamic>> getMeshState() async {
    try {
      final Map<dynamic, dynamic>? res = await _channel.invokeMethod('getMeshState');
      return res != null ? Map<String, dynamic>.from(res) : {};
    } on PlatformException catch (e) {
      print('BlePlatformService.getMeshState failed: ${e.message}');
      return {};
    }
  }
}
