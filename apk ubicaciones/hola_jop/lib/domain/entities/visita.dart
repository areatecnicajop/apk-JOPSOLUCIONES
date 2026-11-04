class Visita {
  final int? id;
  final String rutaUuid;
  final int? prospectoId;
  final String prospectoNombre;
  final String fecha;
  final String? nota;

  const Visita({
    this.id,
    required this.rutaUuid,
    this.prospectoId,
    required this.prospectoNombre,
    this.fecha = '',
    this.nota,
  });

  factory Visita.fromJson(Map<String, dynamic> json) => Visita(
        id: (json['id'] as num?)?.toInt(),
        rutaUuid: json['rutaUuid']?.toString() ?? '',
        prospectoId: (json['prospectoId'] as num?)?.toInt(),
        prospectoNombre: json['prospectoNombre']?.toString() ?? '',
        fecha: json['fecha']?.toString() ?? '',
        nota: json['nota']?.toString(),
      );

  Map<String, dynamic> toJson() => {
        'prospectoId': prospectoId,
        'prospectoNombre': prospectoNombre,
        'nota': nota,
      };
}
