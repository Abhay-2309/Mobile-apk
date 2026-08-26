import 'package:battery_plus/battery_plus.dart';

class BatteryService {
  final Battery _battery = Battery();

  Future<int> getBatteryLevel() async {
    try {
      final level = await _battery.batteryLevel;
      return level.clamp(0, 100);
    } catch (e) {
      print('BatteryService: Failed to read battery level: $e');
      return 100; // Fallback default
    }
  }
}
