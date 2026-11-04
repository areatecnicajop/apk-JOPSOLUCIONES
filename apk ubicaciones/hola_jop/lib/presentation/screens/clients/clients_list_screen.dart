import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/constants/app_constants.dart';
import '../../../domain/entities/client.dart';
import '../../../domain/entities/prospecto.dart';
import '../../../data/services/prospecto_service.dart';
import '../../providers/providers.dart';
import '../../widgets/lead_bottom_sheet.dart';
import 'client_detail_screen.dart';
import 'client_form_screen.dart';

class ClientsListScreen extends ConsumerStatefulWidget {
  const ClientsListScreen({super.key});
  @override
  ConsumerState<ClientsListScreen> createState() => _ClientsListScreenState();
}

class _ClientsListScreenState extends ConsumerState<ClientsListScreen> {
  final _search = TextEditingController();
  List<Prospecto> _prospectos = [];
  bool _loadingProspectos = false;

  @override
  void initState() {
    super.initState();
    _cargarProspectos();
  }

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  Future<void> _cargarProspectos() async {
    setState(() => _loadingProspectos = true);
    try {
      final lista = await ProspectoService.getProspectos();
      if (mounted) setState(() { _prospectos = lista; _loadingProspectos = false; });
    } catch (e) {
      if (mounted) setState(() => _loadingProspectos = false);
    }
  }

  Future<void> _onRefresh() async {
    await Future.wait([
      ref.read(clientsProvider.notifier).load(),
      _cargarProspectos(),
    ]);
  }

  List<Prospecto> _prospectosFiltrados(String query) {
    if (query.isEmpty) return _prospectos;
    final q = query.toLowerCase();
    return _prospectos.where((p) =>
        p.nombre.toLowerCase().contains(q) ||
        (p.telefono?.toLowerCase().contains(q) ?? false) ||
        (p.comuna?.toLowerCase().contains(q) ?? false) ||
        p.rubro.toLowerCase().contains(q)).toList();
  }

  @override
  Widget build(BuildContext context) {
    final clients   = ref.watch(filteredClientsProvider);
    final filter    = ref.watch(clientFilterProvider);
    final categories = ref.watch(categoriesProvider);
    final query     = _search.text;

    // Ocultar prospectos si hay filtro por categoría/status activo (no aplica a leads)
    final mostrarProspectos = !filter.isActive || filter.searchQuery.isNotEmpty;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Clientes'),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(56),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
            child: TextField(
              controller: _search,
              decoration: InputDecoration(
                hintText: 'Buscar por nombre, teléfono, correo...',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _search.text.isNotEmpty
                    ? IconButton(icon: const Icon(Icons.clear), onPressed: () {
                        _search.clear();
                        ref.read(clientFilterProvider.notifier)
                            .update(filter.copyWith(searchQuery: ''));
                        setState(() {});
                      })
                    : null,
                isDense: true, filled: true,
                fillColor: Theme.of(context).colorScheme.surface,
              ),
              onChanged: (v) {
                ref.read(clientFilterProvider.notifier)
                    .update(filter.copyWith(searchQuery: v));
                setState(() {});
              },
            ),
          ),
        ),
      ),
      body: Column(children: [
        // Filtros rápidos
        SizedBox(
          height: 44,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            children: [
              if (filter.isActive) Padding(
                padding: const EdgeInsets.only(right: 8),
                child: FilterChip(
                  label: const Text('Limpiar filtros'),
                  selected: true,
                  onSelected: (_) {
                    _search.clear();
                    setState(() {});
                    ref.read(clientFilterProvider.notifier).reset();
                  },
                  deleteIcon: const Icon(Icons.close, size: 16),
                  onDeleted: () {
                    _search.clear();
                    setState(() {});
                    ref.read(clientFilterProvider.notifier).reset();
                  },
                ),
              ),
              ...AppConstants.clientStatuses.map((s) => Padding(
                padding: const EdgeInsets.only(right: 8),
                child: FilterChip(
                  label: Text(AppConstants.statusLabels[s] ?? s),
                  selected: filter.status == s,
                  onSelected: (v) => ref.read(clientFilterProvider.notifier).update(
                    v ? filter.copyWith(status: s) : filter.copyWith(clearStatus: true),
                  ),
                ),
              )),
              ...categories.when(
                data: (cats) => cats.map((cat) => Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: FilterChip(
                    avatar: CircleAvatar(backgroundColor: cat.color, radius: 8),
                    label: Text(cat.name),
                    selected: filter.categoryId == cat.uuid,
                    onSelected: (v) => ref.read(clientFilterProvider.notifier).update(
                      v ? filter.copyWith(categoryId: cat.uuid)
                        : filter.copyWith(clearCategory: true),
                    ),
                  ),
                )).toList(),
                loading: () => [], error: (_, __) => [],
              ),
            ],
          ),
        ),

        Expanded(child: RefreshIndicator(
          onRefresh: _onRefresh,
          child: clients.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, _) => ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                SizedBox(
                  height: 300,
                  child: Center(child: Text('Error: $e')),
                ),
              ],
            ),
            data: (list) {
              final prospectos = mostrarProspectos ? _prospectosFiltrados(query) : <Prospecto>[];

              if (list.isEmpty && prospectos.isEmpty) {
                return ListView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  children: const [
                    SizedBox(
                      height: 300,
                      child: Center(child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.people_outline, size: 64, color: Colors.grey),
                          SizedBox(height: 12),
                          Text('No hay resultados', style: TextStyle(color: Colors.grey)),
                        ],
                      )),
                    ),
                  ],
                );
              }

              // Sección leads encima, luego clientes
              final sections = <Widget>[];

              if (prospectos.isNotEmpty) {
                sections.add(_SectionHeader(
                  label: 'LEADS (${prospectos.length})',
                  subtitle: _loadingProspectos ? 'Cargando...' : 'Clientes potenciales del scraper',
                ));
                for (final p in prospectos) {
                  sections.add(_ProspectoTile(
                    prospecto: p,
                    onConvertido: () => setState(() => _prospectos.removeWhere((x) => x.id == p.id)),
                  ));
                }
              }

              if (list.isNotEmpty) {
                sections.add(_SectionHeader(
                  label: 'CLIENTES (${list.length})',
                ));
                for (final c in list) {
                  sections.add(_ClientTile(client: c));
                }
              }

              return ListView.separated(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(8),
                itemCount: sections.length,
                separatorBuilder: (_, __) => const SizedBox(height: 2),
                itemBuilder: (_, i) => sections[i],
              );
            },
          ),
        )),
      ]),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => Navigator.push(context,
            MaterialPageRoute(builder: (_) => const ClientFormScreen())),
        icon: const Icon(Icons.add),
        label: const Text('Nuevo cliente'),
      ),
    );
  }

}

