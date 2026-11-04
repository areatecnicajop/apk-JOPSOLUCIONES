import '../../domain/entities/prospecto.dart';

class ParadaEnCurso {
  final Prospecto prospecto;
  final int orden;
  bool visitada;
  ParadaEnCurso({required this.prospecto, required this.orden, this.visitada = false});
}

class RutaEnCurso {
  final String? rutaUuid;
  final List<ParadaEnCurso> paradas;
  RutaEnCurso({required this.rutaUuid, required this.paradas});
}

// Handoff entre RutasScreen (donde se arma la ruta) y la burbuja flotante /
// pantalla de registro rápido (a las que se vuelve después de abrir Google
// Maps). No usa Riverpod porque RutasScreen no es ConsumerWidget y esto es
// solo un traspaso de datos entre dos puntos de entrada, no estado de la app.
class RutaEnCursoHolder {
  static RutaEnCurso? actual;
}
