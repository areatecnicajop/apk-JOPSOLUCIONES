import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../domain/entities/order.dart';
import '../../providers/order_providers.dart';

class ProductPickerScreen extends ConsumerStatefulWidget {
  const ProductPickerScreen({super.key});
  @override
  ConsumerState<ProductPickerScreen> createState() => _ProductPickerScreenState();
}

class _ProductPickerScreenState extends ConsumerState<ProductPickerScreen> {
  final _search = TextEditingController();
  String? _selectedCategory;

  @override
  void dispose() { _search.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) {
    final products = ref.watch(filteredProductsProvider);
    final order = ref.watch(currentOrderProvider);

    // Categorías únicas
    final categories = products.whenData((list) =>
      list.map((p) => p.categoryName ?? 'Sin categoría').toSet().toList()..sort()).value ?? [];

    return Scaffold(
      appBar: AppBar(
        title: const Text('Agregar Productos'),
        actions: [
          if (order != null && order.items.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(right: 12),
              child: Center(child: Badge(
                label: Text('${order.totalItems}'),
                child: const Icon(Icons.shopping_cart),
              )),
            ),
        ],
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(52),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
            child: TextField(
              controller: _search,
              decoration: InputDecoration(
                hintText: 'Buscar por SKU, nombre o categoría...',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _search.text.isNotEmpty
                  ? IconButton(icon: const Icon(Icons.clear), onPressed: () {
                      _search.clear();
                      ref.read(productSearchProvider.notifier).state = '';
                    }) : null,
                isDense: true, filled: true,
                fillColor: Theme.of(context).colorScheme.surface,
              ),
              onChanged: (v) => ref.read(productSearchProvider.notifier).state = v,
            ),
          ),
        ),
      ),
      body: Column(children: [
        // Filtro categorías
        if (categories.isNotEmpty) SizedBox(
          height: 42,
          child: ListView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            children: [
              Padding(padding: const EdgeInsets.only(right: 8), child: FilterChip(
                label: const Text('Todos'),
                selected: _selectedCategory == null,
                onSelected: (_) { setState(() => _selectedCategory = null); ref.read(productSearchProvider.notifier).state = _search.text; },
              )),
              ...categories.map((cat) => Padding(padding: const EdgeInsets.only(right: 8), child: FilterChip(
                label: Text(cat),
                selected: _selectedCategory == cat,
                onSelected: (v) {
                  setState(() => _selectedCategory = v ? cat : null);
                  ref.read(productSearchProvider.notifier).state = v ? cat : _search.text;
                },
              ))),
            ],
          ),
        ),

        // Lista de productos
        Expanded(child: products.when(
          loading: () => const Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
            CircularProgressIndicator(),
            SizedBox(height: 12),
            Text('Cargando productos Laudus...'),
          ])),
          error: (err, _) => Center(child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
              const Icon(Icons.cloud_off, size: 48, color: Colors.orange),
              const SizedBox(height: 12),
              const Text('Sin conexión al backend', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
              const SizedBox(height: 8),
              Text('$err', style: const TextStyle(fontSize: 12, color: Colors.grey), textAlign: TextAlign.center),
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: () => ref.read(laudusProductsProvider.notifier).reload(),
                icon: const Icon(Icons.refresh),
                label: const Text('Reintentar'),
              ),
              const SizedBox(height: 8),
              const Text('Asegúrate de estar en la misma red WiFi que el PC', style: TextStyle(fontSize: 11, color: Colors.grey)),
            ]),
          )),
          data: (list) => list.isEmpty
            ? const Center(child: Text('Sin resultados'))
            : ListView.separated(
                padding: const EdgeInsets.all(12),
                itemCount: list.length,
                separatorBuilder: (_, __) => const SizedBox(height: 4),
                itemBuilder: (_, i) => _ProductTile(product: list[i]),
              ),
        )),
      ]),
      bottomNavigationBar: order != null && order.items.isNotEmpty
        ? SafeArea(child: Padding(
            padding: const EdgeInsets.all(12),
            child: FilledButton.icon(
              onPressed: () => Navigator.pop(context),
              icon: const Icon(Icons.check),
              label: Text('Listo · ${order.totalItems} producto${order.totalItems != 1 ? 's' : ''} · ${order.totalDisplay}'),
            ),
          ))
        : null,
    );
  }
}

