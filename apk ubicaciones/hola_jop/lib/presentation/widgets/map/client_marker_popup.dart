import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/constants/app_constants.dart';
import '../../../domain/entities/client.dart';
import '../../../domain/entities/category.dart';
import '../../screens/clients/client_detail_screen.dart';
import '../../screens/clients/client_form_screen.dart';

class ClientMarkerPopup extends ConsumerWidget {
  final Client client;
  final Category? category;
  const ClientMarkerPopup({super.key, required this.client, this.category});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final color = category?.color ?? Colors.blue;
    return Container(
      padding: const EdgeInsets.all(20),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          CircleAvatar(backgroundColor: color, foregroundColor: Colors.white, child: Text(client.name.isNotEmpty ? client.name[0].toUpperCase() : '?', style: const TextStyle(fontWeight: FontWeight.bold))),
          const SizedBox(width: 12),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(client.name, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
            if (client.categoryName != null) Text(client.categoryName!, style: TextStyle(color: color, fontSize: 12)),
          ])),
          Chip(
            label: Text(AppConstants.statusLabels[client.status] ?? client.status, style: const TextStyle(fontSize: 11)),
            backgroundColor: _statusColor(client.status).withOpacity(0.12),
            padding: EdgeInsets.zero,
          ),
        ]),
        if (client.phone != null || client.email != null) ...[
          const Divider(height: 20),
          if (client.phone != null) Row(children: [const Icon(Icons.phone, size: 16), const SizedBox(width: 8), Text(client.phone!)]),
          if (client.email != null) Row(children: [const Icon(Icons.email, size: 16), const SizedBox(width: 8), Expanded(child: Text(client.email!, overflow: TextOverflow.ellipsis))]),
        ],
        if (client.address != null) ...[
          const SizedBox(height: 4),
          Row(children: [const Icon(Icons.home, size: 16), const SizedBox(width: 8), Expanded(child: Text(client.address!, overflow: TextOverflow.ellipsis))]),
        ],
        const SizedBox(height: 16),
        Row(children: [
          Expanded(child: OutlinedButton.icon(
            onPressed: () async {
              Navigator.pop(context);
              await Navigator.push(context, MaterialPageRoute(builder: (_) => ClientDetailScreen(client: client)));
            },
            icon: const Icon(Icons.info_outline), label: const Text('Ver ficha'),
          )),
          const SizedBox(width: 8),
          Expanded(child: FilledButton.icon(
            onPressed: () async {
              Navigator.pop(context);
              await Navigator.push(context, MaterialPageRoute(builder: (_) => ClientFormScreen(existing: client)));
            },
            icon: const Icon(Icons.edit), label: const Text('Editar'),
          )),
        ]),
        const SizedBox(height: 8),
      ]),
    );
  }

  Color _statusColor(String s) {
    switch (s) {
      case 'active': return Colors.green;
      case 'inactive': return Colors.grey;
      case 'prospect': return Colors.orange;
      case 'lead': return Colors.blue;
      default: return Colors.grey;
    }
  }
}
