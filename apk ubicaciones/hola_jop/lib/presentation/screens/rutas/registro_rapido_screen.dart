import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../data/services/ruta_en_curso.dart';
import '../../../data/services/ruta_service.dart';
import '../../widgets/lead_bottom_sheet.dart';
import '../../../core/ruta_notification.dart';

const Map<String, Color> _coloresRubro = {
  'Mecánico':             Color(0xFF2196F3),
  'Funeraria':            Color(0xFF9C27B0),
  'Mueblería':            Color(0xFF4CAF50),
  'Barraca':              Color(0xFFFF9800),
  'Pintura Automotriz':   Color(0xFFFF5722),
  'Desabolladura':        Color(0xFFF44336),
  'Taller Automotriz':    Color(0xFF03A9F4),
  'Taller de Pintura':    Color(0xFFFF7043),
  'Carpintería':          Color(0xFF8BC34A),
  'Ebanistería':          Color(0xFF33691E),
  'Vulcanización':        Color(0xFFFFC107),
  'Tapicería Automotriz': Color(0xFF00BCD4),
  'Hojalatería':          Color(0xFF78909C),
  'Ferretería':           Color(0xFFE91E63),
};

Color _colorRubro(String? rubro) => _coloresRubro.entries
    .firstWhere((e) => (rubro ?? '').contains(e.key) || e.key.contains(rubro ?? ''),
        orElse: () => const MapEntry('', Colors.grey))
    .value;

// Se muestra al volver a la app tocando la burbuja flotante mientras se
// navega por Google Maps: lista rápida de paradas para registrar la visita
// (o convertir a cliente) sin tener que ir a buscar la ruta de nuevo.
class RegistroRapidoScreen extends ConsumerStatefulWidget {
  const RegistroRapidoScreen({super.key});

  @override
  ConsumerState<RegistroRapidoScreen> createState() => _RegistroRapidoScreenState();
}

class _RegistroRapidoScreenState extends ConsumerState<RegistroRapidoScreen> {
  RutaEnCurso? get _ruta => RutaEnCursoHolder.actual;

  void _abrirParada(ParadaEnCurso p) {
    mostrarLeadBottomSheet(
      context,
      prospecto: p.prospecto,
      color: _colorRubro(p.prospecto.rubro),
      mostrarRegistrarVisita: !p.visitada,
      onRegistrarVisita: p.visitada ? null : (nota) => _registrar(p, nota),
      onConvertido: () {
        if (!p.visitada) _registrar(p, 'Convertido a cliente');
      },
    );
  }

  Future<void> _registrar(ParadaEnCurso p, String nota) async {
    final rutaUuid = _ruta?.rutaUuid;
    try {
      if (rutaUuid != null) {
        await RutaService.registrarVisita(
          rutaUuid,
          prospectoId: p.prospecto.id,
          prospectoNombre: p.prospecto.nombre,
          nota: nota.isNotEmpty ? nota : null,
        );
      }
      setState(() => p.visitada = true);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('✅ ${p.prospecto.nombre} marcada como visitada')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error registrando la visita: $e'), backgroundColor: Colors.red));
      }
    }
  }

  Future<void> _terminarRuta() async {
    await RutaNotification.ocultar();
    RutaEnCursoHolder.actual = null;
    if (mounted) Navigator.of(context).pop();
  }

  @override
  Widget build(BuildContext context) {
    final ruta = _ruta;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Registrar visita'),
        actions: [
          IconButton(icon: const Icon(Icons.close), tooltip: 'Terminar ruta', onPressed: _terminarRuta),
        ],
      ),
      body: ruta == null || ruta.paradas.isEmpty
          ? const Center(child: Text('No hay una ruta activa'))
          : ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: ruta.paradas.length,
              itemBuilder: (_, i) {
                final p = ruta.paradas[i];
                return Card(
                  margin: const EdgeInsets.only(bottom: 8),
                  child: ListTile(
                    onTap: () => _abrirParada(p),
                    leading: CircleAvatar(
                      backgroundColor: p.visitada ? Colors.green : _colorRubro(p.prospecto.rubro),
                      child: p.visitada
                          ? const Icon(Icons.check, color: Colors.white, size: 18)
                          : Text('${p.orden}', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
                    ),
                    title: Text(p.prospecto.nombre),
                    subtitle: Text([p.prospecto.rubro, if (p.prospecto.comuna != null) p.prospecto.comuna!].join(' · ')),
                    trailing: const Icon(Icons.chevron_right),
                  ),
                );
              },
            ),
    );
  }
}
