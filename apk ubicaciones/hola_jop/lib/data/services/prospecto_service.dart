import 'dart:convert';
import 'package:http/http.dart' as http;
import '../../core/app_config.dart';
import '../../domain/entities/prospecto.dart';

class ProspectoService {
  static String get baseUrl => AppConfig.instance.prospectoBase;
  static String get _base   => AppConfig.instance.prospectoBase;

  static Future<List<Prospecto>> getProspectos() async {
    final resp = await http.get(Uri.parse('$_base/prospectos'))
        .timeout(const Duration(seconds: 15));
    if (resp.statusCode != 200) throw Exception('HTTP ${resp.statusCode}');
    final list = jsonDecode(resp.body) as List<dynamic>;
    return list.map((e) => Prospecto.fromJson(e as Map<String, dynamic>)).toList();
  }
}
