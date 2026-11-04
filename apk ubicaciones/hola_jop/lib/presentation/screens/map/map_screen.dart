import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:latlong2/latlong.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../domain/entities/client.dart';
import '../../../domain/entities/category.dart';
import '../../../domain/entities/prospecto.dart';
import '../../../data/services/prospecto_service.dart';
import '../../providers/providers.dart';
import '../clients/client_form_screen.dart';
import '../../widgets/map/client_marker_popup.dart';

// Mapa de navegación/exploración: clientes, prospectos, ubicación propia.
// La planificación de rutas vive en su propia pestaña (RutasScreen).

class MapScreen extends ConsumerStatefulWidget {
  const MapScreen({super.key});
  @override
  ConsumerState<MapScreen> createState() => _MapScreenState();
}

class _MapScreenState extends ConsumerState<MapScreen> {
  final _mapController = MapController();
  bool _loadingLocation = false;

  // Prospectos
  List<Prospecto> _prospectos = [];
  List<Prospecto> _conUbicacion = [];
  bool _mostrarProspectos = true;

  // Rubro colors
  static const Map<String, Color> _colores = {
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

  Color _colorRubro(String rubro) => _colores.entries
      .firstWhere((e) => rubro.contains(e.key) || e.key.contains(rubro),
          orElse: () => const MapEntry('', Colors.grey))
      .value;

  @override
  void initState() {
    super.initState();
    _cargarProspectos();
  }

  @override
  void dispose() {
    _mapController.dispose();
    super.dispose();
  }

  Future<void> _cargarProspectos() async {
    try {
      final todos = await ProspectoService.getProspectos();
      if (mounted) {
        setState(() {
          _prospectos   = todos;
          _conUbicacion = todos.where((p) => p.tieneUbicacion).toList();
        });
      }
    } catch (e) {
      debugPrint('[MapScreen] Error cargando prospectos: $e');
    }
  }

  Future<void> _goToMyLocation() async {
    setState(() => _loadingLocation = true);
    final loc = await ref.read(locationProvider.future);
    setState(() => _loadingLocation = false);
    if (loc != null) {
      _mapController.move(loc, 15);
      ref.read(userLocationProvider.notifier).state = loc;
    } else if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('No se pudo obtener la ubicación')));
    }
  }

  Future<void> _onLongPress(TapPosition _, LatLng point) async {
    final client = await Navigator.push<Client>(context,
      MaterialPageRoute(builder: (_) => ClientFormScreen(
        initialLat: point.latitude, initialLng: point.longitude)));
    if (client != null) {
      _mapController.move(LatLng(client.latitude, client.longitude),
        _mapController.camera.zoom);
    }
  }

  // ── BUILD ─────────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final clients   = ref.watch(filteredClientsProvider);
    final categories = ref.watch(categoriesProvider);
    final userLoc   = ref.watch(userLocationProvider);
    final center    = ref.watch(mapCenterProvider);

    final catMap = categories.whenData((cats) =>
      Map.fromEntries(cats.map((c) => MapEntry(c.uuid, c)))).valueOrNull ?? {};

    return Scaffold(
      body: Stack(children: [
        // ── Mapa ──────────────────────────────────────────────────────────────
        FlutterMap(
          mapController: _mapController,
          options: MapOptions(
            initialCenter: center,
            initialZoom: ref.read(mapZoomProvider),
            onLongPress: _onLongPress,
            onMapEvent: (e) {
              if (e is MapEventMoveEnd) {
                ref.read(mapCenterProvider.notifier).state = e.camera.center;
                ref.read(mapZoomProvider.notifier).state = e.camera.zoom;
              }
            },
          ),
          children: [
            TileLayer(
              urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
              userAgentPackageName: 'com.jop.jop_ubicaciones',
              maxZoom: 19,
            ),

            // Mi ubicación
            if (userLoc != null)
              MarkerLayer(markers: [Marker(
                point: userLoc, width: 32, height: 32,
                child: const Icon(Icons.my_location, color: Colors.blue, size: 32),
              )]),

            // Marcadores de clientes
            clients.when(
              data: (list) => MarkerLayer(
                markers: list.map((client) {
                  final cat = client.categoryId != null ? catMap[client.categoryId] : null;
                  return Marker(
                    point: LatLng(client.latitude, client.longitude),
                    width: 40, height: 46,
                    child: GestureDetector(
                      onTap: () => _showClientPopup(context, client, cat),
                      child: _ClientMarker(client: client, category: cat),
                    ),
                  );
                }).toList(),
              ),
              loading: () => const MarkerLayer(markers: []),
              error: (_, __) => const MarkerLayer(markers: []),
            ),

            // Marcadores de prospectos
            if (_mostrarProspectos)
              MarkerLayer(markers: _conUbicacion.map((p) => Marker(
                point: LatLng(p.latitud!, p.longitud!),
                width: 32, height: 32,
                child: GestureDetector(
                  onTap: () => _mostrarDetalleProspecto(p),
                  child: Container(
                    decoration: BoxDecoration(
                      color: _colorRubro(p.rubro), shape: BoxShape.circle,
                      border: Border.all(color: Colors.white, width: 1.5),
                      boxShadow: const [BoxShadow(color: Colors.black26, blurRadius: 3)],
                    ),
                    child: const Icon(Icons.business, color: Colors.white, size: 16),
                  ),
                ),
              )).toList()),
          ],
        ),

        // ── Filtros activos clientes ────────────────────────────────────────
        Positioned(
          top: MediaQuery.of(context).padding.top + 8,
          left: 12, right: 64,
          child: _ActiveFilterBar(),
        ),

        // ── Botón GPS ───────────────────────────────────────────────────────
        Positioned(
          top: MediaQuery.of(context).padding.top + 8, right: 12,
          child: _MapIconButton(
            icon: _loadingLocation
              ? const SizedBox(width: 20, height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
              : const Icon(Icons.my_location, color: Colors.white),
            onTap: _loadingLocation ? null : _goToMyLocation,
            tooltip: 'Mi ubicación',
          ),
        ),

        // ── Toggle prospectos ────────────────────────────────────────────────
        if (_conUbicacion.isNotEmpty)
          Positioned(
            top: MediaQuery.of(context).padding.top + 60, right: 12,
            child: _MapIconButton(
              icon: Icon(
                _mostrarProspectos ? Icons.business : Icons.business_outlined,
                color: Colors.white, size: 20),
              onTap: () => setState(() => _mostrarProspectos = !_mostrarProspectos),
              tooltip: _mostrarProspectos ? 'Ocultar leads' : 'Mostrar leads',
            ),
          ),

        // ── FAB nuevo cliente ─────────────────────────────────────────────────
        Positioned(
          bottom: 24, right: 16,
          child: FloatingActionButton.extended(
            heroTag: 'nuevo-fab',
            onPressed: () => Navigator.push(context,
              MaterialPageRoute(builder: (_) => const ClientFormScreen())),
            icon: const Icon(Icons.add_location_alt),
            label: const Text('Nuevo'),
          ),
        ),

        // ── Contador ─────────────────────────────────────────────────────────
        clients.when(
          data: (list) => Positioned(
            bottom: 32, left: 16,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: Colors.black54, borderRadius: BorderRadius.circular(20)),
              child: Text(
                '${list.length} clientes · ${_conUbicacion.length} leads',
                style: const TextStyle(color: Colors.white, fontSize: 12)),
            ),
          ),
          loading: () => const SizedBox(),
          error: (_, __) => const SizedBox(),
        ),
      ]),
    );
  }

  void _showClientPopup(BuildContext context, Client client, Category? cat) {
    showModalBottomSheet(
      context: context, isScrollControlled: true,
      builder: (_) => ProviderScope(
        parent: ProviderScope.containerOf(context),
        child: ClientMarkerPopup(client: client, category: cat),
      ),
    );
  }

  void _mostrarDetalleProspecto(Prospecto p) => showModalBottomSheet(
    context: context, isScrollControlled: true,
    builder: (_) => _DetalleProspecto(prospecto: p, color: _colorRubro(p.rubro)),
  );
}

