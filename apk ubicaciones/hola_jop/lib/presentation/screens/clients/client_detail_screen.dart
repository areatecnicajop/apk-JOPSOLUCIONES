import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../core/constants/app_constants.dart';
import '../../../data/services/pedido_service.dart';
import '../../../domain/entities/client.dart';
import '../../providers/providers.dart';
import '../orders/order_screen.dart';
import 'client_form_screen.dart';

class ClientDetailScreen extends ConsumerWidget {
  final Client client;
  const ClientDetailScreen({super.key, required this.client});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final theme = Theme.of(context);
    final fmt   = DateFormat('dd/MM/yyyy HH:mm');
    final fmtD  = DateFormat('dd/MM/yyyy');

    return Scaffold(
      appBar: AppBar(
        title: Text(client.name, overflow: TextOverflow.ellipsis),
        actions: [
          IconButton(
            icon: const Icon(Icons.add_shopping_cart),
            tooltip: 'Crear pedido',
            onPressed: () => Navigator.push(context, MaterialPageRoute(
              builder: (_) => ProviderScope(
                parent: ProviderScope.containerOf(context),
                child: OrderScreen(client: client),
              ),
            )),
          ),
          IconButton(
            icon: const Icon(Icons.edit),
            onPressed: () async {
              final updated = await Navigator.push<Client>(context,
                MaterialPageRoute(builder: (_) => ClientFormScreen(existing: client)));
              if (updated != null && context.mounted) Navigator.pop(context, updated);
            },
          ),
          IconButton(
            icon: const Icon(Icons.delete_outline, color: Colors.red),
            onPressed: () async {
              final confirm = await showDialog<bool>(
                context: context,
                builder: (_) => AlertDialog(
                  title: const Text('Eliminar cliente'),
                  content: Text('¿Eliminar a ${client.name}?'),
                  actions: [
                    TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
                    FilledButton(
                      onPressed: () => Navigator.pop(context, true),
                      style: FilledButton.styleFrom(backgroundColor: Colors.red),
                      child: const Text('Eliminar'),
                    ),
                  ],
                ),
              );
              if (confirm == true && context.mounted) {
                await ref.read(clientsProvider.notifier).delete(client.uuid);
                Navigator.pop(context, 'deleted');
              }
            },
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Status
          Align(
            alignment: Alignment.centerLeft,
            child: Chip(
              label: Text(AppConstants.statusLabels[client.status] ?? client.status),
              backgroundColor: _statusColor(client.status).withOpacity(0.15),
              labelStyle: TextStyle(color: _statusColor(client.status), fontWeight: FontWeight.bold),
            ),
          ),
          if (client.categoryName != null) ...[
            const SizedBox(height: 4),
            Align(alignment: Alignment.centerLeft,
              child: Chip(label: Text(client.categoryName!), avatar: const Icon(Icons.category, size: 16))),
          ],
          const SizedBox(height: 16),

          // Info contacto
          _infoCard(context, [
            _row(context, Icons.phone, 'Teléfono', client.phone ?? '—'),
            _row(context, Icons.email, 'Correo', client.email ?? '—'),
            _row(context, Icons.home, 'Dirección', client.address ?? '—'),
          ]),

          if (client.notes != null && client.notes!.isNotEmpty) ...[
            const SizedBox(height: 12),
            Card(child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Row(children: [
                  Icon(Icons.notes, size: 18, color: theme.colorScheme.primary),
                  const SizedBox(width: 6),
                  Text('Notas', style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold)),
                ]),
                const SizedBox(height: 8),
                Text(client.notes!),
              ]),
            )),
          ],

          const SizedBox(height: 16),

