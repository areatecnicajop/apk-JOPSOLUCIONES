import 'dart:convert';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:http/http.dart' as http;
import 'package:intl/intl.dart';
import 'package:uuid/uuid.dart';
import 'package:url_launcher/url_launcher.dart';
import '../../../core/app_config.dart';
import '../../../domain/entities/prospecto.dart';
import '../../../domain/entities/ruta.dart';
import '../../../data/services/prospecto_service.dart';
import '../../../data/services/ruta_service.dart';
import '../../widgets/address_search_field.dart';
import '../../../core/ruta_notification.dart';
import '../../../data/services/ruta_en_curso.dart';

// Pantalla dedicada de planificación de rutas — reemplaza al planificador
// que vivía embebido en Mapa/Prospectos. Reutiliza el mismo backend
// (/api/rutas) que ya usa el panel de Angular.

class _ParadaCalc {
  final Prospecto prospecto;
  final int orden;
  _ParadaCalc({required this.prospecto, required this.orden});
}

class _RutaCalculada {
  final int id;
  final String nombre;
  final String descripcion;
  final String icono;
  final Color color;
  List<_ParadaCalc> paradas;
  double? distanciaKm;
  int? duracionMin;
  bool cargando;
  bool error;
  List<LatLng> polylinePoints;

  _RutaCalculada({
    required this.id,
    required this.nombre,
    required this.descripcion,
    required this.icono,
    required this.color,
    this.paradas = const [],
    this.distanciaKm,
    this.duracionMin,
    this.cargando = true,
    this.error = false,
    this.polylinePoints = const [],
  });
}

class RutasScreen extends StatefulWidget {
  const RutasScreen({super.key});
  @override
  State<RutasScreen> createState() => _RutasScreenState();
}

class _RutasScreenState extends State<RutasScreen> with SingleTickerProviderStateMixin {
  late final TabController _tabController;
  final _mapController = MapController();

  List<Prospecto> _prospectos = [];
  List<Prospecto> _conUbicacion = [];

  LatLng? _puntoInicio;
  LatLng? _puntoFin;
  String _labelInicio = '';
  String _labelFin = '';
  String _modoCalculo = 'auto'; // auto | manual
  Set<String> _rubrosFiltro = {}; // vacío = todos los rubros
  int _maxParadas = 8;
  bool _calculando = false;
  List<Prospecto> _paradasManuales = [];

  List<_RutaCalculada> _rutas = [];
  int? _rutaSeleccionadaId;

  bool _guardando = false;
  bool _mostrarFormGuardar = false;
  final _nombreCtrl = TextEditingController();
  String? _rutaGuardadaMsg;
  bool _rutaGuardadaMsgError = false;
  String? _rutaCargadaUuid;

  bool _cargandoGuardadas = false;
  List<Ruta> _rutasGuardadas = [];

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

  Color _colorRubro(String? rubro) => _colores.entries
      .firstWhere((e) => (rubro ?? '').contains(e.key) || e.key.contains(rubro ?? ''),
          orElse: () => const MapEntry('', Colors.grey))
      .value;

  List<String> get _rubros => {..._prospectos.map((p) => p.rubro)}.toList()..sort();

  _RutaCalculada? get _rutaActiva => _rutaSeleccionadaId != null && _rutas.isNotEmpty
      ? _rutas.firstWhere((r) => r.id == _rutaSeleccionadaId, orElse: () => _rutas.first)
      : null;

