import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'core/constants/mesh_constants.dart';
import 'services/mesh_service.dart';
import 'services/sos_service.dart';
import 'screens/splash/splash_screen.dart';

class MeshLinkApp extends StatelessWidget {
  const MeshLinkApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => MeshService()),
        Provider(create: (_) => SosService()),
      ],
      child: MaterialApp(
        title: MeshConstants.appName,
        debugShowCheckedModeBanner: false,
        theme: ThemeData(
          useMaterial3: true,
          brightness: Brightness.dark,
          scaffoldBackgroundColor: const Color(0xFF0F172A),
          colorScheme: ColorScheme.fromSeed(
            seedColor: Colors.redAccent,
            brightness: Brightness.dark,
          ),
        ),
        home: const SplashScreen(),
      ),
    );
  }
}
