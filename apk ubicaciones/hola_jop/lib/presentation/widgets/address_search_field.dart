import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:latlong2/latlong.dart';
import '../../core/app_config.dart';

// Campo de búsqueda de direcciones vía Google Places Autocomplete + Details.
// Requiere que el usuario tenga configurada su API Key de Google Maps en
// Config. Si no la tiene, no se muestra — el llamador debe ofrecer una
// alternativa (ej. tocar el mapa).

class _PlaceSuggestion {
  final String placeId;
  final String description;
  _PlaceSuggestion({required this.placeId, required this.description});
}

class AddressSearchField extends StatefulWidget {
  final String label;
  final Color accentColor;
  final IconData icon;
  final void Function(String address, LatLng latlng) onSelected;

  const AddressSearchField({
    super.key,
    required this.label,
    required this.accentColor,
    required this.icon,
    required this.onSelected,
  });

  @override
  State<AddressSearchField> createState() => _AddressSearchFieldState();
}

class _AddressSearchFieldState extends State<AddressSearchField> {
  final _ctrl = TextEditingController();
  Timer? _debounce;
  List<_PlaceSuggestion> _sugerencias = [];
  bool _buscando = false;

  @override
  void dispose() {
    _ctrl.dispose();
    _debounce?.cancel();
    super.dispose();
  }

  void _onChanged(String v) {
    _debounce?.cancel();
    if (v.trim().length < 3) {
      setState(() => _sugerencias = []);
      return;
    }
    _debounce = Timer(const Duration(milliseconds: 400), () => _buscar(v));
  }

  Future<void> _buscar(String query) async {
    final key = AppConfig.instance.gmapsKey;
    if (key.isEmpty) return;
    setState(() => _buscando = true);
    try {
      final url = Uri.parse(
        'https://maps.googleapis.com/maps/api/place/autocomplete/json'
        '?input=${Uri.encodeComponent(query)}&components=country:cl&language=es&key=$key',
      );
      final resp = await http.get(url).timeout(const Duration(seconds: 8));
      final data = jsonDecode(resp.body) as Map<String, dynamic>;
      final preds = (data['predictions'] as List? ?? []);
      if (!mounted) return;
      setState(() {
        _sugerencias = preds.map((p) => _PlaceSuggestion(
          placeId: p['place_id'] as String,
          description: p['description'] as String,
        )).toList();
        _buscando = false;
      });
    } catch (_) {
      if (mounted) setState(() => _buscando = false);
    }
  }

  Future<void> _seleccionar(_PlaceSuggestion s) async {
    final key = AppConfig.instance.gmapsKey;
    setState(() {
      _ctrl.text = s.description;
      _sugerencias = [];
    });
    try {
      final url = Uri.parse(
        'https://maps.googleapis.com/maps/api/place/details/json'
        '?place_id=${s.placeId}&fields=geometry&key=$key',
      );
      final resp = await http.get(url).timeout(const Duration(seconds: 8));
      final data = jsonDecode(resp.body) as Map<String, dynamic>;
      final loc = data['result']?['geometry']?['location'];
      if (loc == null) return;
      widget.onSelected(s.description, LatLng((loc['lat'] as num).toDouble(), (loc['lng'] as num).toDouble()));
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      TextField(
        controller: _ctrl,
        decoration: InputDecoration(
          labelText: widget.label,
          prefixIcon: Icon(widget.icon, color: widget.accentColor, size: 20),
          suffixIcon: _buscando
              ? const Padding(padding: EdgeInsets.all(12), child: SizedBox(
                  width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)))
              : (_ctrl.text.isNotEmpty
                  ? IconButton(icon: const Icon(Icons.close, size: 18),
                      onPressed: () => setState(() { _ctrl.clear(); _sugerencias = []; }))
                  : null),
          isDense: true,
          border: const OutlineInputBorder(),
        ),
        onChanged: _onChanged,
      ),
      if (_sugerencias.isNotEmpty)
        Container(
          margin: const EdgeInsets.only(top: 4),
          decoration: BoxDecoration(border: Border.all(color: Colors.grey.shade300), borderRadius: BorderRadius.circular(8)),
          constraints: const BoxConstraints(maxHeight: 220),
          child: ListView.separated(
            shrinkWrap: true,
            padding: EdgeInsets.zero,
            itemCount: _sugerencias.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (_, i) {
              final s = _sugerencias[i];
              return ListTile(
                dense: true,
                leading: const Icon(Icons.location_on_outlined, size: 18),
                title: Text(s.description, style: const TextStyle(fontSize: 13)),
                onTap: () => _seleccionar(s),
              );
            },
          ),
        ),
    ]);
  }
}
