import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import '../../../core/constants/app_constants.dart';
import '../../../domain/entities/client.dart';
import '../../providers/providers.dart';

class ClientFormScreen extends ConsumerStatefulWidget {
  final Client? existing;
  final double? initialLat;
  final double? initialLng;

  const ClientFormScreen({super.key, this.existing, this.initialLat, this.initialLng});

  @override
  ConsumerState<ClientFormScreen> createState() => _ClientFormScreenState();
}

class _ClientFormScreenState extends ConsumerState<ClientFormScreen> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _name, _phone, _email, _address, _notes;
  String _status = 'active';
  String? _categoryId, _categoryName;
  final Map<String, TextEditingController> _extraControllers = {};
  final List<String> _extraKeys = [];

  double _lat = 0;
  double _lng = 0;
  bool _coordsSet = false;

  // Geocoding state
  List<Map<String, dynamic>> _suggestions = [];
  bool _loadingGeo = false;
  Timer? _debounce;
  bool _showSuggestions = false;

  @override
  void initState() {
    super.initState();
    final c = widget.existing;
    _name    = TextEditingController(text: c?.name ?? '');
    _phone   = TextEditingController(text: c?.phone ?? '');
    _email   = TextEditingController(text: c?.email ?? '');
    _address = TextEditingController(text: c?.address ?? '');
    _notes   = TextEditingController(text: c?.notes ?? '');

    if (c != null) {
      _lat = c.latitude;
      _lng = c.longitude;
      _coordsSet = (c.latitude != 0 || c.longitude != 0);
    } else if (widget.initialLat != null) {
      _lat = widget.initialLat!;
      _lng = widget.initialLng ?? 0;
      _coordsSet = true;
    }

    _status      = c?.status ?? 'active';
    _categoryId  = c?.categoryId;
    _categoryName = c?.categoryName;
    c?.extraFields.forEach((k, v) {
      _extraKeys.add(k);
      _extraControllers[k] = TextEditingController(text: v);
    });
  }

  @override
  void dispose() {
    _debounce?.cancel();
    for (final c in [_name, _phone, _email, _address, _notes]) c.dispose();
    for (final c in _extraControllers.values) c.dispose();
    super.dispose();
  }

  // ── Geocoding ──────────────────────────────────────────────────

  void _onAddressChanged(String value) {
    _debounce?.cancel();
    if (value.trim().length < 4) {
      setState(() { _suggestions = []; _showSuggestions = false; });
      return;
    }
    _debounce = Timer(const Duration(milliseconds: 600), () => _searchNominatim(value));
  }

  Future<void> _searchNominatim(String query) async {
    setState(() => _loadingGeo = true);
    try {
      final uri = Uri.parse(
        'https://nominatim.openstreetmap.org/search'
        '?q=${Uri.encodeComponent(query)}'
        '&countrycodes=cl&format=json&limit=5&addressdetails=1',
      );
      final resp = await http.get(uri, headers: {'User-Agent': 'JOP-App/1.0'})
          .timeout(const Duration(seconds: 8));
      if (resp.statusCode == 200) {
        final List<dynamic> data = jsonDecode(resp.body);
        setState(() {
          _suggestions = data.map((e) => Map<String, dynamic>.from(e)).toList();
          _showSuggestions = _suggestions.isNotEmpty;
          _loadingGeo = false;
        });
      }
    } catch (_) {
      setState(() => _loadingGeo = false);
    }
  }

  void _selectSuggestion(Map<String, dynamic> item) {
    final display = item['display_name'] as String? ?? '';
    final lat     = double.tryParse(item['lat'] as String? ?? '') ?? 0;
    final lng     = double.tryParse(item['lon'] as String? ?? '') ?? 0;
    setState(() {
      _address.text  = display;
      _lat           = lat;
      _lng           = lng;
      _coordsSet     = true;
      _suggestions   = [];
      _showSuggestions = false;
    });
  }

  void _clearCoords() => setState(() { _coordsSet = false; _lat = 0; _lng = 0; });

  // ── Campos extras ──────────────────────────────────────────────

  void _addExtraField() {
    final key = 'Campo ${_extraKeys.length + 1}';
    setState(() {
      _extraKeys.add(key);
      _extraControllers[key] = TextEditingController();
    });
  }

  void _removeExtraField(String key) {
    setState(() {
      _extraControllers[key]?.dispose();
      _extraControllers.remove(key);
      _extraKeys.remove(key);
    });
  }

  // ── Guardar ────────────────────────────────────────────────────

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    final extras = <String, String>{};
    for (final k in _extraKeys) {
      final v = _extraControllers[k]?.text.trim() ?? '';
      if (v.isNotEmpty) extras[k] = v;
    }
    final notifier = ref.read(clientsProvider.notifier);
    final client = widget.existing != null
        ? widget.existing!.copyWith(
            name: _name.text.trim(),
            phone: _phone.text.trim().isEmpty ? null : _phone.text.trim(),
            email: _email.text.trim().isEmpty ? null : _email.text.trim(),
            address: _address.text.trim().isEmpty ? null : _address.text.trim(),
            latitude: _lat,
            longitude: _lng,
            categoryId: _categoryId,
            categoryName: _categoryName,
            status: _status,
            notes: _notes.text.trim().isEmpty ? null : _notes.text.trim(),
            extraFields: extras,
          )
        : notifier.createNew(
            name: _name.text.trim(),
            lat: _lat,
            lng: _lng,
            phone: _phone.text.trim().isEmpty ? null : _phone.text.trim(),
            email: _email.text.trim().isEmpty ? null : _email.text.trim(),
            address: _address.text.trim().isEmpty ? null : _address.text.trim(),
            categoryId: _categoryId,
            categoryName: _categoryName,
            status: _status,
            notes: _notes.text.trim().isEmpty ? null : _notes.text.trim(),
            extraFields: extras,
          );
    await notifier.save(client);
    if (mounted) Navigator.of(context).pop(client);
  }

  // ── Build ──────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final cats    = ref.watch(categoriesProvider);
    final isEdit  = widget.existing != null;
    final colors  = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: Text(isEdit ? 'Editar Cliente' : 'Nuevo Cliente'),
        actions: [
          TextButton.icon(
            onPressed: _save,
            icon: const Icon(Icons.save),
            label: const Text('Guardar'),
          ),
        ],
      ),
      body: Form(
        key: _formKey,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _section('Información básica', Icons.person),
            const SizedBox(height: 8),
            TextFormField(
              controller: _name,
              decoration: const InputDecoration(labelText: 'Nombre *', prefixIcon: Icon(Icons.person_outline)),
              validator: (v) => (v == null || v.trim().isEmpty) ? 'El nombre es requerido' : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _phone,
              decoration: const InputDecoration(labelText: 'Teléfono', prefixIcon: Icon(Icons.phone)),
              keyboardType: TextInputType.phone,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _email,
              decoration: const InputDecoration(labelText: 'Correo', prefixIcon: Icon(Icons.email)),
              keyboardType: TextInputType.emailAddress,
              validator: (v) {
                if (v == null || v.trim().isEmpty) return null;
                if (!RegExp(r'^[\w.-]+@[\w.-]+\.\w+$').hasMatch(v.trim())) return 'Correo inválido';
                return null;
              },
            ),
            const SizedBox(height: 20),

            // ── Dirección con Geocoding ──────────────────────────
            _section('Dirección y ubicación', Icons.location_on),
            const SizedBox(height: 8),
            TextFormField(
              controller: _address,
              decoration: InputDecoration(
                labelText: 'Dirección',
                prefixIcon: const Icon(Icons.home),
                suffixIcon: _loadingGeo
                    ? const Padding(
                        padding: EdgeInsets.all(12),
                        child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
                      )
                    : null,
                helperText: 'Escribe para buscar dirección en Chile',
              ),
              onChanged: _onAddressChanged,
              onTap: () {
                if (_suggestions.isNotEmpty) setState(() => _showSuggestions = true);
              },
            ),

            // Sugerencias Nominatim
            if (_showSuggestions && _suggestions.isNotEmpty)
              Card(
                margin: const EdgeInsets.only(top: 2),
                elevation: 4,
                child: Column(
                  children: _suggestions.map((item) {
                    final display = item['display_name'] as String? ?? '';
                    return ListTile(
                      leading: const Icon(Icons.place, size: 18),
                      title: Text(display, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 13)),
                      onTap: () => _selectSuggestion(item),
                    );
                  }).toList(),
                ),
              ),

            const SizedBox(height: 12),

            // Coordenadas confirmadas
            if (_coordsSet)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                decoration: BoxDecoration(
                  color: colors.primaryContainer.withOpacity(0.3),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: colors.primary.withOpacity(0.3)),
                ),
                child: Row(children: [
                  Icon(Icons.gps_fixed, size: 18, color: colors.primary),
                  const SizedBox(width: 8),
                  Expanded(child: Text(
                    '${_lat.toStringAsFixed(6)}, ${_lng.toStringAsFixed(6)}',
                    style: TextStyle(fontSize: 13, color: colors.onSurface, fontFamily: 'monospace'),
                  )),
                  TextButton(onPressed: _clearCoords, child: const Text('Cambiar')),
                ]),
              )
            else
              OutlinedButton.icon(
                onPressed: _useGps,
                icon: const Icon(Icons.my_location),
                label: const Text('Usar mi ubicación GPS'),
              ),

            const SizedBox(height: 20),

            // ── Clasificación ────────────────────────────────────
            _section('Clasificación', Icons.label),
            const SizedBox(height: 8),
            DropdownButtonFormField<String>(
              value: _status,
              decoration: const InputDecoration(labelText: 'Estado', prefixIcon: Icon(Icons.circle_outlined)),
              items: AppConstants.clientStatuses
                  .map((s) => DropdownMenuItem(value: s, child: Text(AppConstants.statusLabels[s] ?? s)))
                  .toList(),
              onChanged: (v) => setState(() => _status = v ?? 'active'),
            ),
            const SizedBox(height: 12),
            cats.when(
              data: (categories) => DropdownButtonFormField<String>(
                value: _categoryId,
                decoration: const InputDecoration(labelText: 'Categoría', prefixIcon: Icon(Icons.category)),
                items: [
                  const DropdownMenuItem(value: null, child: Text('Sin categoría')),
                  ...categories.map((cat) => DropdownMenuItem(
                    value: cat.uuid,
                    child: Row(children: [
                      CircleAvatar(backgroundColor: cat.color, radius: 8),
                      const SizedBox(width: 8),
                      Text(cat.name),
                    ]),
                  )),
                ],
                onChanged: (v) => setState(() {
                  _categoryId   = v;
                  _categoryName = v == null ? null : categories.firstWhere((c) => c.uuid == v).name;
                }),
              ),
              loading: () => const LinearProgressIndicator(),
              error: (_, __) => const SizedBox(),
            ),

            const SizedBox(height: 20),
            _section('Notas', Icons.notes),
            const SizedBox(height: 8),
            TextFormField(
              controller: _notes,
              decoration: const InputDecoration(labelText: 'Notas o comentarios', prefixIcon: Icon(Icons.notes), alignLabelWithHint: true),
              maxLines: 3,
            ),
            const SizedBox(height: 20),
            Row(children: [
              _section('Campos extras', Icons.add_box),
              const Spacer(),
              TextButton.icon(onPressed: _addExtraField, icon: const Icon(Icons.add), label: const Text('Agregar')),
            ]),
            const SizedBox(height: 8),
            ..._extraKeys.map((key) => Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: Row(children: [
                Expanded(child: TextFormField(
                  controller: _extraControllers[key],
                  decoration: InputDecoration(labelText: key, prefixIcon: const Icon(Icons.text_fields)),
                )),
                IconButton(icon: const Icon(Icons.remove_circle_outline, color: Colors.red), onPressed: () => _removeExtraField(key)),
              ]),
            )),
            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }

  Future<void> _useGps() async {
    final loc = ref.read(locationProvider);
    await loc.when(
      data: (pos) {
        if (pos != null) setState(() { _lat = pos.latitude; _lng = pos.longitude; _coordsSet = true; });
      },
      loading: () async {},
      error: (_, __) {},
    );
  }

  Widget _section(String title, IconData icon) => Row(children: [
    Icon(icon, size: 18, color: Theme.of(context).colorScheme.primary),
    const SizedBox(width: 6),
    Text(title, style: Theme.of(context).textTheme.titleSmall?.copyWith(
      fontWeight: FontWeight.bold,
      color: Theme.of(context).colorScheme.primary,
    )),
  ]);
}
