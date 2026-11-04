import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../domain/entities/order.dart';

class OrderSummaryScreen extends ConsumerWidget {
  final Order order;
  final bool confirmMode;
  const OrderSummaryScreen({super.key, required this.order, this.confirmMode = false});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final fmt = DateFormat('dd/MM/yyyy HH:mm');
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(confirmMode ? 'Confirmar Pedido' : 'Resumen del Pedido'),
      ),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        // ── Header cliente ───────────────────────────────────────────────
        Card(child: Padding(padding: const EdgeInsets.all(16), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Icon(Icons.person, color: theme.colorScheme.primary),
            const SizedBox(width: 8),
            Text('Cliente', style: theme.textTheme.labelLarge?.copyWith(color: theme.colorScheme.primary, fontWeight: FontWeight.bold)),
          ]),
          const SizedBox(height: 8),
          Text(order.clientName, style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
          Text('Pedido #${order.uuid.substring(0, 8).toUpperCase()}', style: const TextStyle(color: Colors.grey, fontSize: 12)),
          Text(fmt.format(order.createdAt), style: const TextStyle(color: Colors.grey, fontSize: 12)),
        ]))),

        const SizedBox(height: 12),

        // ── Productos ────────────────────────────────────────────────────
        Card(child: Column(children: [
          Padding(padding: const EdgeInsets.all(16), child: Row(children: [
            Icon(Icons.shopping_cart, color: theme.colorScheme.primary),
            const SizedBox(width: 8),
            Text('Productos (${order.totalItems})', style: theme.textTheme.labelLarge?.copyWith(color: theme.colorScheme.primary, fontWeight: FontWeight.bold)),
          ])),
          const Divider(height: 1),
          ...order.items.map((item) => Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            child: Row(children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
                decoration: BoxDecoration(color: theme.colorScheme.primaryContainer, borderRadius: BorderRadius.circular(4)),
                child: Text(item.product.sku, style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: theme.colorScheme.primary)),
              ),
              const SizedBox(width: 10),
              Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(item.product.description, style: const TextStyle(fontWeight: FontWeight.w500), maxLines: 2),
                Text('${item.product.priceDisplay} × ${item.quantity}', style: const TextStyle(fontSize: 12, color: Colors.grey)),
              ])),
              Text(item.subtotalDisplay, style: const TextStyle(fontWeight: FontWeight.bold)),
            ]),
          )).toList(),
          const Divider(height: 1),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              Text('Total', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
              Text(order.totalDisplay, style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold, color: theme.colorScheme.primary)),
            ]),
          ),
        ])),

        // ── Notas ────────────────────────────────────────────────────────
        if (order.notes != null) ...[
          const SizedBox(height: 12),
          Card(child: Padding(padding: const EdgeInsets.all(16), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(children: [
              Icon(Icons.notes, color: theme.colorScheme.primary),
              const SizedBox(width: 8),
              Text('Notas', style: theme.textTheme.labelLarge?.copyWith(color: theme.colorScheme.primary, fontWeight: FontWeight.bold)),
            ]),
            const SizedBox(height: 8),
            Text(order.notes!),
          ]))),
        ],

        // ── Estado ───────────────────────────────────────────────────────
        const SizedBox(height: 12),
        Card(child: Padding(padding: const EdgeInsets.all(16), child: Row(children: [
          Icon(Icons.info_outline, color: theme.colorScheme.primary),
          const SizedBox(width: 8),
          Text('Estado: ', style: theme.textTheme.labelLarge?.copyWith(fontWeight: FontWeight.bold)),
          Chip(
            label: Text(_statusLabel(order.status)),
            backgroundColor: _statusColor(order.status).withOpacity(0.15),
            labelStyle: TextStyle(color: _statusColor(order.status), fontWeight: FontWeight.bold, fontSize: 12),
            padding: EdgeInsets.zero,
          ),
        ]))),

        const SizedBox(height: 32),
      ]),

      bottomNavigationBar: confirmMode ? SafeArea(child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(children: [
          Expanded(child: OutlinedButton.icon(
            onPressed: () => Navigator.pop(context, false),
            icon: const Icon(Icons.arrow_back), label: const Text('Modificar'),
          )),
          const SizedBox(width: 12),
          Expanded(flex: 2, child: FilledButton.icon(
            onPressed: () => _showConfirmDialog(context),
            icon: const Icon(Icons.check_circle),
            label: Text('Confirmar · ${order.totalDisplay}'),
            style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
          )),
        ]),
      )) : null,
    );
  }

  Future<void> _showConfirmDialog(BuildContext context) async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Confirmar Pedido'),
        content: Text('¿Confirmar el pedido de ${order.totalItems} producto${order.totalItems != 1 ? 's' : ''} por ${order.totalDisplay} para ${order.clientName}?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Confirmar')),
        ],
      ),
    );
    if (confirm == true && context.mounted) Navigator.pop(context, true);
  }

  String _statusLabel(String s) => switch (s) {
    'draft' => 'Borrador',
    'sent' => 'Enviado',
    'confirmed' => 'Confirmado',
    'delivered' => 'Entregado',
    'cancelled' => 'Cancelado',
    _ => s,
  };

  Color _statusColor(String s) => switch (s) {
    'draft' => Colors.orange,
    'sent' => Colors.blue,
    'confirmed' => Colors.green,
    'delivered' => Colors.teal,
    'cancelled' => Colors.red,
    _ => Colors.grey,
  };
}
