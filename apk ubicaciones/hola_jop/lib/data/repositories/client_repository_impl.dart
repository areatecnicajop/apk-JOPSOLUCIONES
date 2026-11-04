import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:uuid/uuid.dart';
import '../../domain/entities/client.dart';
import '../../core/app_config.dart';
import '../../domain/repositories/client_repository.dart';

class ClientRepositoryImpl implements ClientRepository {
  static String get _baseUrl => AppConfig.instance.mainBase;
  final _uuid = const Uuid();

  Client _fromMap(Map<String, dynamic> m) {
    Map<String, String> extras = {};
    final raw = m['extraFields'];
    if (raw != null && raw.toString().isNotEmpty) {
      try {
        final decoded = jsonDecode(raw.toString()) as Map<String, dynamic>;
        extras = decoded.map((k, v) => MapEntry(k, v.toString()));
      } catch (_) {}
    }
    return Client(
      uuid: m['uuid'] as String,
      name: m['name'] as String,
      phone: m['phone'] as String?,
      email: m['email'] as String?,
      address: m['address'] as String?,
      latitude: (m['latitude'] as num? ?? 0).toDouble(),
      longitude: (m['longitude'] as num? ?? 0).toDouble(),
      categoryId: m['categoryId'] as String?,
      categoryName: m['categoryName'] as String?,
      status: (m['status'] as String?) ?? 'active',
      notes: m['notes'] as String?,
      extraFields: extras,
      createdAt: m['createdAt'] != null ? DateTime.tryParse(m['createdAt']) ?? DateTime.now() : DateTime.now(),
      updatedAt: m['updatedAt'] != null ? DateTime.tryParse(m['updatedAt']) ?? DateTime.now() : DateTime.now(),
    );
  }

  Map<String, dynamic> _toMap(Client c) => {
    'uuid': c.uuid,
    'name': c.name,
    'phone': c.phone,
    'email': c.email,
    'address': c.address,
    'latitude': c.latitude,
    'longitude': c.longitude,
    'categoryId': c.categoryId,
    'categoryName': c.categoryName,
    'status': c.status,
    'notes': c.notes,
    'extraFields': c.extraFields.isNotEmpty ? jsonEncode(c.extraFields) : null,
    'createdAt': c.createdAt.toIso8601String(),
    'updatedAt': c.updatedAt.toIso8601String(),
  };

  @override
  Future<List<Client>> getAll() async {
    final resp = await http.get(Uri.parse('$_baseUrl/api/clientes'))
        .timeout(const Duration(seconds: 10));
    if (resp.statusCode == 200) {
      final List<dynamic> raw = jsonDecode(resp.body);
      final list = raw.map((e) => _fromMap(e as Map<String, dynamic>)).toList();
      list.sort((a, b) => a.name.compareTo(b.name));
      return list;
    }
    throw Exception('Error cargando clientes: ${resp.statusCode}');
  }

  @override
  Future<Client?> getById(String uuid) async {
    final resp = await http.get(Uri.parse('$_baseUrl/api/clientes/$uuid'))
        .timeout(const Duration(seconds: 10));
    if (resp.statusCode == 200) return _fromMap(jsonDecode(resp.body));
    if (resp.statusCode == 404) return null;
    throw Exception('Error: ${resp.statusCode}');
  }

  @override
  Future<void> save(Client client) async {
    final body = jsonEncode(_toMap(client));
    final headers = {'Content-Type': 'application/json'};
    final exists = await getById(client.uuid);
    if (exists != null) {
      await http.put(Uri.parse('$_baseUrl/api/clientes/${client.uuid}'),
          headers: headers, body: body)
          .timeout(const Duration(seconds: 10));
    } else {
      await http.post(Uri.parse('$_baseUrl/api/clientes'),
          headers: headers, body: body)
          .timeout(const Duration(seconds: 10));
    }
  }

  @override
  Future<void> delete(String uuid) async {
    await http.delete(Uri.parse('$_baseUrl/api/clientes/$uuid'))
        .timeout(const Duration(seconds: 10));
  }

  @override
  Future<List<Client>> search(String query) async {
    final all = await getAll();
    final q = query.toLowerCase();
    return all.where((c) =>
      c.name.toLowerCase().contains(q) ||
      (c.phone?.toLowerCase().contains(q) ?? false) ||
      (c.email?.toLowerCase().contains(q) ?? false) ||
      (c.categoryName?.toLowerCase().contains(q) ?? false) ||
      (c.address?.toLowerCase().contains(q) ?? false),
    ).toList();
  }

  @override
  Future<List<Client>> filterByCategory(String categoryId) async {
    final all = await getAll();
    return all.where((c) => c.categoryId == categoryId).toList();
  }

  @override
  Future<List<Client>> filterByStatus(String status) async {
    final all = await getAll();
    return all.where((c) => c.status == status).toList();
  }

  Client createNew({
    required String name, required double lat, required double lng,
    String? phone, String? email, String? address,
    String? categoryId, String? categoryName,
    String status = 'active', String? notes,
    Map<String, String> extraFields = const {},
  }) {
    final now = DateTime.now();
    return Client(
      uuid: _uuid.v4(), name: name,
      phone: phone, email: email, address: address,
      latitude: lat, longitude: lng,
      categoryId: categoryId, categoryName: categoryName,
      status: status, notes: notes, extraFields: extraFields,
      createdAt: now, updatedAt: now,
    );
  }
}
