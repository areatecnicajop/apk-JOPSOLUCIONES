import 'package:flutter/material.dart';

class IconUtils {
  static const Map<String, IconData> _icons = {
    'location_on': Icons.location_on,
    'store': Icons.store,
    'person': Icons.person,
    'star': Icons.star,
    'home': Icons.home,
    'business': Icons.business,
    'local_shipping': Icons.local_shipping,
    'build': Icons.build,
    'favorite': Icons.favorite,
    'work': Icons.work,
    'school': Icons.school,
    'restaurant': Icons.restaurant,
  };

  static IconData get(String name) => _icons[name] ?? Icons.location_on;
}
