// ── Producto del catálogo Laudus ─────────────────────────────────────────────
class LaudusProduct {
  final String sku;
  final String description;
  final double unitPriceWithTaxes;
  final int stock;
  final String? categoryName;
  final bool webStatus;
  final String? webDescription;
  final String? productCategoryId;

  const LaudusProduct({
    required this.sku,
    required this.description,
    required this.unitPriceWithTaxes,
    this.stock = 0,
    this.categoryName,
    this.webStatus = true,
    this.webDescription,
    this.productCategoryId,
  });

  // El precio viene directo de Laudus en CLP (sin centavos)
  double get priceFormatted => unitPriceWithTaxes;

  String get priceDisplay {
    final p = unitPriceWithTaxes;
    // Formato chileno: $8.500
    if (p == 0) return '\$0';
    final intVal = p.toInt();
    final formatted = intVal.toString().replaceAllMapped(
      RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
      (m) => '${m[1]}.',
    );
    return '\$$formatted';
  }

  /// Desde caché Hive (formato guardado)
  factory LaudusProduct.fromJson(Map<String, dynamic> json) {
    return LaudusProduct(
      sku: json['sku']?.toString() ?? '',
      description: json['name']?.toString() ?? json['nombre']?.toString() ?? '',
      unitPriceWithTaxes: (json['unitPriceWithTaxes'] as num?)?.toDouble()
          ?? (json['precio'] as num?)?.toDouble() ?? 0,
      stock: (json['stock'] as num?)?.toInt() ?? 0,
      categoryName: json['categoryName']?.toString() ?? json['categoriaNombre']?.toString(),
      webStatus: true,
      webDescription: json['webDescription']?.toString() ?? json['descripcion']?.toString(),
      productCategoryId: json['productCategoryId']?.toString() ?? json['categoriaId']?.toString(),
    );
  }

  /// Desde el backend propio — campos en inglés (endpoint /api/products)
  factory LaudusProduct.fromBackend(Map<String, dynamic> json) {
    return LaudusProduct(
      sku: json['sku']?.toString() ?? '',
      description: json['name']?.toString() ?? json['nombre']?.toString() ?? '',
      unitPriceWithTaxes: (json['unitPriceWithTaxes'] as num?)?.toDouble()
          ?? (json['precio'] as num?)?.toDouble() ?? 0,
      stock: (json['stock'] as num?)?.toInt() ?? 0,
      categoryName: json['categoryName']?.toString() ?? json['categoriaNombre']?.toString(),
      webStatus: true,
      webDescription: json['webDescription']?.toString() ?? json['descripcion']?.toString(),
      productCategoryId: json['productCategoryId']?.toString() ?? json['categoriaId']?.toString(),
    );
  }

  Map<String, dynamic> toJson() => {
    'sku': sku,
    'name': description,
    'unitPriceWithTaxes': unitPriceWithTaxes,
    'stock': stock,
    'categoryName': categoryName,
    'webDescription': webDescription,
    'productCategoryId': productCategoryId,
  };
}

// ── Ítem de un pedido ────────────────────────────────────────────────────────
class OrderItem {
  final LaudusProduct product;
  int quantity;
  String? notes;

  OrderItem({required this.product, this.quantity = 1, this.notes});

  double get subtotal => product.unitPriceWithTaxes * quantity;

  String get subtotalDisplay {
    final s = subtotal.toInt();
    final formatted = s.toString().replaceAllMapped(
      RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
      (m) => '${m[1]}.',
    );
    return '\$$formatted';
  }

  OrderItem copyWith({int? quantity, String? notes}) =>
    OrderItem(product: product, quantity: quantity ?? this.quantity, notes: notes ?? this.notes);
}

// ── Pedido completo ──────────────────────────────────────────────────────────
class Order {
  final String uuid;
  final String clientUuid;
  final String clientName;
  final List<OrderItem> items;
  final String status; // draft, sent, confirmed, delivered, cancelled
  final String? notes;
  final DateTime createdAt;
  final DateTime updatedAt;

  const Order({
    required this.uuid,
    required this.clientUuid,
    required this.clientName,
    required this.items,
    this.status = 'draft',
    this.notes,
    required this.createdAt,
    required this.updatedAt,
  });

  double get total => items.fold(0, (sum, item) => sum + item.subtotal);

  String get totalDisplay {
    final t = total.toInt();
    final formatted = t.toString().replaceAllMapped(
      RegExp(r'(\d{1,3})(?=(\d{3})+(?!\d))'),
      (m) => '${m[1]}.',
    );
    return '\$$formatted';
  }

  int get totalItems => items.fold(0, (sum, item) => sum + item.quantity);
}

extension on double {
  double truncate() => toInt().toDouble();
}
