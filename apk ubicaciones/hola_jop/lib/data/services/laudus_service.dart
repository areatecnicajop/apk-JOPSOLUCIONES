import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:hive_flutter/hive_flutter.dart';
import '../../core/app_config.dart';
import '../../domain/entities/order.dart';

class LaudusService {
  static String get _backendUrl => AppConfig.instance.mainBase;
  static const String _cacheBox    = 'products_cache';
  static const String _cacheKey    = 'products';
  static const String _tsKey       = 'products_ts';
  static const int    _cacheTtlMin = 30;

  // ─── PRODUCTOS con caché Hive (TTL 30 min) ───────────────────────────────

  static Future<List<LaudusProduct>> getProducts({bool forceRefresh = false}) async {
    final box = await Hive.openBox(_cacheBox);

    if (!forceRefresh) {
      final ts = box.get(_tsKey);
      if (ts != null) {
        final age = DateTime.now().difference(DateTime.parse(ts as String)).inMinutes;
        if (age < _cacheTtlMin) {
          final raw = box.get(_cacheKey);
          if (raw != null) {
            final List<dynamic> cached = jsonDecode(raw as String);
            // Verificar que el caché tiene el campo stock; si no, forzar recarga
            if (cached.isNotEmpty && (cached.first as Map)['stock'] != null) {
              return cached.map((e) => LaudusProduct.fromJson(Map<String, dynamic>.from(e))).toList();
            }
          }
        }
      }
    }

    final resp = await http
        .get(Uri.parse('$_backendUrl/api/app-catalog/products'))
        .timeout(const Duration(seconds: 15));

    if (resp.statusCode == 200) {
      final data = jsonDecode(resp.body) as Map<String, dynamic>;
      final List<dynamic> rawProducts = data['products'] as List<dynamic>;
      final products = rawProducts
          .map((e) => LaudusProduct.fromBackend(e as Map<String, dynamic>))
          .toList();
      // Guardar en caché
      await box.put(_cacheKey, jsonEncode(rawProducts));
      await box.put(_tsKey, DateTime.now().toIso8601String());
      return products;
    }
    throw Exception('Error cargando productos: ${resp.statusCode}');
  }

  // ─── ACTUALIZAR CATÁLOGO LOCAL (productos + stock) ───────────────────────
  //
  // Endpoint dedicado y aislado para la APK — /api/app-catalog/refresh no es
  // /sync/products ni /sync/stock (esos los dispara otro proceso aparte).
  // El backend encadena productos → stock en el orden correcto internamente.
  static Future<Map<String, dynamic>> syncAll() async {
    final resp = await http.post(
      Uri.parse('$_backendUrl/api/app-catalog/refresh'),
    ).timeout(const Duration(seconds: 120));

    if (resp.statusCode != 200) {
      throw Exception('Error actualizando catálogo: ${resp.statusCode}');
    }
    final result = jsonDecode(resp.body) as Map<String, dynamic>;
    await getProducts(forceRefresh: true);
    return {
      'productos': result['productos'] ?? 0,
      'stockActualizado': result['stockActualizado'] ?? 0,
    };
  }

  // ─── HEALTH CHECK ─────────────────────────────────────────────────────────

  static Future<bool> isBackendOnline() async {
    try {
      final resp = await http.get(
        Uri.parse('$_backendUrl/health'),
      ).timeout(const Duration(seconds: 5));
      return resp.statusCode == 200;
    } catch (_) {
      return false;
    }
  }

  // ─── Estado del caché local ────────────────────────────────────────────────

  static Future<String?> getLastSyncTime() async {
    final box = await Hive.openBox(_cacheBox);
    return box.get(_tsKey) as String?;
  }

  static Future<bool> hasLocalCache() async {
    final box = await Hive.openBox(_cacheBox);
    return box.get(_cacheKey) != null;
  }

  static Future<int> getCachedProductCount() async {
    final box = await Hive.openBox(_cacheBox);
    final raw = box.get(_cacheKey);
    if (raw == null) return 0;
    final List<dynamic> cached = jsonDecode(raw as String);
    return cached.length;
  }
}