  // En modo automático, el filtro de rubro también reduce lo que se ve en el
  // mapa (antes solo afectaba el cálculo, sin dar ningún indicio visual).
  List<Prospecto> get _prospectosVisiblesMapa => _modoCalculo == 'auto' && _rubrosFiltro.isNotEmpty
      ? _conUbicacion.where((p) => _rubrosFiltro.contains(p.rubro)).toList()
      : _conUbicacion;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _cargarProspectos();
    _cargarGuardadas();
  }

  @override
  void dispose() {
    _tabController.dispose();
    _mapController.dispose();
    _nombreCtrl.dispose();
    super.dispose();
  }

  Future<void> _cargarProspectos() async {
    try {
      final todos = await ProspectoService.getProspectos();
      if (mounted) {
        setState(() {
          _prospectos = todos;
          _conUbicacion = todos.where((p) => p.tieneUbicacion).toList();
        });
      }
    } catch (_) {}
  }

  Future<void> _cargarGuardadas() async {
    setState(() => _cargandoGuardadas = true);
    try {
      final rutas = await RutaService.getAll();
      if (mounted) setState(() { _rutasGuardadas = rutas; _cargandoGuardadas = false; });
    } catch (_) {
      if (mounted) setState(() => _cargandoGuardadas = false);
    }
  }

  // ── Selección de puntos / prospectos en el mapa ────────────────────────────

  void _onMapTap(TapPosition _, LatLng latlng) {
    if (_puntoInicio == null) {
      setState(() { _puntoInicio = latlng; _labelInicio = ''; });
    } else if (_puntoFin == null) {
      setState(() { _puntoFin = latlng; _labelFin = ''; });
    }
  }

  void _onInicioBuscado(String address, LatLng latlng) {
    setState(() { _puntoInicio = latlng; _labelInicio = address; });
    _mapController.move(latlng, 14);
  }

  void _onFinBuscado(String address, LatLng latlng) {
    setState(() { _puntoFin = latlng; _labelFin = address; });
    _mapController.move(latlng, 14);
  }

  bool get _hasGmapsKey => AppConfig.instance.gmapsKey.isNotEmpty;

  bool _esParadaManual(Prospecto p) => _paradasManuales.any((x) => x.id == p.id);

  void _toggleParadaManual(Prospecto p) {
    setState(() {
      if (_esParadaManual(p)) {
        _paradasManuales.removeWhere((x) => x.id == p.id);
      } else {
        _paradasManuales.add(p);
      }
    });
  }

  void _moverParadaManual(int index, int delta) {
    final nuevo = index + delta;
    if (nuevo < 0 || nuevo >= _paradasManuales.length) return;
    setState(() {
      final item = _paradasManuales.removeAt(index);
      _paradasManuales.insert(nuevo, item);
    });
  }

  void _limpiarInicio() => setState(() {
    _puntoInicio = null; _labelInicio = '';
    _puntoFin = null; _labelFin = '';
  });
  void _limpiarFin() => setState(() { _puntoFin = null; _labelFin = ''; });

  // ── Algoritmos (mismo enfoque que el panel de Angular) ─────────────────────

  double _haversine(LatLng a, LatLng b) {
    const r = 6371.0;
    final dLat = (b.latitude - a.latitude) * pi / 180;
    final dLon = (b.longitude - a.longitude) * pi / 180;
    final sinDLat = sin(dLat / 2), sinDLon = sin(dLon / 2);
    final h = sinDLat * sinDLat +
        cos(a.latitude * pi / 180) * cos(b.latitude * pi / 180) * sinDLon * sinDLon;
    return 2 * r * asin(sqrt(h));
  }

  double _distanciaASegmento(LatLng p, LatLng a, LatLng b) {
    final dx = b.longitude - a.longitude, dy = b.latitude - a.latitude;
    if (dx == 0 && dy == 0) return _haversine(p, a);
    final t = ((p.longitude - a.longitude) * dx + (p.latitude - a.latitude) * dy) / (dx * dx + dy * dy);
    final tc = t.clamp(0.0, 1.0);
    return _haversine(p, LatLng(a.latitude + tc * dy, a.longitude + tc * dx));
  }

  double _ladoDeLaLinea(LatLng p, LatLng a, LatLng b) =>
      (b.longitude - a.longitude) * (p.latitude - a.latitude) -
      (b.latitude - a.latitude) * (p.longitude - a.longitude);

  List<Prospecto> _nearestNeighbor(List<Prospecto> pool, LatLng start) {
    final remaining = [...pool];
    final result = <Prospecto>[];
    var current = start;
    while (remaining.isNotEmpty) {
      int bestIdx = 0;
      double bestDist = double.infinity;
      for (int i = 0; i < remaining.length; i++) {
        final d = _haversine(current, LatLng(remaining[i].latitud!, remaining[i].longitud!));
        if (d < bestDist) { bestDist = d; bestIdx = i; }
      }
      result.add(remaining[bestIdx]);
      current = LatLng(remaining[bestIdx].latitud!, remaining[bestIdx].longitud!);
      remaining.removeAt(bestIdx);
    }
    return result;
  }

  Future<(List<LatLng>, double, int)?> _calcularOSRM(List<LatLng> waypoints) async {
    if (waypoints.length < 2) return null;
    final coords = waypoints.map((w) => '${w.longitude},${w.latitude}').join(';');
    try {
      final resp = await http.get(Uri.parse(
        'https://router.project-osrm.org/route/v1/driving/$coords?overview=full&geometries=geojson',
      )).timeout(const Duration(seconds: 10));
      if (resp.statusCode != 200) return null;
      final data  = jsonDecode(resp.body) as Map<String, dynamic>;
      final route = (data['routes'] as List).first as Map<String, dynamic>;
      final distM = (route['distance'] as num).toDouble();
      final durS  = (route['duration'] as num).toDouble();
      final pts = (route['geometry']['coordinates'] as List)
          .map((c) => LatLng((c[1] as num).toDouble(), (c[0] as num).toDouble())).toList();
      return (pts, distM / 1000, (durS / 60).round());
    } catch (_) {
      return null;
    }
  }

  List<Prospecto> _filtrarCorredor(List<Prospecto> pool, LatLng inicio, LatLng fin, double factor,
      {bool Function(LatLng)? sideFilter}) {
    final umbral = _haversine(inicio, fin) * factor;
    return pool.where((p) {
      final pt = LatLng(p.latitud!, p.longitud!);
      if (_distanciaASegmento(pt, inicio, fin) > umbral) return false;
      if (sideFilter != null && !sideFilter(pt)) return false;
      return true;
    }).toList();
  }

  Future<_RutaCalculada> _buildRuta({
    required int id, required String nombre, required String descripcion,
    required String icono, required Color color, required List<Prospecto> candidates,
    required int maxP, required LatLng inicio, required LatLng fin,
  }) async {
    final ordered = _nearestNeighbor(candidates.take(maxP).toList(), inicio);
    final paradas = ordered.asMap().entries.map((e) => _ParadaCalc(prospecto: e.value, orden: e.key + 1)).toList();
    final waypoints = [inicio, ...ordered.map((p) => LatLng(p.latitud!, p.longitud!)), fin];
    final osrm = await _calcularOSRM(waypoints);
    return _RutaCalculada(
      id: id, nombre: nombre, descripcion: descripcion, icono: icono, color: color,
      paradas: paradas,
      distanciaKm: osrm != null ? double.parse(osrm.$2.toStringAsFixed(1)) : null,
      duracionMin: osrm?.$3, cargando: false, error: osrm == null,
      polylinePoints: osrm?.$1 ?? [inicio, fin],
    );
  }

  Future<void> _calcularRutasAuto() async {
    if (_puntoInicio == null || _puntoFin == null) return;
    final inicio = _puntoInicio!, fin = _puntoFin!;
    final pool = _conUbicacion.where((p) => _rubrosFiltro.isEmpty || _rubrosFiltro.contains(p.rubro)).toList();

    setState(() { _calculando = true; _rutas = []; });

    final resultados = await Future.wait([
      _buildRuta(id: 1, nombre: 'Directa', descripcion: 'Ruta más directa, menos desvíos',
        icono: '⚡', color: const Color(0xFF6366F1),
        candidates: _filtrarCorredor(pool, inicio, fin, 0.25), maxP: min(_maxParadas, 10), inicio: inicio, fin: fin),
      _buildRuta(id: 2, nombre: 'Máx.Visitas', descripcion: 'Más paradas, mayor cobertura',
        icono: '📍', color: const Color(0xFF10B981),
        candidates: _filtrarCorredor(pool, inicio, fin, 0.6), maxP: _maxParadas, inicio: inicio, fin: fin),
      _buildRuta(id: 3, nombre: 'Norte', descripcion: 'Por el lado norte del trayecto',
        icono: '⬆️', color: const Color(0xFFF59E0B),
        candidates: _filtrarCorredor(pool, inicio, fin, 0.6, sideFilter: (pt) => _ladoDeLaLinea(pt, inicio, fin) > 0),
        maxP: _maxParadas, inicio: inicio, fin: fin),
      _buildRuta(id: 4, nombre: 'Sur', descripcion: 'Por el lado sur del trayecto',
        icono: '⬇️', color: const Color(0xFFEF4444),
        candidates: _filtrarCorredor(pool, inicio, fin, 0.6, sideFilter: (pt) => _ladoDeLaLinea(pt, inicio, fin) < 0),
        maxP: _maxParadas, inicio: inicio, fin: fin),
    ]);

    if (!mounted) return;
    setState(() { _rutas = resultados; _calculando = false; });
    final mejor = [...resultados]..sort((a, b) => b.paradas.length.compareTo(a.paradas.length));
    if (resultados.isNotEmpty) setState(() => _rutaSeleccionadaId = mejor.first.id);
  }

  Future<void> _calcularRutaManual() async {
    if (_puntoInicio == null || _puntoFin == null || _paradasManuales.isEmpty) return;
    setState(() { _calculando = true; _rutas = []; });

    final paradas = _paradasManuales.asMap().entries
        .map((e) => _ParadaCalc(prospecto: e.value, orden: e.key + 1)).toList();
    final waypoints = [_puntoInicio!, ..._paradasManuales.map((p) => LatLng(p.latitud!, p.longitud!)), _puntoFin!];
    final osrm = await _calcularOSRM(waypoints);

    if (!mounted) return;
    setState(() {
      _rutas = [_RutaCalculada(
        id: 1, nombre: 'Ruta Manual', descripcion: 'Paradas elegidas por ti, en el orden seleccionado',
        icono: '✍️', color: const Color(0xFF6366F1), paradas: paradas,
        distanciaKm: osrm != null ? double.parse(osrm.$2.toStringAsFixed(1)) : null,
        duracionMin: osrm?.$3, cargando: false, error: osrm == null,
        polylinePoints: osrm?.$1 ?? [_puntoInicio!, _puntoFin!],
      )];
      _rutaSeleccionadaId = 1;
      _calculando = false;
    });
  }

  void _nuevaRuta() {
    setState(() {
      _rutas = [];
      _rutaSeleccionadaId = null;
      _puntoInicio = null;
      _puntoFin = null;
      _labelInicio = '';
      _labelFin = '';
      _paradasManuales = [];
      _mostrarFormGuardar = false;
      _rutaGuardadaMsg = null;
      _rutaCargadaUuid = null;
    });
  }

  void _abrirEnGoogleMaps() {
    final ruta = _rutaActiva;
    if (ruta == null || _puntoInicio == null || _puntoFin == null) return;
    final waypts = ruta.paradas.map((p) => '${p.prospecto.latitud},${p.prospecto.longitud}').join('|');
    final url = 'https://www.google.com/maps/dir/?api=1'
        '&origin=${_puntoInicio!.latitude},${_puntoInicio!.longitude}'
        '&destination=${_puntoFin!.latitude},${_puntoFin!.longitude}'
        '&waypoints=$waypts&travelmode=driving';
    launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
  }

  // "Iniciar ruta": abre la navegación real en Google Maps (turn-by-turn de
  // verdad) y deja una notificación para volver rápido a JOP y registrar la
  // visita, sin tener que salir de Maps a buscar la app.
  Future<void> _iniciarNavegacion() async {
    final ruta = _rutaActiva;
    if (ruta == null || ruta.paradas.isEmpty) return;

    RutaEnCursoHolder.actual = RutaEnCurso(
      rutaUuid: _rutaCargadaUuid,
      paradas: ruta.paradas.map((p) => ParadaEnCurso(prospecto: p.prospecto, orden: p.orden)).toList(),
    );

    _abrirEnGoogleMaps();

    await RutaNotification.pedirPermiso();
    await RutaNotification.mostrar(pendientes: ruta.paradas.length);
  }

  // ── Guardar / cargar ─────────────────────────────────────────────────────

  Future<void> _guardarRuta() async {
    final ruta = _rutaActiva;
    if (ruta == null || _puntoInicio == null || _puntoFin == null || _nombreCtrl.text.trim().isEmpty) return;
    setState(() => _guardando = true);

    final paradas = ruta.paradas.map((p) => RutaParada(
      prospectoUuid: p.prospecto.id.toString(),
      nombre: p.prospecto.nombre, rubro: p.prospecto.rubro,
      lat: p.prospecto.latitud!, lng: p.prospecto.longitud!, orden: p.orden,
    )).toList();

    final nueva = Ruta(
      uuid: const Uuid().v4(), nombre: _nombreCtrl.text.trim(), tipo: _modoCalculo,
      inicioLat: _puntoInicio!.latitude, inicioLng: _puntoInicio!.longitude,
      inicioLabel: _labelInicio.isNotEmpty ? _labelInicio : _coordsLabel(_puntoInicio!),
      finLat: _puntoFin!.latitude, finLng: _puntoFin!.longitude,
      finLabel: _labelFin.isNotEmpty ? _labelFin : _coordsLabel(_puntoFin!),
      paradas: paradas, distanciaKm: ruta.distanciaKm, duracionMin: ruta.duracionMin, createdAt: '',
    );

    try {
      final creada = await RutaService.create(nueva);
      if (!mounted) return;
      setState(() {
        _guardando = false; _mostrarFormGuardar = false;
        _rutaGuardadaMsg = '✅ Ruta "${creada.nombre}" guardada';
        _rutaGuardadaMsgError = false;
        _rutaCargadaUuid = creada.uuid;
      });
      _cargarGuardadas();
    } catch (e) {
      if (!mounted) return;
      setState(() { _guardando = false; _rutaGuardadaMsg = '❌ Error al guardar'; _rutaGuardadaMsgError = true; });
    }
  }

  Future<void> _cargarRutaGuardada(Ruta r) async {
    setState(() {
      _rutas = []; _rutaSeleccionadaId = null;
      _puntoInicio = LatLng(r.inicioLat, r.inicioLng);
      _puntoFin = LatLng(r.finLat, r.finLng);
      _labelInicio = r.inicioLabel;
      _labelFin = r.finLabel;
      _modoCalculo = r.tipo;
      _rutaCargadaUuid = r.uuid;
      _calculando = true;
    });
    _tabController.animateTo(0);

    final paradas = r.paradas.map((pg) {
      final real = _prospectos.where((p) => p.id.toString() == pg.prospectoUuid);
      final prospecto = real.isNotEmpty ? real.first : Prospecto(
        id: -1, nombre: pg.nombre, rubro: pg.rubro ?? '', latitud: pg.lat, longitud: pg.lng,
      );
      return _ParadaCalc(prospecto: prospecto, orden: pg.orden);
    }).toList();

    final waypoints = [_puntoInicio!, ...paradas.map((p) => LatLng(p.prospecto.latitud!, p.prospecto.longitud!)), _puntoFin!];
    final osrm = await _calcularOSRM(waypoints);

    if (!mounted) return;
    setState(() {
      _rutas = [_RutaCalculada(
        id: 1, nombre: r.nombre, descripcion: 'Ruta guardada',
        icono: r.tipo == 'manual' ? '✍️' : '⚡', color: const Color(0xFF6366F1),
        paradas: paradas,
        distanciaKm: osrm != null ? double.parse(osrm.$2.toStringAsFixed(1)) : r.distanciaKm,
        duracionMin: osrm?.$3 ?? r.duracionMin, cargando: false, error: osrm == null,
        polylinePoints: osrm?.$1 ?? [_puntoInicio!, _puntoFin!],
      )];
      _rutaSeleccionadaId = 1;
      _calculando = false;
    });
  }

  Future<void> _eliminarRutaGuardada(Ruta r) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Eliminar ruta'),
        content: Text('¿Eliminar "${r.nombre}"?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Eliminar')),
        ],
      ),
    );
    if (confirm != true) return;
    try {
      await RutaService.delete(r.uuid);
      if (mounted) setState(() => _rutasGuardadas.removeWhere((x) => x.uuid == r.uuid));
    } catch (_) {}
  }

  // ── BUILD ────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Rutas'),
        bottom: TabBar(controller: _tabController, tabs: const [
          Tab(text: 'Planificar', icon: Icon(Icons.route, size: 20)),
          Tab(text: 'Guardadas', icon: Icon(Icons.folder, size: 20)),
        ]),
      ),
      body: TabBarView(controller: _tabController, children: [
        _buildPlanificarTab(),
        _buildGuardadasTab(),
      ]),
    );
  }

  Widget _buildPlanificarTab() {
    final mapHeight = MediaQuery.of(context).size.height * 0.36;
    return Column(children: [
      SizedBox(height: max(mapHeight, 220), child: _buildMapa()),
      Expanded(
        child: _rutas.isEmpty ? _buildConfigPanel() : _buildResultadoPanel(),
      ),
    ]);
  }

  Widget _buildMapa() {
    return FlutterMap(
      mapController: _mapController,
      options: MapOptions(
        initialCenter: const LatLng(-33.42719, -70.70964),
        initialZoom: 11,
        onTap: _onMapTap,
      ),
      children: [
        TileLayer(
          urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
          userAgentPackageName: 'com.jop.jop_ubicaciones',
          maxZoom: 19,
        ),

        // Prospectos (solo mientras no hay ruta calculada, respeta el filtro de rubro)
        if (_rutas.isEmpty)
          MarkerLayer(markers: _prospectosVisiblesMapa.map((p) {
            final enManual = _modoCalculo == 'manual' && _esParadaManual(p);
            return Marker(
              point: LatLng(p.latitud!, p.longitud!),
              width: enManual ? 36 : 30, height: enManual ? 36 : 30,
              child: GestureDetector(
                onTap: _modoCalculo == 'manual' ? () => _toggleParadaManual(p) : null,
                child: Container(
                  decoration: BoxDecoration(
                    color: enManual ? const Color(0xFF6366F1) : _colorRubro(p.rubro),
                    shape: BoxShape.circle,
                    border: Border.all(color: Colors.white, width: enManual ? 2.5 : 1.5),
                    boxShadow: const [BoxShadow(color: Colors.black26, blurRadius: 3)],
                  ),
                  child: Icon(Icons.business, color: Colors.white, size: enManual ? 18 : 14),
                ),
              ),
            );
          }).toList()),

        // Ruta calculada
        if (_rutaActiva != null)
          PolylineLayer(polylines: [Polyline(points: _rutaActiva!.polylinePoints, color: _rutaActiva!.color, strokeWidth: 5)]),
        if (_rutaActiva != null)
          MarkerLayer(markers: _rutaActiva!.paradas.map((p) => Marker(
            point: LatLng(p.prospecto.latitud!, p.prospecto.longitud!),
            width: 30, height: 30,
            child: Container(
              decoration: BoxDecoration(
                color: _colorRubro(p.prospecto.rubro), shape: BoxShape.circle,
                border: Border.all(color: Colors.white, width: 2),
                boxShadow: const [BoxShadow(color: Colors.black38, blurRadius: 4)],
              ),
              child: Center(child: Text('${p.orden}',
                  style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.w900))),
            ),
          )).toList()),

        if (_puntoInicio != null)
          MarkerLayer(markers: [Marker(
            point: _puntoInicio!, width: 44, height: 40,
            child: const Column(mainAxisSize: MainAxisSize.min, children: [
              Icon(Icons.circle, color: Colors.green, size: 20),
              Text('Inicio', style: TextStyle(fontSize: 9, color: Colors.green, fontWeight: FontWeight.bold,
                  shadows: [Shadow(color: Colors.white, blurRadius: 3)])),
            ]),
          )]),
        if (_puntoFin != null)
          MarkerLayer(markers: [Marker(
            point: _puntoFin!, width: 36, height: 40,
            child: const Column(mainAxisSize: MainAxisSize.min, children: [
              Icon(Icons.circle, color: Colors.red, size: 20),
              Text('Fin', style: TextStyle(fontSize: 9, color: Colors.red, fontWeight: FontWeight.bold,
                  shadows: [Shadow(color: Colors.white, blurRadius: 3)])),
            ]),
          )]),
      ],
    );
  }

  Widget _buildConfigPanel() {
    return ListView(padding: const EdgeInsets.all(16), children: [
      if (!_hasGmapsKey && (_puntoInicio == null || _puntoFin == null))
        Container(
          margin: const EdgeInsets.only(bottom: 10),
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: Colors.orange.shade50,
            border: Border.all(color: Colors.orange.shade200),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Row(children: [
            Icon(Icons.info_outline, color: Colors.orange.shade800, size: 18),
            const SizedBox(width: 8),
            Expanded(child: Text(
              'No tienes una API Key de Google Maps configurada, así que no puedes escribir direcciones. '
              'Agrégala en Config, o toca el mapa para marcar los puntos.',
              style: TextStyle(fontSize: 12, color: Colors.orange.shade900))),
          ]),
        ),

      // ── Punto de inicio ──────────────────────────────────────
      if (_puntoInicio != null)
        _puntoRow('🟢 Inicio', _labelInicio.isNotEmpty ? _labelInicio : _coordsLabel(_puntoInicio!), _limpiarInicio)
      else if (_hasGmapsKey)
        AddressSearchField(
          label: '🟢 Punto de inicio', accentColor: Colors.green, icon: Icons.trip_origin,
          onSelected: _onInicioBuscado,
        ),

      const SizedBox(height: 10),

      // ── Punto de llegada ─────────────────────────────────────
      if (_puntoFin != null)
        _puntoRow('🔴 Llegada', _labelFin.isNotEmpty ? _labelFin : _coordsLabel(_puntoFin!), _limpiarFin)
      else if (_hasGmapsKey)
        AddressSearchField(
          label: '🔴 Punto de llegada', accentColor: Colors.red, icon: Icons.flag,
          onSelected: _onFinBuscado,
        ),

      if (_puntoInicio == null || _puntoFin == null)
        Padding(padding: const EdgeInsets.only(top: 8), child: Row(children: [
          const Icon(Icons.touch_app, size: 14, color: Colors.grey),
          const SizedBox(width: 4),
          Expanded(child: Text(
            _puntoInicio == null ? 'También puedes tocar el mapa para marcar el inicio' : 'También puedes tocar el mapa para marcar la llegada',
            style: TextStyle(fontSize: 12, color: Colors.grey.shade600))),
        ])),

      const SizedBox(height: 16),

      SegmentedButton<String>(
        segments: const [
          ButtonSegment(value: 'auto', label: Text('Automática'), icon: Icon(Icons.bolt, size: 16)),
          ButtonSegment(value: 'manual', label: Text('Manual'), icon: Icon(Icons.edit, size: 16)),
        ],
        selected: {_modoCalculo},
        onSelectionChanged: (s) => setState(() => _modoCalculo = s.first),
      ),
      const SizedBox(height: 16),

      if (_modoCalculo == 'auto') ...[
        Row(children: [
          const Text('Filtrar por rubro', style: TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
          const Spacer(),
          if (_rubrosFiltro.isNotEmpty)
            TextButton(
              onPressed: () => setState(() => _rubrosFiltro = {}),
              child: const Text('Limpiar', style: TextStyle(fontSize: 12)),
            ),
        ]),
        const SizedBox(height: 6),
        Wrap(spacing: 6, runSpacing: 6, children: _rubros.map((r) => FilterChip(
          label: Text(r, style: const TextStyle(fontSize: 12)),
          selected: _rubrosFiltro.contains(r),
          onSelected: (v) => setState(() {
            if (v) _rubrosFiltro.add(r); else _rubrosFiltro.remove(r);
          }),
        )).toList()),
        const SizedBox(height: 12),
        Text('Máx. paradas por ruta: $_maxParadas', style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 13)),
        Slider(value: _maxParadas.toDouble(), min: 3, max: 20, divisions: 17,
          label: '$_maxParadas', onChanged: (v) => setState(() => _maxParadas = v.round())),
        const SizedBox(height: 8),
        FilledButton.icon(
          onPressed: (_calculando || _puntoInicio == null || _puntoFin == null) ? null : _calcularRutasAuto,
          icon: _calculando ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.bolt),
          label: Text(_calculando ? 'Calculando...' : 'Calcular 4 Rutas'),
        ),
      ] else ...[
        Row(children: [
          const Icon(Icons.touch_app, size: 16, color: Colors.grey),
          const SizedBox(width: 6),
          const Expanded(child: Text('Toca los prospectos del mapa para agregarlos, en el orden que quieras visitarlos.',
              style: TextStyle(fontSize: 12, color: Colors.grey))),
        ]),
        const SizedBox(height: 12),
        Text('Paradas seleccionadas (${_paradasManuales.length})',
            style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 12, color: Colors.grey)),
        const SizedBox(height: 6),
        ..._paradasManuales.asMap().entries.map((e) => Card(
          margin: const EdgeInsets.only(bottom: 6),
          child: ListTile(
            dense: true,
            leading: CircleAvatar(radius: 14, backgroundColor: _colorRubro(e.value.rubro),
                child: Text('${e.key + 1}', style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold))),
            title: Text(e.value.nombre, style: const TextStyle(fontSize: 13)),
            subtitle: Text(e.value.rubro, style: const TextStyle(fontSize: 11)),
            trailing: Row(mainAxisSize: MainAxisSize.min, children: [
              IconButton(icon: const Icon(Icons.arrow_upward, size: 16), onPressed: e.key == 0 ? null : () => _moverParadaManual(e.key, -1)),
              IconButton(icon: const Icon(Icons.arrow_downward, size: 16), onPressed: e.key == _paradasManuales.length - 1 ? null : () => _moverParadaManual(e.key, 1)),
              IconButton(icon: const Icon(Icons.close, size: 16), onPressed: () => _toggleParadaManual(e.value)),
            ]),
          ),
        )),
        if (_paradasManuales.isEmpty)
          const Padding(padding: EdgeInsets.symmetric(vertical: 20),
              child: Center(child: Text('Ningún prospecto seleccionado todavía.', style: TextStyle(color: Colors.grey)))),
        const SizedBox(height: 8),
        FilledButton.icon(
          onPressed: (_calculando || _puntoInicio == null || _puntoFin == null || _paradasManuales.isEmpty) ? null : _calcularRutaManual,
          icon: _calculando ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)) : const Icon(Icons.edit),
          label: Text(_calculando ? 'Calculando...' : 'Calcular Ruta Manual'),
        ),
      ],
    ]);
  }

  String _coordsLabel(LatLng p) => '${p.latitude.toStringAsFixed(4)}, ${p.longitude.toStringAsFixed(4)}';

  Widget _puntoRow(String label, String valor, VoidCallback onClear) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: Colors.grey.shade100,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(children: [
        Expanded(child: Text('$label · $valor',
          style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: Colors.black87),
          maxLines: 1, overflow: TextOverflow.ellipsis,
        )),
        IconButton(icon: const Icon(Icons.close, size: 16), onPressed: onClear),
      ]),
    );
  }

  Widget _buildResultadoPanel() {
    final ruta = _rutaActiva;
    return ListView(padding: const EdgeInsets.all(16), children: [
      SizedBox(width: double.infinity, child: FilledButton.icon(
        style: FilledButton.styleFrom(backgroundColor: Colors.red),
        onPressed: _nuevaRuta,
        icon: const Icon(Icons.close),
        label: const Text('Dejar de ver esta ruta — ingresar una nueva'),
      )),
      const SizedBox(height: 12),

      SizedBox(
        height: 40,
        child: ListView(scrollDirection: Axis.horizontal, children: _rutas.map((r) => Padding(
          padding: const EdgeInsets.only(right: 8),
          child: ChoiceChip(
            label: Text('${r.icono} ${r.nombre} · ${r.cargando ? "..." : "${r.paradas.length} paradas"}'),
            selected: _rutaSeleccionadaId == r.id,
            onSelected: r.cargando ? null : (_) => setState(() => _rutaSeleccionadaId = r.id),
          ),
        )).toList()),
      ),
      const SizedBox(height: 12),

      if (ruta != null) ...[
        Text(ruta.descripcion, style: TextStyle(fontSize: 12, fontStyle: FontStyle.italic, color: Colors.grey.shade600), textAlign: TextAlign.center),
        const SizedBox(height: 10),
        if (ruta.distanciaKm != null)
          Wrap(spacing: 10, alignment: WrapAlignment.center, children: [
            Text('📏 ${ruta.distanciaKm} km', style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12)),
            Text('⏱️ ~${ruta.duracionMin} min', style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12)),
            Text('📍 ${ruta.paradas.length} paradas', style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 12)),
          ]),
        const SizedBox(height: 12),

        SizedBox(width: double.infinity, child: FilledButton.icon(
          style: FilledButton.styleFrom(backgroundColor: const Color(0xFF6366F1)),
          onPressed: ruta.paradas.isEmpty ? null : _iniciarNavegacion,
          icon: const Icon(Icons.navigation),
          label: const Text('Iniciar ruta'),
        )),
        const SizedBox(height: 8),
        SizedBox(width: double.infinity, child: FilledButton.icon(
          style: FilledButton.styleFrom(backgroundColor: const Color(0xFF10B981)),
          onPressed: _abrirEnGoogleMaps,
          icon: const Icon(Icons.map),
          label: const Text('Abrir en Google Maps'),
        )),
        const SizedBox(height: 8),

        if (_rutaCargadaUuid == null) ...[
          if (!_mostrarFormGuardar)
            SizedBox(width: double.infinity, child: OutlinedButton.icon(
              onPressed: () => setState(() => _mostrarFormGuardar = true),
              icon: const Icon(Icons.save),
              label: const Text('Guardar ruta'),
            ))
          else
            Column(children: [
              TextField(controller: _nombreCtrl, decoration: const InputDecoration(
                labelText: 'Nombre de la ruta', isDense: true, border: OutlineInputBorder())),
              const SizedBox(height: 8),
              Row(children: [
                Expanded(child: FilledButton(
                  onPressed: _guardando ? null : _guardarRuta,
                  child: Text(_guardando ? 'Guardando...' : 'Guardar'),
                )),
                const SizedBox(width: 8),
                Expanded(child: OutlinedButton(
                  onPressed: () => setState(() => _mostrarFormGuardar = false),
                  child: const Text('Cancelar'),
                )),
              ]),
            ]),
        ],
        if (_rutaGuardadaMsg != null)
          Padding(padding: const EdgeInsets.only(top: 8), child: Text(_rutaGuardadaMsg!,
              style: TextStyle(color: _rutaGuardadaMsgError ? Colors.red : Colors.green, fontSize: 12), textAlign: TextAlign.center)),

        const SizedBox(height: 16),
        const Text('ORDEN DE VISITAS', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: Colors.grey, letterSpacing: 0.5)),
        const SizedBox(height: 8),
        ...ruta.paradas.map((p) => Card(
          margin: const EdgeInsets.only(bottom: 6),
          child: ListTile(
            dense: true,
            leading: CircleAvatar(radius: 14, backgroundColor: _colorRubro(p.prospecto.rubro),
                child: Text('${p.orden}', style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold))),
            title: Text(p.prospecto.nombre, style: const TextStyle(fontSize: 13)),
            subtitle: Text([p.prospecto.rubro, if (p.prospecto.comuna != null) p.prospecto.comuna!].join(' · '), style: const TextStyle(fontSize: 11)),
            trailing: p.prospecto.telefono != null
                ? IconButton(icon: const Icon(Icons.phone, size: 18, color: Colors.green),
                    onPressed: () => launchUrl(Uri.parse('tel:${p.prospecto.telefono}')))
                : null,
          ),
        )),
        if (ruta.paradas.isEmpty)
          const Padding(padding: EdgeInsets.symmetric(vertical: 20),
              child: Center(child: Text('Sin prospectos en este corredor', style: TextStyle(color: Colors.grey)))),
      ],
    ]);
  }

  Widget _buildGuardadasTab() {
    if (_cargandoGuardadas) return const Center(child: CircularProgressIndicator());
    if (_rutasGuardadas.isEmpty) {
      return const Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
        Icon(Icons.folder_open, size: 56, color: Colors.grey),
        SizedBox(height: 12),
        Text('Todavía no guardas ninguna ruta', style: TextStyle(color: Colors.grey)),
      ]));
    }
    final fmt = DateFormat('dd/MM/yyyy HH:mm');
    return RefreshIndicator(
      onRefresh: _cargarGuardadas,
      child: ListView.separated(
        padding: const EdgeInsets.all(12),
        itemCount: _rutasGuardadas.length,
        separatorBuilder: (_, __) => const SizedBox(height: 2),
        itemBuilder: (_, i) {
          final r = _rutasGuardadas[i];
          DateTime? dt;
          try { dt = DateTime.parse(r.createdAt); } catch (_) {}
          return Card(
            child: ListTile(
              leading: Container(
                width: 40, height: 40,
                decoration: BoxDecoration(color: const Color(0xFF6366F1).withOpacity(0.12), borderRadius: BorderRadius.circular(8)),
                child: Center(child: Text(r.tipo == 'manual' ? '✍️' : '⚡', style: const TextStyle(fontSize: 18))),
              ),
              title: Text(r.nombre, style: const TextStyle(fontWeight: FontWeight.w600)),
              subtitle: Text([
                if (dt != null) fmt.format(dt),
                if (r.distanciaKm != null) '${r.distanciaKm} km',
                '${r.paradas.length} paradas',
              ].join(' · '), style: const TextStyle(fontSize: 11)),
              trailing: IconButton(icon: const Icon(Icons.delete_outline, color: Colors.grey), onPressed: () => _eliminarRutaGuardada(r)),
              onTap: () => _cargarRutaGuardada(r),
            ),
          );
        },
      ),
    );
  }
}
