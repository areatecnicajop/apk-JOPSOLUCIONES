import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../data/services/pedido_service.dart';
import '../../../domain/entities/client.dart';
import '../../../domain/entities/order.dart';
import '../../providers/order_providers.dart';
import 'product_picker_screen.dart';
import 'order_summary_screen.dart';

class OrderScreen extends ConsumerStatefulWidget {
  final Client client;
  const OrderScreen({super.key, required this.client});

  @override
  ConsumerState<OrderScreen> createState() => _OrderScreenState();
}

class _OrderScreenState extends ConsumerState<OrderScreen> {
  final _notesController = TextEditingController();

  @override
  void initState() {
    super.initState();
    // Inicia un nuevo pedido para este cliente
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(currentOrderProvider.notifier).start(
        clientUuid: widget.client.uuid,
        clientName: widget.client.name,
      );
    });
  }

  @override
  void dispose() { _notesController.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    final order = ref.watch(currentOrderProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
          const Text('Nuevo Pedido', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
          Text(widget.client.name, style: TextStyle(fontSize: 12, color: theme.colorScheme.onSurface.withOpacity(0.7))),
        ]),
        actions: [
          if (order != null && order.items.isNotEmpty)
            TextButton.icon(
              onPressed: () => _goToSummary(context, order),
              icon: const Icon(Icons.receipt_long),
              label: Text('Ver (${order.totalItems})'),
            ),
        ],
      ),
      body: order == null
        ? const Center(child: CircularProgressIndicator())
        : Column(children: [
            // ── Banner cliente ───────────────────────────────────────────
            _ClientBanner(client: widget.client),

            // ── Carrito rápido ───────────────────────────────────────────
            if (order.items.isNotEmpty) _CartSummaryBar(order: order),

            // ── Cuerpo: agregar productos ────────────────────────────────
            Expanded(
              child: order.items.isEmpty
                ? _EmptyOrderView(onAddProducts: () => _openPicker(context))
                : _OrderItemsList(order: order, onAddMore: () => _openPicker(context)),
            ),

            // ── Notas ────────────────────────────────────────────────────
            if (order.items.isNotEmpty) _NotesSection(controller: _notesController,
              onChanged: (v) => ref.read(currentOrderProvider.notifier).setNotes(v)),

            // ── Botón confirmar ──────────────────────────────────────────
            if (order.items.isNotEmpty) _ConfirmButton(order: order, onConfirm: () => _confirm(context, order)),
          ]),

      // ── FAB agregar producto ─────────────────────────────────────────────
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _openPicker(context),
        icon: const Icon(Icons.add_shopping_cart),
        label: const Text('Agregar producto'),
      ),
    );
  }

  void _openPicker(BuildContext context) {
    Navigator.push(context, MaterialPageRoute(builder: (_) =>
      ProviderScope(parent: ProviderScope.containerOf(context), child: const ProductPickerScreen())));
  }

  void _goToSummary(BuildContext context, Order order) {
    Navigator.push(context, MaterialPageRoute(builder: (_) =>
      ProviderScope(parent: ProviderScope.containerOf(context), child: OrderSummaryScreen(order: order))));
  }

  Future<void> _confirm(BuildContext context, Order order) async {
    final result = await Navigator.push<bool>(context, MaterialPageRoute(builder: (_) =>
      ProviderScope(parent: ProviderScope.containerOf(context), child: OrderSummaryScreen(order: order, confirmMode: true))));
    if (result == true && mounted) {
      // Guardar en BD
      try {
        await PedidoService.createPedido(order);
      } catch (e) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text('⚠️ Pedido creado localmente, error al guardar en servidor: $e'),
            backgroundColor: Colors.orange, behavior: SnackBarBehavior.floating));
        }
      }
      ref.read(ordersHistoryProvider.notifier).add(order);
      ref.read(currentOrderProvider.notifier).clear();
      Navigator.pop(context);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
          content: Text('✅ Pedido guardado para ${widget.client.name}'),
          backgroundColor: Colors.green, behavior: SnackBarBehavior.floating));
      }
    }
  }
}

// ── Banner del cliente ────────────────────────────────────────────────────────

class _ClientBanner extends StatelessWidget {
  final Client client;
  const _ClientBanner({required this.client});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      color: Theme.of(context).colorScheme.primaryContainer.withOpacity(0.4),
      child: Row(children: [
        CircleAvatar(
          backgroundColor: Theme.of(context).colorScheme.primary,
          foregroundColor: Colors.white,
          radius: 18,
          child: Text(client.name[0].toUpperCase(), style: const TextStyle(fontWeight: FontWeight.bold)),
        ),
        const SizedBox(width: 10),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(client.name, style: const TextStyle(fontWeight: FontWeight.bold)),
          if (client.address != null) Text(client.address!, style: const TextStyle(fontSize: 12), overflow: TextOverflow.ellipsis),
        ])),
        if (client.phone != null) Row(children: [
          const Icon(Icons.phone, size: 14),
          const SizedBox(width: 4),
          Text(client.phone!, style: const TextStyle(fontSize: 12)),
        ]),
      ]),
    );
  }
}

