import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import 'core/app_config.dart';
import 'core/navigator_key.dart';
import 'core/ruta_notification.dart';
import 'core/theme/app_theme.dart';
import 'data/services/database_service.dart';
import 'presentation/providers/providers.dart';
import 'presentation/screens/home_shell.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppConfig.instance.load();
  await DatabaseService.init();
  await _seedInitialData();
  await RutaNotification.init();
  runApp(const ProviderScope(child: JopUbicacionesApp()));
}

Future<void> _seedInitialData() async {
  // Solo inserta si la base de datos está vacía
  if (DatabaseService.clients.isNotEmpty) return;

  const uuid = Uuid();
  final now = DateTime.now().toIso8601String();

  // Categoría: Empresa
  final catId = uuid.v4();
  await DatabaseService.categories.put(catId, {
    'uuid': catId,
    'name': 'Empresa',
    'colorHex': '1565C0',
    'iconName': 'business',
    'description': 'Empresas y locales comerciales',
    'createdAt': now,
  });

  // Cliente: JOP Soluciones
  final clientId = uuid.v4();
  await DatabaseService.clients.put(clientId, {
    'uuid': clientId,
    'name': 'JOP Soluciones',
    'phone': '+56 9 0000 0000',
    'email': 'contacto@jopsoluciones.com',
    'address': 'Lo Espejo, Región Metropolitana, Chile',
    'latitude': -33.42719,
    'longitude': -70.70964,
    'categoryId': catId,
    'categoryName': 'Empresa',
    'status': 'active',
    'notes': 'Oficina principal JOP Soluciones',
    'extraFields': jsonEncode({'Web': 'jopsoluciones.com'}),
    'createdAt': now,
    'updatedAt': now,
  });
}

class JopUbicacionesApp extends ConsumerWidget {
  const JopUbicacionesApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final isDark = ref.watch(themeModeProvider);
    return MaterialApp(
      navigatorKey: navigatorKey,
      title: 'JOP Ubicaciones',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      darkTheme: AppTheme.dark(),
      themeMode: isDark ? ThemeMode.dark : ThemeMode.light,
      home: const HomeShell(),
    );
  }
}
