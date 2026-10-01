import 'package:geolocator/geolocator.dart';

class LocationResult {
  final double latitude;
  final double longitude;
  final double accuracy;
  final bool isLastKnown;
  final bool isAvailable;

  LocationResult({
    required this.latitude,
    required this.longitude,
    required this.accuracy,
    required this.isLastKnown,
    required this.isAvailable,
  });

  factory LocationResult.unavailable() {
    return LocationResult(
      latitude: 0.0,
      longitude: 0.0,
      accuracy: 0.0,
      isLastKnown: false,
      isAvailable: false,
    );
  }
}

class LocationService {
  Future<bool> checkAndRequestPermissions() async {
    bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
    if (!serviceEnabled) {
      return false;
    }

    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
      if (permission == LocationPermission.denied) {
        return false;
      }
    }

    if (permission == LocationPermission.deniedForever) {
      return false;
    }

    return true;
  }

  Future<LocationResult> getCurrentLocation() async {
    try {
      bool hasPermission = await checkAndRequestPermissions();
      if (!hasPermission) {
        return _tryGetLastKnown();
      }

      Position position = await Geolocator.getCurrentPosition(
        desiredAccuracy: LocationAccuracy.high,
        timeLimit: const Duration(seconds: 5),
      );

      return LocationResult(
        latitude: position.latitude,
        longitude: position.longitude,
        accuracy: position.accuracy,
        isLastKnown: false,
        isAvailable: true,
      );
    } catch (e) {
      print('LocationService: error getting fresh location, falling back to last known: $e');
      return _tryGetLastKnown();
    }
  }

  Future<LocationResult> _tryGetLastKnown() async {
    try {
      Position? position = await Geolocator.getLastKnownPosition();
      if (position != null) {
        return LocationResult(
          latitude: position.latitude,
          longitude: position.longitude,
          accuracy: position.accuracy,
          isLastKnown: true,
          isAvailable: true,
        );
      }
    } catch (e) {
      print('LocationService: error getting last known location: $e');
    }
    return LocationResult.unavailable();
  }
}