// ── Marker visual clientes ────────────────────────────────────────────────────

class _ClientMarker extends StatelessWidget {
  final Client client;
  final Category? category;
  const _ClientMarker({required this.client, this.category});

  @override
  Widget build(BuildContext context) {
    final color = category?.color ?? _statusColor(client.status);
    return Column(mainAxisSize: MainAxisSize.min, children: [
      Container(
        width: 34, height: 34,
        decoration: BoxDecoration(
          color: color, shape: BoxShape.circle,
          border: Border.all(color: Colors.white, width: 2),
          boxShadow: [BoxShadow(color: color.withOpacity(0.4), blurRadius: 4, offset: const Offset(0, 2))],
        ),
        child: Center(
          child: Text(
            client.name.isNotEmpty ? client.name[0].toUpperCase() : '?',
            style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 15),
          ),
        ),
      ),
      CustomPaint(size: const Size(12, 7), painter: _TrianglePainter(color)),
    ]);
  }

  Color _statusColor(String s) => switch (s) {
    'active'   => Colors.green.shade600,
    'inactive' => Colors.grey.shade600,
    'prospect' => Colors.orange.shade600,
    'lead'     => Colors.blue.shade600,
    _          => Colors.grey,
  };
}

class _TrianglePainter extends CustomPainter {
  final Color color;
  _TrianglePainter(this.color);
  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()..color = color;
    final path = ui.Path()
      ..moveTo(0, 0)
      ..lineTo(size.width, 0)
      ..lineTo(size.width / 2, size.height)
      ..close();
    canvas.drawPath(path, paint);
  }
  @override
  bool shouldRepaint(_) => false;
}