          // ── Pedidos ──────────────────────────────────────────────────────
          Row(children: [
            Icon(Icons.shopping_bag, size: 18, color: theme.colorScheme.primary),
            const SizedBox(width: 6),
            Text('Pedidos', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
            const Spacer(),
            TextButton.icon(
              onPressed: () => Navigator.push(context, MaterialPageRoute(
                builder: (_) => ProviderScope(
                  parent: ProviderScope.containerOf(context),
                  child: OrderScreen(client: client),
                ),
              )),
              icon: const Icon(Icons.add, size: 16),
              label: const Text('Nuevo'),
            ),
          ]),
          const SizedBox(height: 8),

          FutureBuilder<List<Map<String, dynamic>>>(
            future: PedidoService.getByCliente(client.uuid),
            builder: (context, snap) {
              if (snap.connectionState == ConnectionState.waiting) {
                return const Padding(
                  padding: EdgeInsets.all(16),
                  child: Center(child: CircularProgressIndicator()),
                );
              }
              final pedidos = snap.data ?? [];
              if (pedidos.isEmpty) {
                return Container(
                  padding: const EdgeInsets.all(20),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.surfaceContainerHighest.withOpacity(0.5),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Center(
                    child: Text('Sin pedidos todavía',
                        style: TextStyle(color: Colors.grey)),
                  ),
                );
              }
              return Column(
                children: pedidos.map((p) => _PedidoCard(pedido: p, fmtD: fmtD)).toList(),
              );
            },
          ),

          const SizedBox(height: 12),
          _infoCard(context, [
            _row(context, Icons.calendar_today, 'Creado', fmt.format(client.createdAt)),
            _row(context, Icons.update, 'Actualizado', fmt.format(client.updatedAt)),
          ]),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Widget _infoCard(BuildContext ctx, List<Widget> children) => Card(
    child: Padding(padding: const EdgeInsets.all(16), child: Column(children: children)),
  );

  Widget _row(BuildContext ctx, IconData icon, String label, String value) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 6),
    child: Row(children: [
      Icon(icon, size: 18, color: Theme.of(ctx).colorScheme.primary),
      const SizedBox(width: 10),
      SizedBox(width: 90, child: Text(label, style: const TextStyle(fontWeight: FontWeight.w600))),
      Expanded(child: Text(value, overflow: TextOverflow.ellipsis)),
    ]),
  );

  Color _statusColor(String status) => switch (status) {
    'active'   => Colors.green,
    'inactive' => Colors.grey,
    'prospect' => Colors.orange,
    'lead'     => Colors.blue,
    _          => Colors.grey,
  };
}

// ── Tarjeta de pedido ────────────────────────────────────────────────────────

class _PedidoCard extends StatelessWidget {
  final Map<String, dynamic> pedido;
  final DateFormat fmtD;
  const _PedidoCard({required this.pedido, required this.fmtD});

  Color _statusColor(String s) => switch (s) {
    'confirmed'  => Colors.green,
    'cancelled'  => Colors.red,
    _            => Colors.orange,
  };

  String _statusLabel(String s) => switch (s) {
    'confirmed' => 'Confirmado',
    'cancelled' => 'Cancelado',
    _           => 'Pendiente',
  };

  String _formatPrice(dynamic v) {
    final n = (v is num) ? v.toDouble() : double.tryParse(v.toString()) ?? 0;
    return '\$${n.round().toString().replaceAllMapped(RegExp(r'(\d)(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')}';
  }

  @override
  Widget build(BuildContext context) {
    final status = pedido['status'] as String? ?? 'pending';
    final total  = pedido['total'];
    final date   = pedido['createdAt'] as String? ?? '';
    final notes  = pedido['notes'] as String?;

    List<dynamic> items = [];
    try {
      final raw = pedido['items'];
      items = raw is String ? jsonDecode(raw) : (raw as List? ?? []);
    } catch (_) {}

    DateTime? dt;
    try { dt = DateTime.parse(date); } catch (_) {}

    final color = _statusColor(status);

    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: ExpansionTile(
        leading: Container(
          width: 40, height: 40,
          decoration: BoxDecoration(
            color: color.withOpacity(0.12),
            borderRadius: BorderRadius.circular(8),
          ),
          child: Icon(Icons.receipt_long, color: color, size: 22),
        ),
        title: Text(
          dt != null ? fmtD.format(dt) : date,
          style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
        ),
        subtitle: Row(children: [
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
            decoration: BoxDecoration(
              color: color.withOpacity(0.1),
              borderRadius: BorderRadius.circular(4),
            ),
            child: Text(_statusLabel(status),
                style: TextStyle(fontSize: 10, color: color, fontWeight: FontWeight.bold)),
          ),
          const SizedBox(width: 8),
          Text('${items.length} producto${items.length != 1 ? 's' : ''}',
              style: const TextStyle(fontSize: 11, color: Colors.grey)),
        ]),
        trailing: Text(
          _formatPrice(total),
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
        ),
        children: [
          if (notes != null && notes.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Row(children: [
                const Icon(Icons.notes, size: 14, color: Colors.grey),
                const SizedBox(width: 6),
                Expanded(child: Text(notes, style: const TextStyle(fontSize: 12, color: Colors.grey))),
              ]),
            ),
          ...items.map((item) {
            final m = item as Map<String, dynamic>;
            return ListTile(
              dense: true,
              leading: const Icon(Icons.inventory_2_outlined, size: 18),
              title: Text(m['name']?.toString() ?? m['sku']?.toString() ?? '—',
                  style: const TextStyle(fontSize: 13)),
              subtitle: Text('SKU: ${m['sku'] ?? '—'}  ·  Qty: ${m['qty'] ?? 1}',
                  style: const TextStyle(fontSize: 11)),
              trailing: Text(_formatPrice(m['total'] ?? 0),
                  style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
            );
          }),
          const SizedBox(height: 8),
        ],
      ),
    );
  }
}
