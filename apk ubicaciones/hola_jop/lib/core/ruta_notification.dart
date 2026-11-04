import 'package:flutter/material.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'navigator_key.dart';
import '../data/services/ruta_en_curso.dart';
import '../presentation/screens/rutas/registro_rapido_screen.dart';

// Notificación persistente para volver rápido a JOP y registrar una visita
// mientras navegas con Google Maps abierto — reemplaza al intento anterior
// con una burbuja flotante (requería un permiso especial de Android que
// varía por marca de teléfono y no se pudo hacer andar de forma confiable).
class RutaNotification {
  static final _plugin = FlutterLocalNotificationsPlugin();
  static const _channelId = 'ruta_en_curso';
  static const _notifId = 1001;
  static bool _inicializado = false;

  static Future<void> init() async {
    if (_inicializado) return;
    _inicializado = true;
    const androidInit = AndroidInitializationSettings('@mipmap/ic_launcher');
    await _plugin.initialize(
      settings: const InitializationSettings(android: androidInit),
      onDidReceiveNotificationResponse: _onTap,
    );
  }

  static void _onTap(NotificationResponse response) {
    if (RutaEnCursoHolder.actual == null) return;
    navigatorKey.currentState?.push(
      MaterialPageRoute(builder: (_) => const RegistroRapidoScreen()),
    );
  }

  static Future<void> pedirPermiso() async {
    await _plugin
        .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
        ?.requestNotificationsPermission();
  }

  static Future<void> mostrar({required int pendientes}) async {
    const androidDetails = AndroidNotificationDetails(
      _channelId, 'Ruta en curso',
      channelDescription: 'Volver a JOP para registrar visitas mientras navegas una ruta',
      importance: Importance.high,
      priority: Priority.high,
      ongoing: true,
      autoCancel: false,
      icon: '@mipmap/ic_launcher',
    );
    await _plugin.show(
      id: _notifId,
      title: 'Ruta en curso — JOP',
      body: pendientes > 0 ? 'Toca para registrar una visita ($pendientes pendientes)' : 'Toca para ver el detalle',
      notificationDetails: const NotificationDetails(android: androidDetails),
    );
  }

  static Future<void> ocultar() => _plugin.cancel(id: _notifId);
}
