class AppConstants {
  static const List<String> clientStatuses = ['active', 'inactive', 'prospect', 'lead'];

  static const Map<String, String> statusLabels = {
    'active': 'Activo',
    'inactive': 'Inactivo',
    'prospect': 'Prospecto',
    'lead': 'Lead',
  };

  static const List<Map<String, String>> defaultCategoryColors = [
    {'name': 'Rojo', 'hex': 'E53935'},
    {'name': 'Rosa', 'hex': 'E91E63'},
    {'name': 'Morado', 'hex': '8E24AA'},
    {'name': 'Azul', 'hex': '1E88E5'},
    {'name': 'Celeste', 'hex': '039BE5'},
    {'name': 'Verde', 'hex': '43A047'},
    {'name': 'Lima', 'hex': '7CB342'},
    {'name': 'Naranja', 'hex': 'FB8C00'},
    {'name': 'Amarillo', 'hex': 'F9A825'},
    {'name': 'Marrón', 'hex': '6D4C41'},
  ];

  static const List<String> categoryIcons = [
    'location_on', 'store', 'person', 'star', 'home', 'business',
    'local_shipping', 'build', 'favorite', 'work', 'school', 'restaurant',
  ];
}