class _ProductTile extends ConsumerWidget {
  final LaudusProduct product;
  const _ProductTile({required this.product});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final order = ref.watch(currentOrderProvider);
    final qty = order?.items.where((i) => i.product.sku == product.sku).firstOrNull?.quantity ?? 0;
    final inCart = qty > 0;
    final stock = product.stock;
    final noStock = stock <= 0;
    final atMax = qty >= stock && stock > 0;

    Color stockColor = Colors.green.shade700;
    if (stock <= 0) stockColor = Colors.red.shade700;
    else if (stock <= 5) stockColor = Colors.orange.shade700;

    return Card(
      color: noStock
          ? Colors.grey.shade100
          : inCart ? Theme.of(context).colorScheme.primaryContainer.withOpacity(0.3) : null,
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: noStock ? null : () => ref.read(currentOrderProvider.notifier).addProduct(product),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          child: Row(children: [
            // SKU
            Container(
              width: 70,
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
              decoration: BoxDecoration(
                color: noStock
                    ? Colors.grey.shade300
                    : inCart ? Theme.of(context).colorScheme.primary : Theme.of(context).colorScheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(6)),
              child: Text(product.sku, textAlign: TextAlign.center,
                style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold,
                  color: inCart && !noStock ? Colors.white : Theme.of(context).colorScheme.onSurface)),
            ),
            const SizedBox(width: 10),
            // Info
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(product.description,
                style: TextStyle(fontWeight: FontWeight.w600, color: noStock ? Colors.grey : null),
                maxLines: 2, overflow: TextOverflow.ellipsis),
              Row(children: [
                if (product.categoryName != null) ...[
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(color: Colors.grey.shade100, borderRadius: BorderRadius.circular(4)),
                    child: Text(product.categoryName!, style: const TextStyle(fontSize: 10, color: Colors.grey)),
                  ),
                  const SizedBox(width: 6),
                ],
                Text(product.priceDisplay, style: TextStyle(
                  color: noStock ? Colors.grey : Theme.of(context).colorScheme.primary,
                  fontWeight: FontWeight.bold)),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 2),
                  decoration: BoxDecoration(
                    color: stockColor.withOpacity(0.12),
                    borderRadius: BorderRadius.circular(4)),
                  child: Text(
                    noStock ? 'Sin stock' : 'Stock: $stock',
                    style: TextStyle(fontSize: 10, color: stockColor, fontWeight: FontWeight.w600)),
                ),
              ]),
            ])),
            const SizedBox(width: 8),
            // Botones cantidad
            if (noStock)
              const Icon(Icons.block, color: Colors.grey, size: 22)
            else if (!inCart)
              FilledButton(
                onPressed: () => ref.read(currentOrderProvider.notifier).addProduct(product),
                style: FilledButton.styleFrom(minimumSize: const Size(36, 36), padding: EdgeInsets.zero),
                child: const Icon(Icons.add, size: 18),
              )
            else
              Row(children: [
                IconButton(icon: const Icon(Icons.remove_circle), iconSize: 22,
                  onPressed: () => ref.read(currentOrderProvider.notifier).updateQuantity(product.sku, qty - 1),
                  padding: EdgeInsets.zero, constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                  color: Theme.of(context).colorScheme.primary),
                SizedBox(width: 28, child: Text('$qty', textAlign: TextAlign.center,
                  style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16))),
                IconButton(icon: Icon(Icons.add_circle, color: atMax ? Colors.grey : Theme.of(context).colorScheme.primary), iconSize: 22,
                  onPressed: atMax ? null : () => ref.read(currentOrderProvider.notifier).addProduct(product),
                  padding: EdgeInsets.zero, constraints: const BoxConstraints(minWidth: 32, minHeight: 32)),
              ]),
          ]),
        ),
      ),
    );
  }
}
