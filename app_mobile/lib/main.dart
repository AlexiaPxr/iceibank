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
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF1B5E9E)),
        useMaterial3: true,
      ),
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
}
