import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:uuid/uuid.dart';
import '../../domain/entities/category.dart';
import '../../core/app_config.dart';
import '../../domain/repositories/category_repository.dart';

class CategoryRepositoryImpl implements CategoryRepository {
  static String get _base => AppConfig.instance.mainBase;
  final _uuid = const Uuid();

  Category _fromJson(Map<String, dynamic> m) => Category(
        uuid: m['uuid'] as String,
        name: m['name'] as String,
        colorHex: (m['colorHex'] as String?) ?? '1565C0',
        iconName: (m['iconName'] as String?) ?? 'location_on',
        description: m['description'] as String?,
        createdAt: m['createdAt'] != null
            ? DateTime.tryParse(m['createdAt'] as String) ?? DateTime.now()
            : DateTime.now(),
      );

  Map<String, dynamic> _toJson(Category c) => {
        'uuid': c.uuid,
        'name': c.name,
        'colorHex': c.colorHex,
        'iconName': c.iconName,
        'description': c.description,
        'createdAt': c.createdAt.toIso8601String(),
      };

  @override
  Future<List<Category>> getAll() async {
    final resp = await http
        .get(Uri.parse('$_base/api/categorias-cliente'))
        .timeout(const Duration(seconds: 10));
    if (resp.statusCode == 200) {
      final List<dynamic> data = jsonDecode(resp.body);
      final list = data.map((e) => _fromJson(Map<String, dynamic>.from(e))).toList();
      list.sort((a, b) => a.name.compareTo(b.name));
      return list;
    }
    throw Exception('Error cargando categorías: ${resp.statusCode}');
  }

  @override
  Future<Category?> getById(String uuid) async {
    final resp = await http
        .get(Uri.parse('$_base/api/categorias-cliente/$uuid'))
        .timeout(const Duration(seconds: 10));
    if (resp.statusCode == 200) return _fromJson(jsonDecode(resp.body));
    return null;
  }

  @override
  Future<void> save(Category cat) async {
    final body = jsonEncode(_toJson(cat));
    final headers = {'Content-Type': 'application/json'};
    // Try update first; if 404 create
    final put = await http
        .put(Uri.parse('$_base/api/categorias-cliente/${cat.uuid}'),
            headers: headers, body: body)
        .timeout(const Duration(seconds: 10));
    if (put.statusCode == 404) {
      await http
          .post(Uri.parse('$_base/api/categorias-cliente'),
              headers: headers, body: body)
          .timeout(const Duration(seconds: 10));
    }
  }

  @override
  Future<void> delete(String uuid) async {
    await http
        .delete(Uri.parse('$_base/api/categorias-cliente/$uuid'))
        .timeout(const Duration(seconds: 10));
  }

  Category createNew({
    required String name,
    required String colorHex,
    String iconName = 'location_on',
    String? description,
  }) =>
      Category(
        uuid: _uuid.v4(),
        name: name,
        colorHex: colorHex,
        iconName: iconName,
        description: description,
        createdAt: DateTime.now(),
      );
}
