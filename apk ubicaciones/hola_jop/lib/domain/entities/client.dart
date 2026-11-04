import 'dart:convert';

class Client {
  final String uuid;
  final String name;
  final String? phone;
  final String? email;
  final String? address;
  final double latitude;
  final double longitude;
  final String? categoryId;
  final String? categoryName;
  final String status;
  final String? notes;
  final Map<String, String> extraFields;
  final DateTime createdAt;
  final DateTime updatedAt;

  const Client({
    required this.uuid,
    required this.name,
    this.phone,
    this.email,
    this.address,
    required this.latitude,
    required this.longitude,
    this.categoryId,
    this.categoryName,
    this.status = 'active',
    this.notes,
    this.extraFields = const {},
    required this.createdAt,
    required this.updatedAt,
  });

  Client copyWith({
    String? name,
    String? phone,
    String? email,
    String? address,
    double? latitude,
    double? longitude,
    String? categoryId,
    String? categoryName,
    String? status,
    String? notes,
    Map<String, String>? extraFields,
  }) {
    return Client(
      uuid: uuid,
      name: name ?? this.name,
      phone: phone ?? this.phone,
      email: email ?? this.email,
      address: address ?? this.address,
      latitude: latitude ?? this.latitude,
      longitude: longitude ?? this.longitude,
      categoryId: categoryId ?? this.categoryId,
      categoryName: categoryName ?? this.categoryName,
      status: status ?? this.status,
      notes: notes ?? this.notes,
      extraFields: extraFields ?? this.extraFields,
      createdAt: createdAt,
      updatedAt: DateTime.now(),
    );
  }

  Map<String, String> extraFieldsFromJson(String? json) {
    if (json == null || json.isEmpty) return {};
    try {
      final Map<String, dynamic> decoded = jsonDecode(json);
      return decoded.map((k, v) => MapEntry(k, v.toString()));
    } catch (_) {
      return {};
    }
  }

  String extraFieldsToJson() => jsonEncode(extraFields);
}
