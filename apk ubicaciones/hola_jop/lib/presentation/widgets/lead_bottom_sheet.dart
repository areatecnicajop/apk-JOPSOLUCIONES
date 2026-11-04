import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:uuid/uuid.dart';
import '../../domain/entities/client.dart';
import '../../domain/entities/prospecto.dart';
import '../providers/providers.dart';
import '../screens/clients/client_detail_screen.dart';

// Bottom sheet de detalle de un lead/prospecto. Reutilizado desde la lista de
// Clientes (leads) y desde la navegación de rutas, para que "manipular" un
// prospecto se vea y se sienta igual sin importar desde dónde se abrió.
Future<void> mostrarLeadBottomSheet(
  BuildContext context, {
  required Prospecto prospecto,
  required Color color,
  bool mostrarRegistrarVisita = false,
  Future<void> Function(String nota)? onRegistrarVisita,
  VoidCallback? onConvertido,
}) {
  return showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    builder: (_) => LeadBottomSheet(
      prospecto: prospecto,
      color: color,
      mostrarRegistrarVisita: mostrarRegistrarVisita,
      onRegistrarVisita: onRegistrarVisita,
      onConvertido: onConvertido,
    ),
  );
}

Future<void> convertirProspectoACliente(BuildContext context, WidgetRef ref, Prospecto p, {VoidCallback? onConvertido}) async {
  final confirm = await showDialog<bool>(
    context: context,
    builder: (_) => AlertDialog(
      title: const Text('Convertir a Cliente'),
      content: Text('¿Convertir "${p.nombre}" a cliente activo?'),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancelar')),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Convertir')),
      ],
    ),
  );
  if (confirm != true || !context.mounted) return;

  final newClient = Client(
    uuid: const Uuid().v4(),
    name: p.nombre,
    phone: p.telefono,
    email: p.email,
    address: p.direccionCompleta.isNotEmpty ? p.direccionCompleta : null,
    latitude: p.latitud ?? -33.45,
    longitude: p.longitud ?? -70.67,
    status: 'active',
    notes: 'Convertido desde lead (rubro: ${p.rubro})',
    categoryId: null,
    categoryName: p.rubro,
    extraFields: {},
    createdAt: DateTime.now(),
    updatedAt: DateTime.now(),
  );

  try {
    await ref.read(clientsProvider.notifier).save(newClient);
    onConvertido?.call();
    if (!context.mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('${p.nombre} convertido a cliente')));
    await Navigator.push(context, MaterialPageRoute(builder: (_) => ClientDetailScreen(client: newClient)));
  } catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text('Error: $e'), backgroundColor: Colors.red));
    }
  }
}

class LeadBottomSheet extends ConsumerStatefulWidget {
  final Prospecto prospecto;
  final Color color;
  final bool mostrarRegistrarVisita;
  final Future<void> Function(String nota)? onRegistrarVisita;
  final VoidCallback? onConvertido;

  const LeadBottomSheet({
    super.key,
    required this.prospecto,
    required this.color,
    this.mostrarRegistrarVisita = false,
    this.onRegistrarVisita,
    this.onConvertido,
  });

  @override
  ConsumerState<LeadBottomSheet> createState() => _LeadBottomSheetState();
}

class _LeadBottomSheetState extends ConsumerState<LeadBottomSheet> {
  final _notaCtrl = TextEditingController();
  bool _registrando = false;

  @override
  void dispose() {
    _notaCtrl.dispose();
    super.dispose();
  }

