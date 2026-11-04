import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/app_config.dart';
import '../../../data/services/laudus_service.dart';
import '../../providers/providers.dart';
import '../../providers/order_providers.dart';
import '../products/local_products_screen.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key});
  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  late bool _isLocal;
  late TextEditingController _gmapsCtrl;
  bool _gmapsSaved    = false;
  bool _gmapsObscured = true;

  bool _syncingCatalog = false;
  int _cachedCount = 0;
  String? _lastSync;
  String? _catalogMsg;
  bool _catalogError = false;

  @override
  void initState() {
    super.initState();
    _isLocal   = AppConfig.instance.isLocal;
    _gmapsCtrl = TextEditingController(text: AppConfig.instance.gmapsKey);
    _gmapsSaved = AppConfig.instance.gmapsKey.isNotEmpty;
    _loadCatalogInfo();
  }

  Future<void> _loadCatalogInfo() async {
    final count = await LaudusService.getCachedProductCount();
    final lastSync = await LaudusService.getLastSyncTime();
    if (mounted) setState(() { _cachedCount = count; _lastSync = lastSync; });
  }

  String get _lastSyncLabel {
    if (_lastSync == null) return 'Nunca sincronizado';
    try {
      final dt = DateTime.parse(_lastSync!);
      final diff = DateTime.now().difference(dt);
      if (diff.inMinutes < 60) return 'Hace ${diff.inMinutes} min';
      if (diff.inHours < 24) return 'Hace ${diff.inHours} horas';
      return 'Hace ${diff.inDays} días';
    } catch (_) {
      return _lastSync!;
    }
  }

  Future<void> _actualizarCatalogo() async {
    setState(() { _syncingCatalog = true; _catalogMsg = null; _catalogError = false; });
    try {
      final result = await LaudusService.syncAll();
      await ref.read(laudusProductsProvider.notifier).reload();
      await _loadCatalogInfo();
      if (!mounted) return;
      setState(() {
        _catalogMsg = '✅ ${result['productos']} productos · stock actualizado en ${result['stockActualizado']}';
        _catalogError = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() { _catalogMsg = '❌ Error al actualizar: $e'; _catalogError = true; });
    } finally {
      if (mounted) setState(() => _syncingCatalog = false);
    }
  }

  @override
  void dispose() { _gmapsCtrl.dispose(); super.dispose(); }

  Future<void> _toggleLocal(bool val) async {
    await AppConfig.instance.setLocal(val);
    setState(() => _isLocal = val);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(val ? 'Modo LOCAL (localhost:8080)' : 'Modo PRODUCCIÓN (apkjop.jopsoluciones.cl)'),
        duration: const Duration(seconds: 2),
      ));
    }
  }

  Future<void> _saveGmapsKey() async {
    await AppConfig.instance.setGmapsKey(_gmapsCtrl.text);
    setState(() => _gmapsSaved = _gmapsCtrl.text.trim().isNotEmpty);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('API Key de Google Maps guardada'),
        duration: Duration(seconds: 2),
      ));
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark  = ref.watch(themeModeProvider);
    final clients = ref.watch(clientsProvider);
    final cats    = ref.watch(categoriesProvider);
    final scheme  = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(title: const Text('Configuración')),
      body: ListView(children: [

        // ── Apariencia ──────────────────────────────────────────
        ListTile(
          leading: const Icon(Icons.palette),
          title: const Text('Modo oscuro'),
          trailing: Switch(value: isDark, onChanged: (v) => ref.read(themeModeProvider.notifier).state = v),
        ),

        const Divider(),

        // ── Servidor ────────────────────────────────────────────
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: Text('SERVIDOR', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: scheme.primary, letterSpacing: 1.2)),
        ),

        SwitchListTile(
          secondary: Icon(_isLocal ? Icons.computer : Icons.cloud_outlined,
              color: _isLocal ? Colors.orange : Colors.green),
          title: Text(_isLocal ? 'LOCAL (localhost:8080)' : 'PRODUCCIÓN',
              style: TextStyle(fontWeight: FontWeight.bold,
                  color: _isLocal ? Colors.orange : Colors.green)),
          subtitle: Text(_isLocal
              ? 'Conectado al Spring Boot local vía USB'
              : 'apkjop.jopsoluciones.cl'),
          value: _isLocal,
          onChanged: _toggleLocal,
        ),

        const Divider(),

        // ── Catálogo local ──────────────────────────────────────
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: Text('CATÁLOGO LOCAL', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: scheme.primary, letterSpacing: 1.2)),
        ),

        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
          child: Text(
            'Productos y stock se guardan en el dispositivo. La app trabaja con este '
            'respaldo local, no consulta el ERP en cada pantalla — actualízalo cuando lo necesites.',
            style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
          ),
        ),

        ListTile(
          leading: Icon(Icons.inventory_2_outlined, color: _cachedCount > 0 ? Colors.blue : Colors.orange),
          title: Text('$_cachedCount productos en caché'),
          subtitle: Text('Última actualización: $_lastSyncLabel'),
          trailing: TextButton(
            onPressed: _cachedCount > 0
                ? () => Navigator.push(context, MaterialPageRoute(builder: (_) => const LocalProductsScreen()))
                : null,
            child: const Text('Ver productos'),
          ),
        ),

        if (_catalogMsg != null)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: Container(
              padding: const EdgeInsets.all(10),

              decoration: BoxDecoration(
                color: _catalogError ? Colors.red.shade50 : Colors.green.shade50,
                border: Border.all(color: _catalogError ? Colors.red.shade300 : Colors.green.shade300),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(_catalogMsg!,
                  style: TextStyle(fontSize: 12, color: _catalogError ? Colors.red.shade800 : Colors.green.shade800)),
            ),
          ),

        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
          child: SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: _syncingCatalog ? null : _actualizarCatalogo,
              icon: _syncingCatalog
                  ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : const Icon(Icons.refresh),
              label: Text(_syncingCatalog ? 'Actualizando...' : 'Actualizar catálogo'),
            ),
          ),
        ),

        const Divider(),

        // ── Google Maps ─────────────────────────────────────────
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
          child: Text('GOOGLE MAPS', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: scheme.primary, letterSpacing: 1.2)),
        ),

        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
          child: Row(children: [
            const Icon(Icons.map_outlined, size: 18, color: Colors.grey),
            const SizedBox(width: 6),
            const Expanded(
              child: Text('API Key', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
            ),
            if (_gmapsSaved)
              const Chip(
                label: Text('Configurada', style: TextStyle(fontSize: 11)),
                backgroundColor: Color(0x2222C55E),
                labelStyle: TextStyle(color: Colors.green),
                padding: EdgeInsets.zero,
              ),
          ]),
        ),

        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
          child: Row(children: [
            Expanded(
              child: TextField(
                controller: _gmapsCtrl,
                obscureText: _gmapsObscured,
                style: const TextStyle(fontSize: 13, fontFamily: 'monospace'),
                decoration: InputDecoration(
                  hintText: 'AIzaSy...',
                  hintStyle: const TextStyle(fontSize: 12, color: Colors.grey),
                  isDense: true,
                  filled: true,
                  border: OutlineInputBorder(borderRadius: BorderRadius.circular(10), borderSide: BorderSide.none),
                  suffixIcon: IconButton(
                    icon: Icon(_gmapsObscured ? Icons.visibility_off : Icons.visibility, size: 18),
                    onPressed: () => setState(() => _gmapsObscured = !_gmapsObscured),
                  ),
                ),
                onChanged: (_) => setState(() => _gmapsSaved = false),
              ),
            ),
            const SizedBox(width: 8),
            FilledButton(
              onPressed: _saveGmapsKey,
              style: FilledButton.styleFrom(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
              ),
              child: const Text('Guardar'),
            ),
          ]),
        ),

        Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
          child: Text(
            'Necesitas Maps JavaScript API + Places API habilitadas en Google Cloud.',
            style: TextStyle(fontSize: 11, color: Colors.grey.shade500),
          ),
        ),

        const Divider(),

        // ── Estadísticas ─────────────────────────────────────────
        ListTile(
          leading: const Icon(Icons.people),
          title: const Text('Total clientes'),
          trailing: Text(clients.valueOrNull?.length.toString() ?? '—', style: const TextStyle(fontWeight: FontWeight.bold)),
        ),
        ListTile(
          leading: const Icon(Icons.category),
          title: const Text('Total categorías'),
          trailing: Text(cats.valueOrNull?.length.toString() ?? '—', style: const TextStyle(fontWeight: FontWeight.bold)),
        ),

        const Divider(),
        const ListTile(leading: Icon(Icons.info), title: Text('JOP Ubicaciones'), subtitle: Text('v1.0.0 — JOP Soluciones')),
      ]),
    );
  }
}
