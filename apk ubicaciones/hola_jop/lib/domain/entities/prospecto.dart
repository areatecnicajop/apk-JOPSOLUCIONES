class Prospecto {
  final int id;
  final String nombre;
  final String rubro;
  final String? telefono;
  final String? email;
  final String? direccion;
  final String? comuna;
  final String? region;
  final double? latitud;
  final double? longitud;
  final String? url;

  const Prospecto({
    required this.id,
    required this.nombre,
    required this.rubro,
    this.telefono,
    this.email,
    this.direccion,
    this.comuna,
    this.region,
    this.latitud,
    this.longitud,
    this.url,
  });

  bool get tieneUbicacion => latitud != null && longitud != null;

  String get direccionCompleta {
    final parts = [direccion, comuna].where((p) => p != null && p.isNotEmpty).toList();
    return parts.join(', ');
  }

  String get googleMapsUrl =>
      tieneUbicacion
          ? 'https://www.google.com/maps/search/?api=1&query=$latitud,$longitud'
          : 'https://www.google.com/maps/search/?api=1&query=${Uri.encodeComponent(nombre + ' ' + (comuna ?? ''))}';

  factory Prospecto.fromJson(Map<String, dynamic> json) {
    return Prospecto(
      id:        (json['id'] as num).toInt(),
      nombre:    json['nombre']?.toString() ?? '',
      rubro:     json['rubro']?.toString() ?? '',
      telefono:  json['telefono']?.toString(),
      email:     json['email']?.toString(),
      direccion: json['direccion']?.toString(),
      comuna:    json['comuna']?.toString(),
      region:    json['region']?.toString(),
      latitud:   (json['latitud'] as num?)?.toDouble(),
      longitud:  (json['longitud'] as num?)?.toDouble(),
      url:       json['url']?.toString(),
    );
  }
}
