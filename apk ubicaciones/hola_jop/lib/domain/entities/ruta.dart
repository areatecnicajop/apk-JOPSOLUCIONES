import 'dart:convert';

// ── Parada guardada dentro de una ruta (snapshot del prospecto) ────────────
class RutaParada {
  final String? prospectoUuid;
  final String nombre;
  final String? rubro;
  final double lat;
  final double lng;
  final int orden;

  const RutaParada({
    this.prospectoUuid,
    required this.nombre,
    this.rubro,
    required this.lat,
    required this.lng,
    required this.orden,
  });

  factory RutaParada.fromJson(Map<String, dynamic> json) => RutaParada(
        prospectoUuid: json['prospectoUuid']?.toString(),
        nombre: json['nombre']?.toString() ?? '',
        rubro: json['rubro']?.toString(),
        lat: (json['lat'] as num).toDouble(),
        lng: (json['lng'] as num).toDouble(),
        orden: (json['orden'] as num).toInt(),
      );

  Map<String, dynamic> toJson() => {
        'prospectoUuid': prospectoUuid,
        'nombre': nombre,
        'rubro': rubro,
        'lat': lat,
        'lng': lng,
        'orden': orden,
      };
}

// ── Ruta guardada (tal como la devuelve /api/rutas) ─────────────────────────
class Ruta {
  final String uuid;
  final String nombre;
  final String tipo; // auto | manual
  final double inicioLat;
  final double inicioLng;
  final String inicioLabel;
  final double finLat;
  final double finLng;
  final String finLabel;
  final List<RutaParada> paradas;
  final double? distanciaKm;
  final int? duracionMin;
  final String createdAt;

  const Ruta({
    required this.uuid,
    required this.nombre,
    required this.tipo,
    required this.inicioLat,
    required this.inicioLng,
    required this.inicioLabel,
    required this.finLat,
    required this.finLng,
    required this.finLabel,
    required this.paradas,
    this.distanciaKm,
    this.duracionMin,
    required this.createdAt,
  });

  factory Ruta.fromJson(Map<String, dynamic> json) {
    List<RutaParada> paradas = [];
    try {
      final raw = json['paradas'];
      final list = raw is String ? jsonDecode(raw) as List<dynamic> : (raw as List? ?? []);
      paradas = list.map((e) => RutaParada.fromJson(e as Map<String, dynamic>)).toList();
    } catch (_) {}

    return Ruta(
      uuid: json['uuid']?.toString() ?? '',
      nombre: json['nombre']?.toString() ?? '',
      tipo: json['tipo']?.toString() ?? 'auto',
      inicioLat: (json['inicioLat'] as num).toDouble(),
      inicioLng: (json['inicioLng'] as num).toDouble(),
      inicioLabel: json['inicioLabel']?.toString() ?? '',
      finLat: (json['finLat'] as num).toDouble(),
      finLng: (json['finLng'] as num).toDouble(),
      finLabel: json['finLabel']?.toString() ?? '',
      paradas: paradas,
      distanciaKm: (json['distanciaKm'] as num?)?.toDouble(),
      duracionMin: (json['duracionMin'] as num?)?.toInt(),
      createdAt: json['createdAt']?.toString() ?? '',
    );
  }

  Map<String, dynamic> toJson() => {
        'uuid': uuid,
        'nombre': nombre,
        'tipo': tipo,
        'inicioLat': inicioLat,
        'inicioLng': inicioLng,
        'inicioLabel': inicioLabel,
        'finLat': finLat,
        'finLng': finLng,
        'finLabel': finLabel,
        'paradas': jsonEncode(paradas.map((p) => p.toJson()).toList()),
        'distanciaKm': distanciaKm,
        'duracionMin': duracionMin,
      };
}
