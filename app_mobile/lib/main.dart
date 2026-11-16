import 'package:flutter/material.dart';

import 'app_state.dart';
import 'screens/home_screen.dart';
import 'screens/login_screen.dart';
import 'services/lider_service.dart';

void main() {
  runApp(const IceiBankApp());
}

class IceiBankApp extends StatefulWidget {
  const IceiBankApp({super.key});

  @override
  State<IceiBankApp> createState() => _IceiBankAppState();
}

class _IceiBankAppState extends State<IceiBankApp> {
  final AppState _app = AppState();
  final LiderService _lider = LiderService();

  @override
  void initState() {
    super.initState();
    _app.restaurarSessao();
  }

  @override
  void dispose() {
    _lider.dispose();
    _app.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ICEIBank',
      debugShowCheckedModeBanner: false,
      theme: _tema(),
      home: ListenableBuilder(
        listenable: _app,
        builder: (context, _) {
          if (_app.restaurando) {
            return const Scaffold(body: Center(child: CircularProgressIndicator()));
          }
          return _app.logado ? HomeScreen(app: _app, lider: _lider) : LoginScreen(app: _app);
        },
      ),
    );
  }

  ThemeData _tema() {
    final cores = ColorScheme.fromSeed(seedColor: const Color(0xFF0B3D91));
    final borda = OutlineInputBorder(
      borderRadius: BorderRadius.circular(12),
      borderSide: BorderSide(color: Colors.blueGrey.shade100),
    );
    return ThemeData(
      colorScheme: cores,
      useMaterial3: true,
      scaffoldBackgroundColor: const Color(0xFFF2F5FA),
      appBarTheme: AppBarTheme(
        backgroundColor: cores.primary,
        foregroundColor: Colors.white,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        color: Colors.white,
        elevation: 0,
        margin: const EdgeInsets.only(bottom: 12),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(18),
          side: BorderSide(color: Colors.blueGrey.shade50),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        border: borda,
        enabledBorder: borda,
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: cores.primary, width: 2),
        ),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(48),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          minimumSize: const Size.fromHeight(48),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: Colors.white,
        indicatorColor: cores.primaryContainer,
      ),
    );
  }
}
