import 'package:shared_preferences/shared_preferences.dart';

class AppConfig {
  static final AppConfig _instance = AppConfig._();
  static AppConfig get instance => _instance;
  AppConfig._();

  static const _keyLocal  = 'use_local';
  static const _keyGmaps  = 'gmaps_key';

  // Clave por defecto — la misma que ya usa el panel Angular. Queda
  // hardcodeada para que la APK funcione sin configuración manual; si se
  // guarda una distinta desde Config, esa tiene prioridad.
  static const _defaultGmapsKey = 'AIzaSyCvTrpvV4w_pnjBHcJrsu9Zn-3PJXteJC8';

  bool   _isLocal   = true;
  String _gmapsKey  = _defaultGmapsKey;

  bool   get isLocal   => _isLocal;
  String get gmapsKey  => _gmapsKey;

  // Local: ADB reverse tcp:8080 → llega al Spring Boot de la PC
  // Prod:  Nginx en apkjop strip /api/ → Spring Boot recibe /api/...
  String get mainBase => _isLocal
      ? 'http://localhost:8080'
      : 'https://apkjop.jopsoluciones.cl/api';

  String get prospectoBase => _isLocal
      ? 'http://localhost:8080/api'
      : 'https://apkjop.jopsoluciones.cl/api/api';

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    _isLocal  = prefs.getBool(_keyLocal)    ?? true;
    _gmapsKey = prefs.getString(_keyGmaps)  ?? _defaultGmapsKey;
  }

  Future<void> setLocal(bool value) async {
    _isLocal = value;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_keyLocal, value);
  }

  Future<void> setGmapsKey(String key) async {
    _gmapsKey = key.trim();
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_keyGmaps, _gmapsKey);
  }
}
