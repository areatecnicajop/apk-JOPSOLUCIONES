import 'dart:convert';
import 'package:http/http.dart' as http;
import '../../core/app_config.dart';
import '../../domain/entities/ruta.dart';
import '../../domain/entities/visita.dart';

class RutaService {
  static String get _base => AppConfig.instance.prospectoBase;

  static Future<List<Ruta>> getAll() async {
    final resp = await http.get(Uri.parse('$_base/rutas'))
        .timeout(const Duration(seconds: 15));
    if (resp.statusCode != 200) throw Exception('Error cargando rutas: ${resp.statusCode}');
    final list = jsonDecode(resp.body) as List<dynamic>;
    return list.map((e) => Ruta.fromJson(e as Map<String, dynamic>)).toList();
  }

  static Future<Ruta> create(Ruta ruta) async {
    final resp = await http.post(
      Uri.parse('$_base/rutas'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode(ruta.toJson()),
    ).timeout(const Duration(seconds: 15));
    if (resp.statusCode != 200) throw Exception('Error guardando ruta: ${resp.statusCode}');
    return Ruta.fromJson(jsonDecode(resp.body));
  }

  static Future<void> delete(String uuid) async {
    final resp = await http.delete(Uri.parse('$_base/rutas/$uuid'))
        .timeout(const Duration(seconds: 10));
    if (resp.statusCode != 204 && resp.statusCode != 200) {
      throw Exception('Error eliminando ruta: ${resp.statusCode}');
    }
  }

  static Future<List<Visita>> getVisitas(String rutaUuid) async {
    final resp = await http.get(Uri.parse('$_base/rutas/$rutaUuid/visitas'))
        .timeout(const Duration(seconds: 15));
    if (resp.statusCode != 200) throw Exception('Error cargando visitas: ${resp.statusCode}');
    final list = jsonDecode(resp.body) as List<dynamic>;
    return list.map((e) => Visita.fromJson(e as Map<String, dynamic>)).toList();
  }

  static Future<Visita> registrarVisita(String rutaUuid, {int? prospectoId, required String prospectoNombre, String? nota}) async {
    final resp = await http.post(
      Uri.parse('$_base/rutas/$rutaUuid/visitas'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode(Visita(rutaUuid: rutaUuid, prospectoId: prospectoId, prospectoNombre: prospectoNombre, nota: nota).toJson()),
    ).timeout(const Duration(seconds: 15));
    if (resp.statusCode != 200) throw Exception('Error registrando visita: ${resp.statusCode}');
    return Visita.fromJson(jsonDecode(resp.body));
  }
}
