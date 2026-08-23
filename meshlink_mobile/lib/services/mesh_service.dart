import 'dart:async';
import 'package:flutter/foundation.dart';
import '../core/constants/mesh_constants.dart';
import '../models/sos_message.dart';
import '../models/relay_result.dart';
import '../native/ble_platform_service.dart';
import 'local_message_store.dart';
import 'packet_service.dart';

class MeshService extends ChangeNotifier {
  final BlePlatformService _bleService;
  final LocalMessageStore _store;
  final PacketService _packetService;

  bool _isRelayEnabled = true;
  bool _isMeshRunning = false;

  int _receivedCount = 0;
  int _forwardedCount = 0;
  int _duplicateCount = 0;
  int _expiredCount = 0;

  final List<SosMessage> _receivedMessages = [];
  final List<RelayResult> _relayHistory = [];
  final List<String> _logs = [];

  StreamSubscription? _eventSubscription;

  MeshService({
    BlePlatformService? bleService,
    LocalMessageStore? store,
    PacketService? packetService,
  })  : _bleService = bleService ?? BlePlatformService(),
        _store = store ?? LocalMessageStore(),
        _packetService = packetService ?? PacketService() {
    _initStream();
  }

  bool get isRelayEnabled => _isRelayEnabled;
  bool get isMeshRunning => _isMeshRunning;
  int get receivedCount => _receivedCount;
  int get forwardedCount => _forwardedCount;
  int get duplicateCount => _duplicateCount;
  int get expiredCount => _expiredCount;

  List<SosMessage> get receivedMessages => List.unmodifiable(_receivedMessages);
  List<RelayResult> get relayHistory => List.unmodifiable(_relayHistory);
  List<String> get logs => List.unmodifiable(_logs);

  void _initStream() {
    _eventSubscription = _bleService.eventStream.listen((event) {
      final type = event['type'] as String?;
      if (type == 'PACKET') {
        _handleIncomingPacketMap(Map<String, dynamic>.from(event['data'] as Map));
      } else if (type == 'LOG') {
        final tag = event['tag'] as String? ?? 'BLE';
        final msg = event['message'] as String? ?? '';
        addLog('[$tag] $msg');
      }
    });
  }

  Future<void> toggleRelay(bool enabled) async {
    _isRelayEnabled = enabled;
    addLog('[RELAY] Relay mode switched to ${enabled ? "ON" : "OFF"}');
    notifyListeners();
    if (_isMeshRunning) {
      await startMesh(); // Restart with updated relay flag
    }
  }

  Future<bool> startMesh() async {
    _isMeshRunning = await _bleService.startMesh(enableRelay: _isRelayEnabled);
    if (_isMeshRunning) {
      addLog('[MESH] Mesh service started successfully (Scanner Active)');
    }
    notifyListeners();
    return _isMeshRunning;
  }

  Future<void> stopMesh() async {
    await _bleService.stopMesh();
    _isMeshRunning = false;
    addLog('[MESH] Mesh service stopped');
    notifyListeners();
  }

  void _handleIncomingPacketMap(Map<String, dynamic> data) async {
    final int messageId = data['messageId'] as int;
    final String statusStr = data['status'] as String? ?? 'PROCESSED';
    final int ttl = data['ttl'] as int;
    final int hopCount = data['hopCount'] as int;

    final String hexId = '0x${messageId.toRadixString(16).toUpperCase().padLeft(8, '0')}';

    if (statusStr == 'DUPLICATE_DISCARDED') {
      _duplicateCount++;
      addLog('[DUPLICATE] $hexId already processed. Discarding.');
      _recordRelay(RelayResult(
        messageIdHex: hexId,
        status: RelayStatus.duplicateIgnored,
        inputTtl: ttl,
        outputTtl: ttl,
        inputHops: hopCount,
        outputHops: hopCount,
        details: 'Duplicate packet discarded',
      ));
      notifyListeners();
      return;
    }

    _receivedCount++;

    final sos = SosMessage(
      messageId: messageId,
      senderIdHash: data['senderIdHash'] as int,
      senderIdStr: 'DEV-${(data['senderIdHash'] as int).toRadixString(16).toUpperCase().padLeft(6, '0')}',
      latitude: (data['latitude'] as num).toDouble(),
      longitude: (data['longitude'] as num).toDouble(),
      timestamp: data['timestamp'] as int,
      ttl: ttl,
      hopCount: hopCount,
      battery: data['battery'] as int,
      severity: data['severity'] as int,
    );

    // Save locally
    _receivedMessages.insert(0, sos);
    await _store.saveMessage(sos);
    addLog('[STORE] Saved message $hexId locally');

    if (!_isRelayEnabled) {
      _recordRelay(RelayResult(
        messageIdHex: hexId,
        status: RelayStatus.received,
        inputTtl: ttl,
        outputTtl: ttl,
        inputHops: hopCount,
        outputHops: hopCount,
        details: 'Received (Relay disabled)',
      ));
    } else if (ttl <= 0) {
      _expiredCount++;
      addLog('[RELAY] Message $hexId TTL reached 0. Stopping relay.');
      _recordRelay(RelayResult(
        messageIdHex: hexId,
        status: RelayStatus.expiredTtl,
        inputTtl: ttl,
        outputTtl: 0,
        inputHops: hopCount,
        outputHops: hopCount,
        details: 'TTL expired',
      ));
    } else {
      _forwardedCount++;
      final int newTtl = ttl - 1;
      final int newHops = hopCount + 1;
      addLog('[RELAY] TTL $ttl -> $newTtl | HOPS $hopCount -> $newHops');
      addLog('[BLE] Forwarding $hexId over BLE advertising...');
      _recordRelay(RelayResult(
        messageIdHex: hexId,
        status: RelayStatus.forwarded,
        inputTtl: ttl,
        outputTtl: newTtl,
        inputHops: hopCount,
        outputHops: newHops,
        details: 'Forwarded over BLE',
      ));
    }

    notifyListeners();
  }

  void _recordRelay(RelayResult result) {
    _relayHistory.insert(0, result);
    _store.logRelay(result);
  }

  void addLog(String log) {
    final timestamp = DateTime.now().toIso8601String().substring(11, 19);
    _logs.insert(0, '$timestamp $log');
    if (_logs.length > 200) {
      _logs.removeLast();
    }
    notifyListeners();
  }

  void clearLogs() {
    _logs.clear();
    notifyListeners();
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    super.dispose();
  }
}
