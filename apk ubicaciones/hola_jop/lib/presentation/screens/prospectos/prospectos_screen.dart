import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../domain/entities/prospecto.dart';
import '../../../data/services/prospecto_service.dart';

// Lista + mapa de prospectos. La planificación de rutas vive en su propia
// pestaña (RutasScreen).

class ProspectosScreen extends StatefulWidget {
  const ProspectosScreen({super.key});
  @override
  State<ProspectosScreen> createState() => _ProspectosScreenState();
}

class _ProspectosScreenState extends State<ProspectosScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabs;

  // Data
  List<Prospecto> _todos = [];
  List<Prospecto> _filtrados = [];
  List<Prospecto> _conUbicacion = [];
  List<String> _rubros = [];
  String? _rubroSeleccionado;
  String _busqueda = '';
  bool _cargando = true;
  String? _error;
  final _searchCtrl = TextEditingController();
  final _mapCtrl = MapController();

  // ── Color map ─────────────────────────────────────────────────────────────
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
    _tabs = TabController(length: 2, vsync: this);
    _cargar();
  }

  @override
  void dispose() {
    _tabs.dispose();
    _searchCtrl.dispose();
    super.dispose();
  }

  Future<void> _cargar() async {
    setState(() { _cargando = true; _error = null; });
    try {
      debugPrint('[Prospectos] Cargando desde ${ProspectoService.baseUrl}...');
      final todos = await ProspectoService.getProspectos();
      debugPrint('[Prospectos] OK: ${todos.length} total, ${todos.where((p) => p.tieneUbicacion).length} con ubicación');
      setState(() {
        _todos        = todos;
        _conUbicacion = todos.where((p) => p.tieneUbicacion).toList();
        _rubros       = {...todos.map((p) => p.rubro)}.toList()..sort();
        _cargando     = false;
        _aplicarFiltro();
      });
    } catch (e, st) {
      debugPrint('[Prospectos] ERROR: $e\n$st');
      setState(() { _cargando = false; _error = e.toString(); });
    }
  }

  void _aplicarFiltro() {
    _filtrados = _todos.where((p) {
      final matchRubro = _rubroSeleccionado == null || p.rubro == _rubroSeleccionado;
      final matchBusq  = _busqueda.isEmpty ||
          p.nombre.toLowerCase().contains(_busqueda.toLowerCase()) ||
          (p.comuna?.toLowerCase().contains(_busqueda.toLowerCase()) ?? false);
      return matchRubro && matchBusq;
    }).toList();
  }

  List<Prospecto> get _ubicadosFiltrados => _conUbicacion.where((p) =>
      p.tieneUbicacion &&
      (_rubroSeleccionado == null || p.rubro == _rubroSeleccionado)).toList();

  // ══ BUILD ════════════════════════════════════════════════════════════════

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          const Text('Clientes Potenciales'),
          if (!_cargando)
            Text('${_todos.length} negocios · ${_conUbicacion.length} en mapa',
                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.normal)),
        ]),
        bottom: TabBar(
          controller: _tabs,
          tabs: const [
            Tab(icon: Icon(Icons.list), text: 'Lista'),
            Tab(icon: Icon(Icons.map), text: 'Mapa'),
          ],
        ),
      ),
      body: _cargando
          ? const Center(child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [CircularProgressIndicator(), SizedBox(height: 12),
                         Text('Cargando prospectos...')]))
          : _error != null
              ? _buildError()
              : Column(children: [
                  _buildFiltros(),
                  Expanded(child: TabBarView(
                    controller: _tabs,
                    children: [_buildLista(), _buildMapa()],
                  )),
                ]),
    );
  }

  Widget _buildError() => Center(child: Padding(
    padding: const EdgeInsets.all(24),
    child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
      const Icon(Icons.cloud_off, size: 48, color: Colors.orange),
      const SizedBox(height: 12),
      const Text('Sin conexión al backend',
          style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
      const SizedBox(height: 8),
      Text(_error ?? '', style: const TextStyle(fontSize: 12, color: Colors.grey),
          textAlign: TextAlign.center),
      const SizedBox(height: 16),
      FilledButton.icon(onPressed: _cargar, icon: const Icon(Icons.refresh),
          label: const Text('Reintentar')),
    ]),
  ));

  Widget _buildFiltros() => Container(
    color: Theme.of(context).colorScheme.surface,
    padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
    child: Column(children: [
      TextField(
        controller: _searchCtrl,
        decoration: InputDecoration(
          hintText: 'Buscar por nombre o comuna...',
          prefixIcon: const Icon(Icons.search, size: 20),
          suffixIcon: _busqueda.isNotEmpty
              ? IconButton(icon: const Icon(Icons.clear, size: 18),
                  onPressed: () {
                    _searchCtrl.clear();
                    setState(() { _busqueda = ''; _aplicarFiltro(); });
                  })
              : null,
          isDense: true, filled: true,
          fillColor: Theme.of(context).colorScheme.surfaceContainerHighest,
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(10),
              borderSide: BorderSide.none),
          contentPadding: const EdgeInsets.symmetric(vertical: 8, horizontal: 12),
        ),
        onChanged: (v) => setState(() { _busqueda = v; _aplicarFiltro(); }),
      ),
      const SizedBox(height: 6),
      SizedBox(height: 34, child: ListView(
        scrollDirection: Axis.horizontal,
        children: [
          Padding(padding: const EdgeInsets.only(right: 6), child: FilterChip(
            label: const Text('Todos', style: TextStyle(fontSize: 12)),
            selected: _rubroSeleccionado == null,
            onSelected: (_) => setState(() { _rubroSeleccionado = null; _aplicarFiltro(); }),
          )),
          ..._rubros.map((r) => Padding(padding: const EdgeInsets.only(right: 6),
            child: FilterChip(
              label: Text(r, style: const TextStyle(fontSize: 12)),
              selected: _rubroSeleccionado == r,
              selectedColor: _colorRubro(r).withOpacity(0.25),
              onSelected: (v) => setState(() {
                _rubroSeleccionado = v ? r : null;
                _aplicarFiltro();
              }),
            ))),
        ],
      )),
    ]),
  );

  // ─── Lista ────────────────────────────────────────────────────────────────

  Widget _buildLista() {
    if (_filtrados.isEmpty) return const Center(child: Text('Sin resultados'));
    return ListView.builder(
      padding: const EdgeInsets.all(8),
      itemCount: _filtrados.length,
      itemBuilder: (_, i) => _ProspectoTile(
        prospecto: _filtrados[i],
        color: _colorRubro(_filtrados[i].rubro),
        onMapTap: () {
          _tabs.animateTo(1);
          if (_filtrados[i].tieneUbicacion) {
            Future.delayed(const Duration(milliseconds: 300), () {
              _mapCtrl.move(
                  LatLng(_filtrados[i].latitud!, _filtrados[i].longitud!), 16);
            });
          }
        },
      ),
    );
  }

  // ─── Mapa ─────────────────────────────────────────────────────────────────

  Widget _buildMapa() {
    final prospectos = _ubicadosFiltrados;

    return FlutterMap(
      mapController: _mapCtrl,
      options: const MapOptions(
        initialCenter: LatLng(-33.45, -70.67),
        initialZoom: 11,
      ),
      children: [
        TileLayer(
          urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
          userAgentPackageName: 'com.example.jop_ubicaciones',
        ),
        MarkerLayer(markers: prospectos.map((p) => Marker(
          point: LatLng(p.latitud!, p.longitud!),
          width: 36, height: 36,
          child: GestureDetector(
            onTap: () => _mostrarDetalleProspecto(p),
            child: Container(
              decoration: BoxDecoration(
                color: _colorRubro(p.rubro), shape: BoxShape.circle,
                border: Border.all(color: Colors.white, width: 2),
                boxShadow: const [BoxShadow(
                    color: Colors.black26, blurRadius: 4, offset: Offset(0, 2))],
              ),
              child: const Icon(Icons.business, color: Colors.white, size: 18),
            ),
          ),
        )).toList()),
      ],
    );
  }

  void _mostrarDetalleProspecto(Prospecto p) => showModalBottomSheet(
    context: context,
    builder: (_) => _DetalleProspecto(prospecto: p, color: _colorRubro(p.rubro)),
  );
}

