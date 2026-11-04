import 'package:flutter/material.dart';

class Category {
  final String uuid;
  final String name;
  final String colorHex;
  final String iconName;
  final String? description;
  final DateTime createdAt;

  const Category({
    required this.uuid,
    required this.name,
    required this.colorHex,
    this.iconName = 'location_on',
    this.description,
    required this.createdAt,
  });

  Color get color {
    try {
      return Color(int.parse('FF$colorHex', radix: 16));
    } catch (_) {
      return Colors.blue;
    }
  }

  Category copyWith({
    String? name,
    String? colorHex,
    String? iconName,
    String? description,
  }) {
    return Category(
      uuid: uuid,
      name: name ?? this.name,
      colorHex: colorHex ?? this.colorHex,
      iconName: iconName ?? this.iconName,
      description: description ?? this.description,
      createdAt: createdAt,
    );
  }
}