// ── Botón mapa ────────────────────────────────────────────────────────────────

class _MapIconButton extends StatelessWidget {
  final Widget icon;
  final VoidCallback? onTap;
  final String tooltip;
  const _MapIconButton({required this.icon, required this.onTap, required this.tooltip});

  @override
  Widget build(BuildContext context) => Tooltip(
    message: tooltip,
    child: GestureDetector(
      onTap: onTap,
      child: Container(
        width: 44, height: 44,
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.primary,
          borderRadius: BorderRadius.circular(8),
          boxShadow: const [BoxShadow(color: Colors.black26, blurRadius: 4)],
        ),
        child: Center(child: icon),
      ),
    ),
  );
}

// ── Barra filtros activos ─────────────────────────────────────────────────────

class _ActiveFilterBar extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final filter = ref.watch(clientFilterProvider);
    if (!filter.isActive) return const SizedBox();
    return Card(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        child: Row(children: [
          const Icon(Icons.filter_list, size: 16),
          const SizedBox(width: 6),
          const Expanded(child: Text('Filtros activos', style: TextStyle(fontSize: 12))),
          GestureDetector(
            onTap: () => ref.read(clientFilterProvider.notifier).reset(),
            child: const Icon(Icons.close, size: 16),
          ),
        ]),
      ),
    );
  }
}

// ── Detalle prospecto bottom sheet ────────────────────────────────────────────

class _DetalleProspecto extends StatelessWidget {
  final Prospecto prospecto;
  final Color color;
  const _DetalleProspecto({required this.prospecto, required this.color});

  @override
  Widget build(BuildContext context) {
    final p = prospecto;
    return Container(
      padding: const EdgeInsets.all(20),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start,
          children: [
        Center(child: Container(width: 40, height: 4,
            decoration: BoxDecoration(color: Colors.grey.shade300,
                borderRadius: BorderRadius.circular(2)))),
        const SizedBox(height: 16),
        Row(children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(20)),
            child: Text(p.rubro, style: const TextStyle(color: Colors.white,
                fontWeight: FontWeight.bold, fontSize: 12)),
          ),
          const Spacer(),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(color: Colors.blue.withOpacity(0.1),
                border: Border.all(color: Colors.blue.withOpacity(0.4)),
                borderRadius: BorderRadius.circular(8)),
            child: const Text('Lead', style: TextStyle(color: Colors.blue,
                fontSize: 11, fontWeight: FontWeight.bold)),
          ),
        ]),
        const SizedBox(height: 8),
        Text(p.nombre, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
        if (p.direccionCompleta.isNotEmpty)
          Padding(padding: const EdgeInsets.only(top: 4),
            child: Row(children: [
              Icon(Icons.location_on, size: 14, color: Colors.grey.shade500),
              const SizedBox(width: 4),
              Expanded(child: Text(p.direccionCompleta,
                  style: TextStyle(color: Colors.grey.shade700, fontSize: 13))),
            ])),
        const SizedBox(height: 16),
        const Divider(),
        const SizedBox(height: 8),
        Wrap(spacing: 10, runSpacing: 10, children: [
          if (p.telefono != null) ...[
            _ActionBtn(icon: Icons.phone, label: 'Llamar', color: Colors.green,
              onTap: () => launchUrl(Uri.parse('tel:${p.telefono}'))),
            _ActionBtn(icon: Icons.chat, label: 'WhatsApp', color: const Color(0xFF25D366),
              onTap: () {
                final num = p.telefono!.replaceAll(RegExp(r'[^\d]'), '');
                launchUrl(Uri.parse('https://wa.me/$num'), mode: LaunchMode.externalApplication);
              }),
          ],
          if (p.email != null)
            _ActionBtn(icon: Icons.email, label: 'Email', color: Colors.orange,
              onTap: () => launchUrl(Uri.parse('mailto:${p.email}'))),
          _ActionBtn(icon: Icons.map, label: 'Ver en Maps', color: Colors.blue,
            onTap: () => launchUrl(Uri.parse(p.googleMapsUrl),
                mode: LaunchMode.externalApplication)),
        ]),
        SizedBox(height: MediaQuery.of(context).viewInsets.bottom + 8),
      ]),
    );
  }
}

class _ActionBtn extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;
  const _ActionBtn({required this.icon, required this.label, required this.color, required this.onTap});

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap,
    borderRadius: BorderRadius.circular(12),
    child: Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: BoxDecoration(
        color: color.withOpacity(0.1),
        border: Border.all(color: color.withOpacity(0.4)),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(icon, size: 18, color: color),
        const SizedBox(width: 6),
        Text(label, style: TextStyle(color: color, fontWeight: FontWeight.w600, fontSize: 13)),
      ]),
    ),
  );
}