// ─── Prospecto Tile ───────────────────────────────────────────────────────────

class _ProspectoTile extends StatelessWidget {
  final Prospecto prospecto;
  final Color color;
  final VoidCallback onMapTap;
  const _ProspectoTile({required this.prospecto, required this.color, required this.onMapTap});

  @override
  Widget build(BuildContext context) {
    final p = prospecto;
    return Card(
      margin: const EdgeInsets.symmetric(vertical: 3),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => showModalBottomSheet(
          context: context, isScrollControlled: true,
          builder: (_) => _DetalleProspecto(prospecto: p, color: color),
        ),
        child: Padding(
          padding: const EdgeInsets.all(10),
          child: Row(children: [
            Container(
              width: 40, height: 40,
              decoration: BoxDecoration(color: color.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(8)),
              child: Icon(Icons.business, color: color, size: 22),
            ),
            const SizedBox(width: 10),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(p.nombre,
                  style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                  maxLines: 2, overflow: TextOverflow.ellipsis),
              const SizedBox(height: 2),
              Row(children: [
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(color: color.withOpacity(0.12),
                      borderRadius: BorderRadius.circular(4)),
                  child: Text(p.rubro, style: TextStyle(fontSize: 10, color: color,
                      fontWeight: FontWeight.w600)),
                ),
                if (p.comuna != null) ...[
                  const SizedBox(width: 6),
                  Icon(Icons.location_on, size: 12, color: Colors.grey.shade500),
                  Text(p.comuna!, style: TextStyle(fontSize: 11,
                      color: Colors.grey.shade600)),
                ],
              ]),
              if (p.telefono != null)
                Padding(padding: const EdgeInsets.only(top: 2),
                  child: Text(p.telefono!,
                      style: TextStyle(fontSize: 11, color: Colors.grey.shade700))),
            ])),
            Column(children: [
              if (p.telefono != null) IconButton(
                icon: const Icon(Icons.phone, size: 20), color: Colors.green,
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                onPressed: () => launchUrl(Uri.parse('tel:${p.telefono}')),
              ),
              if (p.tieneUbicacion) IconButton(
                icon: const Icon(Icons.map_outlined, size: 20), color: Colors.blue,
                padding: EdgeInsets.zero,
                constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                onPressed: onMapTap,
              ),
            ]),
          ]),
        ),
      ),
    );
  }
}

