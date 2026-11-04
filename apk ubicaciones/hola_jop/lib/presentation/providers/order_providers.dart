import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import '../../data/services/laudus_service.dart';
import '../../domain/entities/order.dart';

// ── Productos Laudus ──────────────────────────────────────────────────────────

final laudusProductsProvider = StateNotifierProvider<ProductsNotifier, AsyncValue<List<LaudusProduct>>>(
  (_) => ProductsNotifier(),
);

class ProductsNotifier extends StateNotifier<AsyncValue<List<LaudusProduct>>> {
  ProductsNotifier() : super(const AsyncLoading()) { load(); }

  Future<void> load() async {
    state = const AsyncLoading();
    try {
      state = AsyncData(await LaudusService.getProducts());
    } catch (e, s) {
      state = AsyncError(e, s);
    }
  }

  Future<void> reload() => load();
}

// ── Búsqueda de productos ─────────────────────────────────────────────────────

final productSearchProvider = StateProvider<String>((_) => '');

final filteredProductsProvider = Provider<AsyncValue<List<LaudusProduct>>>((ref) {
  final products = ref.watch(laudusProductsProvider);
  final search = ref.watch(productSearchProvider).toLowerCase();
  return products.whenData((list) {
    if (search.isEmpty) return list;
    return list.where((p) =>
      p.sku.toLowerCase().contains(search) ||
      p.description.toLowerCase().contains(search) ||
      (p.categoryName?.toLowerCase().contains(search) ?? false),
    ).toList();
  });
});

// ── Pedido en construcción ────────────────────────────────────────────────────

final currentOrderProvider = StateNotifierProvider<OrderBuilder, Order?>((ref) => OrderBuilder());

class OrderBuilder extends StateNotifier<Order?> {
  OrderBuilder() : super(null);

  void start({required String clientUuid, required String clientName}) {
    final now = DateTime.now();
    state = Order(
      uuid: const Uuid().v4(),
      clientUuid: clientUuid,
      clientName: clientName,
      items: [],
      createdAt: now,
      updatedAt: now,
    );
  }

  void addProduct(LaudusProduct product) {
    if (state == null) return;
    final items = List<OrderItem>.from(state!.items);
    final idx = items.indexWhere((i) => i.product.sku == product.sku);
    if (idx >= 0) {
      items[idx] = items[idx].copyWith(quantity: items[idx].quantity + 1);
    } else {
      items.add(OrderItem(product: product));
    }
    state = Order(
      uuid: state!.uuid, clientUuid: state!.clientUuid, clientName: state!.clientName,
      items: items, status: state!.status, notes: state!.notes,
      createdAt: state!.createdAt, updatedAt: DateTime.now(),
    );
  }

  void removeProduct(String sku) {
    if (state == null) return;
    final items = List<OrderItem>.from(state!.items)..removeWhere((i) => i.product.sku == sku);
    state = Order(
      uuid: state!.uuid, clientUuid: state!.clientUuid, clientName: state!.clientName,
      items: items, status: state!.status, notes: state!.notes,
      createdAt: state!.createdAt, updatedAt: DateTime.now(),
    );
  }

  void updateQuantity(String sku, int quantity) {
    if (state == null) return;
    if (quantity <= 0) { removeProduct(sku); return; }
    final items = List<OrderItem>.from(state!.items);
    final idx = items.indexWhere((i) => i.product.sku == sku);
    if (idx >= 0) items[idx] = items[idx].copyWith(quantity: quantity);
    state = Order(
      uuid: state!.uuid, clientUuid: state!.clientUuid, clientName: state!.clientName,
      items: items, status: state!.status, notes: state!.notes,
      createdAt: state!.createdAt, updatedAt: DateTime.now(),
    );
  }

  void setNotes(String notes) {
    if (state == null) return;
    state = Order(
      uuid: state!.uuid, clientUuid: state!.clientUuid, clientName: state!.clientName,
      items: state!.items, status: state!.status, notes: notes.isEmpty ? null : notes,
      createdAt: state!.createdAt, updatedAt: DateTime.now(),
    );
  }

  void clear() => state = null;

  int quantityOf(String sku) =>
    state?.items.firstWhere((i) => i.product.sku == sku, orElse: () => OrderItem(product: state!.items.first.product, quantity: 0)).quantity ?? 0;
}

// ── Historial de pedidos (guardados localmente) ───────────────────────────────

final ordersHistoryProvider = StateNotifierProvider<OrdersHistory, List<Order>>(
  (_) => OrdersHistory(),
);

class OrdersHistory extends StateNotifier<List<Order>> {
  OrdersHistory() : super([]);

  void add(Order order) => state = [order, ...state];

  void updateStatus(String uuid, String status) {
    state = state.map((o) => o.uuid == uuid
      ? Order(uuid: o.uuid, clientUuid: o.clientUuid, clientName: o.clientName,
          items: o.items, status: status, notes: o.notes,
          createdAt: o.createdAt, updatedAt: DateTime.now())
      : o).toList();
  }
}
