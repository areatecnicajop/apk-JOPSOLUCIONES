import 'dart:convert';
import 'package:http/http.dart' as http;
import '../../core/app_config.dart';
import '../../domain/entities/order.dart';

class PedidoService {
  static String get _base => AppConfig.instance.mainBase;

  static Future<void> createPedido(Order order) async {
    final body = jsonEncode({
      'uuid': order.uuid,
      'clienteUuid': order.clientUuid,
      'clienteName': order.clientName,
      'items': jsonEncode(order.items.map((i) => {
        'sku': i.product.sku,
        'name': i.product.description,
        'qty': i.quantity,
        'unitPrice': i.product.unitPriceWithTaxes,
        'total': i.product.unitPriceWithTaxes * i.quantity,
      }).toList()),
      'subtotal': order.total,
      'tax': 0.0,
      'total': order.total,
      'status': order.status ?? 'pending',
      'notes': order.notes,
      'createdAt': order.createdAt.toIso8601String(),
      'updatedAt': order.updatedAt.toIso8601String(),
    });
    final resp = await http.post(
      Uri.parse('$_base/api/pedidos'),
      headers: {'Content-Type': 'application/json'},
      body: body,
    ).timeout(const Duration(seconds: 15));
    if (resp.statusCode != 200 && resp.statusCode != 201) {
      throw Exception('Error guardando pedido: ${resp.statusCode}');
    }
  }

  static Future<List<Map<String, dynamic>>> getByCliente(String clienteUuid) async {
    final resp = await http
        .get(Uri.parse('$_base/api/pedidos/cliente/$clienteUuid'))
        .timeout(const Duration(seconds: 10));
    if (resp.statusCode == 200) {
      return List<Map<String, dynamic>>.from(jsonDecode(resp.body));
    }
    return [];
  }

  static Future<List<Map<String, dynamic>>> getAll() async {
    final resp = await http
        .get(Uri.parse('$_base/api/pedidos'))
        .timeout(const Duration(seconds: 15));
    if (resp.statusCode == 200) {
      return List<Map<String, dynamic>>.from(jsonDecode(resp.body));
    }
    throw Exception('Error cargando pedidos: ${resp.statusCode}');
  }

  static Future<void> updateStatus(String uuid, String status) async {
    await http.patch(
      Uri.parse('$_base/api/pedidos/$uuid/status'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({'status': status}),
    ).timeout(const Duration(seconds: 10));
  }
}