// ─── Detalle bottom sheet ─────────────────────────────────────────────────────

class _DetalleProspecto extends StatelessWidget {
  final Prospecto prospecto;
  final Color color;
  const _DetalleProspecto({required this.prospecto, required this.color});

  @override
  Widget build(BuildContext context) {
    final p = prospecto;
    return Container(
      padding: const EdgeInsets.all(20),
      child: Column(mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start, children: [
        Center(child: Container(width: 40, height: 4,
            decoration: BoxDecoration(color: Colors.grey.shade300,
                borderRadius: BorderRadius.circular(2)))),
        const SizedBox(height: 16),
        Row(children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(color: color,
                borderRadius: BorderRadius.circular(20)),
            child: Text(p.rubro, style: const TextStyle(color: Colors.white,
                fontWeight: FontWeight.bold, fontSize: 12)),
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
            _ActionBtn(icon: Icons.chat, label: 'WhatsApp',
              color: const Color(0xFF25D366),
              onTap: () {
                final num = p.telefono!.replaceAll(RegExp(r'[^\d]'), '');
                launchUrl(Uri.parse('https://wa.me/$num'),
                    mode: LaunchMode.externalApplication);
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
  const _ActionBtn({required this.icon, required this.label,
      required this.color, required this.onTap});

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
        Text(label, style: TextStyle(color: color,
            fontWeight: FontWeight.w600, fontSize: 13)),
      ]),
    ),
  );
}