// ── Sección header ────────────────────────────────────────────────────────────

class _SectionHeader extends StatelessWidget {
  final String label;
  final String? subtitle;
  const _SectionHeader({required this.label, this.subtitle});

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(4, 12, 4, 4),
    child: Row(children: [
      Text(label, style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold,
          color: Theme.of(context).colorScheme.primary, letterSpacing: 0.8)),
      if (subtitle != null) ...[
        const SizedBox(width: 6),
        Expanded(child: Text(subtitle!,
            style: const TextStyle(fontSize: 11, color: Colors.grey),
            overflow: TextOverflow.ellipsis)),
      ],
    ]),
  );
}

// ── Tile de prospecto/lead ────────────────────────────────────────────────────

class _ProspectoTile extends StatelessWidget {
  final Prospecto prospecto;
  final VoidCallback onConvertido;
  const _ProspectoTile({required this.prospecto, required this.onConvertido});

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
  Widget build(BuildContext context) {
    final p = prospecto;
    final color = _colorRubro(p.rubro);
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () => mostrarLeadBottomSheet(context, prospecto: p, color: color, onConvertido: onConvertido),
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
                  maxLines: 1, overflow: TextOverflow.ellipsis),
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
                  Expanded(child: Text(p.comuna!,
                      style: TextStyle(fontSize: 11, color: Colors.grey.shade600),
                      overflow: TextOverflow.ellipsis)),
                ],
              ]),
              if (p.telefono != null)
                Text(p.telefono!,
                    style: TextStyle(fontSize: 11, color: Colors.grey.shade700)),
            ])),
            // Badge Lead
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
              decoration: BoxDecoration(
                color: Colors.blue.withOpacity(0.1),
                border: Border.all(color: Colors.blue.withOpacity(0.3)),
                borderRadius: BorderRadius.circular(6),
              ),
              child: const Text('Lead', style: TextStyle(fontSize: 10,
                  color: Colors.blue, fontWeight: FontWeight.bold)),
            ),
          ]),
        ),
      ),
    );
  }
}

// ── Tile de cliente real ──────────────────────────────────────────────────────

class _ClientTile extends StatelessWidget {
  final Client client;
  const _ClientTile({required this.client});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: _statusColor(client.status).withOpacity(0.15),
          child: Text(client.name.isNotEmpty ? client.name[0].toUpperCase() : '?',
            style: TextStyle(color: _statusColor(client.status), fontWeight: FontWeight.bold)),
        ),
        title: Text(client.name, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Text([
          if (client.phone != null) client.phone!,
          if (client.categoryName != null) client.categoryName!,
        ].join(' · '), overflow: TextOverflow.ellipsis),
        trailing: Chip(
          label: Text(AppConstants.statusLabels[client.status] ?? client.status,
              style: const TextStyle(fontSize: 11)),
          backgroundColor: _statusColor(client.status).withOpacity(0.12),
          padding: EdgeInsets.zero,
          materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
        ),
        onTap: () => Navigator.push(context,
            MaterialPageRoute(builder: (_) => ClientDetailScreen(client: client))),
      ),
    );
  }

  Color _statusColor(String s) {
    switch (s) {
      case 'active':   return Colors.green;
      case 'inactive': return Colors.grey;
      case 'prospect': return Colors.orange;
      case 'lead':     return Colors.blue;
      default:         return Colors.grey;
    }
  }
}