// ── Barra resumen carrito ─────────────────────────────────────────────────────

class _CartSummaryBar extends StatelessWidget {
  final Order order;
  const _CartSummaryBar({required this.order});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      color: Theme.of(context).colorScheme.primary,
      child: Row(children: [
        const Icon(Icons.shopping_cart, color: Colors.white, size: 18),
        const SizedBox(width: 8),
        Text('${order.totalItems} item${order.totalItems != 1 ? 's' : ''}',
          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w500)),
        const Spacer(),
        Text('Total: ${order.totalDisplay}',
          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)),
      ]),
    );
  }
}

// ── Vista vacía ───────────────────────────────────────────────────────────────

class _EmptyOrderView extends StatelessWidget {
  final VoidCallback onAddProducts;
  const _EmptyOrderView({required this.onAddProducts});

  @override
  Widget build(BuildContext context) {
    return Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
      Icon(Icons.shopping_cart_outlined, size: 80, color: Colors.grey.shade300),
      const SizedBox(height: 16),
      Text('Sin productos aún', style: Theme.of(context).textTheme.titleMedium?.copyWith(color: Colors.grey)),
      const SizedBox(height: 8),
      const Text('Agrega productos al pedido', style: TextStyle(color: Colors.grey)),
      const SizedBox(height: 24),
      FilledButton.icon(onPressed: onAddProducts, icon: const Icon(Icons.add), label: const Text('Buscar productos')),
    ]));
  }
}

// ── Lista de ítems ────────────────────────────────────────────────────────────

class _OrderItemsList extends ConsumerWidget {
  final Order order;
  final VoidCallback onAddMore;
  const _OrderItemsList({required this.order, required this.onAddMore});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ListView(padding: const EdgeInsets.all(12), children: [
      ...order.items.map((item) => _OrderItemCard(item: item, ref: ref)),
      const SizedBox(height: 8),
      OutlinedButton.icon(onPressed: onAddMore, icon: const Icon(Icons.add), label: const Text('Agregar más productos')),
      const SizedBox(height: 80),
    ]);
  }
}

class _OrderItemCard extends StatelessWidget {
  final OrderItem item;
  final WidgetRef ref;
  const _OrderItemCard({required this.item, required this.ref});

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Row(children: [
          // SKU badge
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.primaryContainer,
              borderRadius: BorderRadius.circular(6)),
            child: Text(item.product.sku, style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Theme.of(context).colorScheme.primary)),
          ),
          const SizedBox(width: 10),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(item.product.description, style: const TextStyle(fontWeight: FontWeight.w600), maxLines: 2, overflow: TextOverflow.ellipsis),
            Text(item.product.priceDisplay, style: TextStyle(color: Theme.of(context).colorScheme.primary, fontWeight: FontWeight.bold)),
          ])),
          const SizedBox(width: 8),
          // Cantidad
          Row(children: [
            IconButton(
              icon: const Icon(Icons.remove_circle_outline),
              onPressed: () => ref.read(currentOrderProvider.notifier).updateQuantity(item.product.sku, item.quantity - 1),
              iconSize: 22, padding: EdgeInsets.zero, constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
            ),
            Container(
              width: 32, alignment: Alignment.center,
              child: Text('${item.quantity}', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            ),
            IconButton(
              icon: const Icon(Icons.add_circle_outline),
              onPressed: () => ref.read(currentOrderProvider.notifier).updateQuantity(item.product.sku, item.quantity + 1),
              iconSize: 22, padding: EdgeInsets.zero, constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
            ),
          ]),
          const SizedBox(width: 4),
          Column(children: [
            Text(item.subtotalDisplay, style: const TextStyle(fontWeight: FontWeight.bold)),
            IconButton(
              icon: const Icon(Icons.delete_outline, color: Colors.red, size: 18),
              onPressed: () => ref.read(currentOrderProvider.notifier).removeProduct(item.product.sku),
              padding: EdgeInsets.zero, constraints: const BoxConstraints(minWidth: 28, minHeight: 28),
            ),
          ]),
        ]),
      ),
    );
  }
}

// ── Notas ─────────────────────────────────────────────────────────────────────

class _NotesSection extends StatelessWidget {
  final TextEditingController controller;
  final ValueChanged<String> onChanged;
  const _NotesSection({required this.controller, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
      child: TextField(
        controller: controller,
        decoration: const InputDecoration(labelText: 'Notas del pedido (opcional)', prefixIcon: Icon(Icons.notes), isDense: true),
        maxLines: 2,
        onChanged: onChanged,
      ),
    );
  }
}

// ── Botón confirmar ───────────────────────────────────────────────────────────

class _ConfirmButton extends StatelessWidget {
  final Order order;
  final VoidCallback onConfirm;
  const _ConfirmButton({required this.order, required this.onConfirm});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 0, 12, 16),
      child: SizedBox(
        width: double.infinity,
        child: FilledButton.icon(
          onPressed: onConfirm,
          icon: const Icon(Icons.check_circle),
          label: Text('Confirmar pedido · ${order.totalDisplay}'),
          style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
        ),
      ),
    );
  }
}
