import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import '../../../data/services/pedido_service.dart';

class AllOrdersScreen extends StatefulWidget {
  const AllOrdersScreen({super.key});
  @override
  State<AllOrdersScreen> createState() => _AllOrdersScreenState();
}

class _AllOrdersScreenState extends State<AllOrdersScreen> {
  late Future<List<Map<String, dynamic>>> _future;
  String? _statusFilter;
  final _fmtD = DateFormat('dd/MM/yyyy HH:mm');

  @override
  void initState() {
    super.initState();
    _future = PedidoService.getAll();
  }

  Future<void> _reload() async {
    setState(() => _future = PedidoService.getAll());
    await _future;
  }

  double _totalOf(Map<String, dynamic> p) {
    final v = p['total'];
    return (v is num) ? v.toDouble() : double.tryParse(v.toString()) ?? 0;
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Pedidos totales')),
      body: RefreshIndicator(
        onRefresh: _reload,
        child: FutureBuilder<List<Map<String, dynamic>>>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snap.hasError) {
              return ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: [
                  SizedBox(height: 300, child: Center(child: Text('Error: ${snap.error}'))),
                ],
              );
            }

            final pedidos = snap.data ?? [];
            final filtrados = _statusFilter == null
                ? pedidos
                : pedidos.where((p) => (p['status'] as String? ?? 'pending') == _statusFilter).toList();
            final totalMonto = filtrados.fold<double>(0, (sum, p) => sum + _totalOf(p));

            return ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(12),
              children: [
                Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Row(children: [
                      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text('${filtrados.length}', style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold)),
                        const Text('pedidos', style: TextStyle(color: Colors.grey, fontSize: 12)),
                      ])),
                      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(_formatPrice(totalMonto), style: Theme.of(context).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold, color: Colors.green)),
                        const Text('monto total', style: TextStyle(color: Colors.grey, fontSize: 12)),
                      ])),
                    ]),
                  ),
                ),
                const SizedBox(height: 12),
                SizedBox(
                  height: 40,
                  child: ListView(scrollDirection: Axis.horizontal, children: [
                    _filterChip('Todos', null),
                    _filterChip('Pendiente', 'pending'),
                    _filterChip('Confirmado', 'confirmed'),
                    _filterChip('Cancelado', 'cancelled'),
                  ]),
                ),
                const SizedBox(height: 12),
                if (filtrados.isEmpty)
                  const Padding(
                    padding: EdgeInsets.only(top: 60),
                    child: Center(child: Text('Sin pedidos', style: TextStyle(color: Colors.grey))),
                  )
                else
                  ...filtrados.map((p) => _PedidoCard(pedido: p, fmtD: _fmtD)),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _filterChip(String label, String? value) => Padding(
    padding: const EdgeInsets.only(right: 8),
    child: FilterChip(
      label: Text(label),
      selected: _statusFilter == value,
      onSelected: (_) => setState(() => _statusFilter = value),
    ),
  );

  String _formatPrice(double v) =>
      '\$${v.round().toString().replaceAllMapped(RegExp(r'(\d)(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')}';
}

// ── Tarjeta de pedido (con nombre del cliente, a diferencia de la del detalle
// de cliente, ya que acá se listan pedidos de todos los clientes juntos) ────

class _PedidoCard extends StatelessWidget {
  final Map<String, dynamic> pedido;
  final DateFormat fmtD;
  const _PedidoCard({required this.pedido, required this.fmtD});

  Color _statusColor(String s) => switch (s) {
    'confirmed' => Colors.green,
    'cancelled' => Colors.red,
    _ => Colors.orange,
  };

  String _statusLabel(String s) => switch (s) {
    'confirmed' => 'Confirmado',
    'cancelled' => 'Cancelado',
    _ => 'Pendiente',
  };

  String _formatPrice(dynamic v) {
    final n = (v is num) ? v.toDouble() : double.tryParse(v.toString()) ?? 0;
    return '\$${n.round().toString().replaceAllMapped(RegExp(r'(\d)(?=(\d{3})+(?!\d))'), (m) => '${m[1]}.')}';
  }

  @override
  Widget build(BuildContext context) {
    final status = pedido['status'] as String? ?? 'pending';
    final total = pedido['total'];
    final date = pedido['createdAt'] as String? ?? '';
    final notes = pedido['notes'] as String?;
    final clienteName = pedido['clienteName'] as String? ?? 'Cliente';

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
        title: Text(clienteName, style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14)),
        subtitle: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(dt != null ? fmtD.format(dt) : date, style: const TextStyle(fontSize: 11, color: Colors.grey)),
          const SizedBox(height: 2),
          Row(children: [
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