  Future<void> _registrarVisita() async {
    setState(() => _registrando = true);
    try {
      await widget.onRegistrarVisita!(_notaCtrl.text.trim());
      if (mounted) Navigator.pop(context);
    } finally {
      if (mounted) setState(() => _registrando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = widget.prospecto;
    final color = widget.color;
    return Container(
      padding: EdgeInsets.fromLTRB(20, 20, 20, MediaQuery.of(context).viewInsets.bottom + 20),
      child: SingleChildScrollView(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Center(child: Container(width: 40, height: 4,
              decoration: BoxDecoration(color: Colors.grey.shade300, borderRadius: BorderRadius.circular(2)))),
          const SizedBox(height: 16),
          Row(children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(20)),
              child: Text(p.rubro, style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12)),
            ),
          ]),
          const SizedBox(height: 8),
          Text(p.nombre, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          if (p.direccionCompleta.isNotEmpty)
            Padding(padding: const EdgeInsets.only(top: 4),
              child: Row(children: [
                Icon(Icons.location_on, size: 14, color: Colors.grey.shade500),
                const SizedBox(width: 4),
                Expanded(child: Text(p.direccionCompleta, style: TextStyle(color: Colors.grey.shade700, fontSize: 13))),
              ])),
          const SizedBox(height: 16),
          const Divider(),
          const SizedBox(height: 8),
          SizedBox(width: double.infinity,
            child: FilledButton.icon(
              style: FilledButton.styleFrom(backgroundColor: Colors.green),
              onPressed: () {
                Navigator.pop(context);
                convertirProspectoACliente(context, ref, p, onConvertido: widget.onConvertido);
              },
              icon: const Icon(Icons.star),
              label: const Text('Convertir a Cliente'),
            )),
          const SizedBox(height: 8),
          Wrap(spacing: 10, runSpacing: 10, children: [
            if (p.telefono != null) ...[
              ActionBtn(icon: Icons.phone, label: 'Llamar', color: Colors.green,
                onTap: () => launchUrl(Uri.parse('tel:${p.telefono}'))),
              ActionBtn(icon: Icons.chat, label: 'WhatsApp', color: const Color(0xFF25D366),
                onTap: () {
                  final num = p.telefono!.replaceAll(RegExp(r'[^\d]'), '');
                  launchUrl(Uri.parse('https://wa.me/$num'), mode: LaunchMode.externalApplication);
                }),
            ],
            if (p.email != null)
              ActionBtn(icon: Icons.email, label: 'Email', color: Colors.orange,
                onTap: () => launchUrl(Uri.parse('mailto:${p.email}'))),
            ActionBtn(icon: Icons.map, label: 'Ver en Maps', color: Colors.blue,
              onTap: () => launchUrl(Uri.parse(p.googleMapsUrl), mode: LaunchMode.externalApplication)),
          ]),
          if (widget.mostrarRegistrarVisita) ...[
            const SizedBox(height: 16),
            const Divider(),
            const SizedBox(height: 8),
            const Text('NOTA DE LA VISITA (opcional)', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: Colors.grey, letterSpacing: 0.5)),
            const SizedBox(height: 8),
            TextField(
              controller: _notaCtrl,
              maxLines: 3,
              decoration: const InputDecoration(
                hintText: 'Ej: no había nadie, dejó tarjeta, interesado en...',
                border: OutlineInputBorder(), isDense: true,
              ),
            ),
            const SizedBox(height: 10),
            SizedBox(width: double.infinity,
              child: FilledButton.icon(
                onPressed: _registrando ? null : _registrarVisita,
                icon: const Icon(Icons.check_circle),
                label: Text(_registrando ? 'Registrando...' : 'Registrar visita'),
              )),
          ],
        ]),
      ),
    );
  }
}

class ActionBtn extends StatelessWidget {
  final IconData icon;
  final String label;
  final Color color;
  final VoidCallback onTap;
  const ActionBtn({super.key, required this.icon, required this.label, required this.color, required this.onTap});

  @override
  Widget build(BuildContext context) => InkWell(
    onTap: onTap, borderRadius: BorderRadius.circular(12),
    child: Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      decoration: BoxDecoration(color: color.withOpacity(0.1),
          border: Border.all(color: color.withOpacity(0.4)),
          borderRadius: BorderRadius.circular(12)),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(icon, size: 18, color: color),
        const SizedBox(width: 6),
        Text(label, style: TextStyle(color: color, fontWeight: FontWeight.w600, fontSize: 13)),
      ]),
    ),
  );
}
