import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../providers/order_providers.dart';

class LocalProductsScreen extends ConsumerStatefulWidget {
  const LocalProductsScreen({super.key});
  @override
  ConsumerState<LocalProductsScreen> createState() => _LocalProductsScreenState();
}

class _LocalProductsScreenState extends ConsumerState<LocalProductsScreen> {
  final _search = TextEditingController();

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final products = ref.watch(filteredProductsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Catálogo local'),
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(56),
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
                      })
                    : null,
                isDense: true, filled: true,
                fillColor: Theme.of(context).colorScheme.surface,
              ),
              onChanged: (v) {
                ref.read(productSearchProvider.notifier).state = v;
                setState(() {});
              },
            ),
          ),
        ),
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.read(laudusProductsProvider.notifier).reload(),
        child: products.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              SizedBox(height: 300, child: Center(child: Text('Error: $e'))),
            ],
          ),
          data: (list) {
            if (list.isEmpty) {
              return ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: const [
                  SizedBox(
                    height: 300,
                    child: Center(child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(Icons.inventory_2_outlined, size: 64, color: Colors.grey),
                        SizedBox(height: 12),
                        Text('No hay productos en el catálogo local', style: TextStyle(color: Colors.grey)),
                        SizedBox(height: 4),
                        Text('Actualiza el catálogo desde Config', style: TextStyle(color: Colors.grey, fontSize: 12)),
                      ],
                    )),
                  ),
                ],
              );
            }
            return ListView.separated(
              physics: const AlwaysScrollableScrollPhysics(),
              padding: const EdgeInsets.all(8),
              itemCount: list.length,
              separatorBuilder: (_, __) => const SizedBox(height: 2),
              itemBuilder: (_, i) {
                final p = list[i];
                final sinStock = p.stock <= 0;
                return Card(
                  child: ListTile(
                    leading: CircleAvatar(
                      backgroundColor: (sinStock ? Colors.red : Colors.green).withOpacity(0.12),
                      child: Text(p.stock.toString(),
                          style: TextStyle(
                              color: sinStock ? Colors.red : Colors.green,
                              fontWeight: FontWeight.bold, fontSize: 12)),
                    ),
                    title: Text(p.description, style: const TextStyle(fontWeight: FontWeight.w600)),
                    subtitle: Text([
                      p.sku,
                      if (p.categoryName != null) p.categoryName!,
                    ].join(' · '), overflow: TextOverflow.ellipsis),
                    trailing: Text(p.priceDisplay, style: const TextStyle(fontWeight: FontWeight.bold)),
                  ),
                );
              },
            );
          },
        ),
      ),
    );
  }
}
