import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/constants/app_constants.dart';
import '../../../core/utils/icon_utils.dart';
import '../../../domain/entities/category.dart';
import '../../providers/providers.dart';

class CategoriesScreen extends ConsumerWidget {
  const CategoriesScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cats = ref.watch(categoriesProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Categorías')),
      body: cats.when(
        data: (list) => list.isEmpty
          ? const Center(child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
              Icon(Icons.category_outlined, size: 64, color: Colors.grey),
              SizedBox(height: 12),
              Text('Sin categorías', style: TextStyle(color: Colors.grey)),
            ]))
          : ListView.separated(
              padding: const EdgeInsets.all(12),
              itemCount: list.length,
              separatorBuilder: (_, __) => const SizedBox(height: 4),
              itemBuilder: (ctx, i) => _CategoryTile(category: list[i]),
            ),
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('Error: $e')),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _showCategoryForm(context, ref, null),
        icon: const Icon(Icons.add),
        label: const Text('Nueva categoría'),
      ),
    );
  }

  static Future<void> _showCategoryForm(BuildContext context, WidgetRef ref, Category? existing) async {
    await showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (_) => ProviderScope(parent: ProviderScope.containerOf(context), child: _CategoryForm(existing: existing)),
    );
  }
}

class _CategoryTile extends ConsumerWidget {
  final Category category;
  const _CategoryTile({required this.category});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Card(
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: category.color,
          child: Icon(IconUtils.get(category.iconName), color: Colors.white, size: 20),
        ),
        title: Text(category.name, style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: category.description != null ? Text(category.description!) : null,
        trailing: Row(mainAxisSize: MainAxisSize.min, children: [
          IconButton(icon: const Icon(Icons.edit_outlined), onPressed: () => _edit(context, ref)),
          IconButton(icon: const Icon(Icons.delete_outline, color: Colors.red), onPressed: () => _delete(context, ref)),
        ]),
      ),
    );
  }

  void _edit(BuildContext ctx, WidgetRef ref) {
    showModalBottomSheet(
      context: ctx, isScrollControlled: true,
      builder: (_) => ProviderScope(parent: ProviderScope.containerOf(ctx), child: _CategoryForm(existing: category)),
    );
  }

  Future<void> _delete(BuildContext ctx, WidgetRef ref) async {
    final confirm = await showDialog<bool>(
      context: ctx,
      builder: (_) => AlertDialog(
        title: const Text('Eliminar categoría'),
        content: Text('¿Eliminar "${category.name}"?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), style: FilledButton.styleFrom(backgroundColor: Colors.red), child: const Text('Eliminar')),
        ],
      ),
    );
    if (confirm == true) await ref.read(categoriesProvider.notifier).delete(category.uuid);
  }
}

class _CategoryForm extends ConsumerStatefulWidget {
  final Category? existing;
  const _CategoryForm({this.existing});
  @override
  ConsumerState<_CategoryForm> createState() => _CategoryFormState();
}

class _CategoryFormState extends ConsumerState<_CategoryForm> {
  final _formKey = GlobalKey<FormState>();
  late final TextEditingController _name, _desc;
  late String _colorHex, _iconName;

  @override
  void initState() {
    super.initState();
    _name = TextEditingController(text: widget.existing?.name ?? '');
    _desc = TextEditingController(text: widget.existing?.description ?? '');
    _colorHex = widget.existing?.colorHex ?? AppConstants.defaultCategoryColors.first['hex']!;
    _iconName = widget.existing?.iconName ?? 'location_on';
  }

  @override
  void dispose() { _name.dispose(); _desc.dispose(); super.dispose(); }

  Future<void> _save() async {
    if (!_formKey.currentState!.validate()) return;
    final notifier = ref.read(categoriesProvider.notifier);
    final cat = widget.existing != null
      ? widget.existing!.copyWith(name: _name.text.trim(), colorHex: _colorHex, iconName: _iconName, description: _desc.text.trim().isEmpty ? null : _desc.text.trim())
      : notifier.createNew(name: _name.text.trim(), colorHex: _colorHex, iconName: _iconName, description: _desc.text.trim().isEmpty ? null : _desc.text.trim());
    await notifier.save(cat);
    if (mounted) Navigator.pop(context);
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom, left: 16, right: 16, top: 24),
      child: Form(
        key: _formKey,
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(widget.existing != null ? 'Editar categoría' : 'Nueva categoría', style: Theme.of(context).textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          TextFormField(
            controller: _name, decoration: const InputDecoration(labelText: 'Nombre *'),
            validator: (v) => (v == null || v.trim().isEmpty) ? 'Requerido' : null,
          ),
          const SizedBox(height: 12),
          TextFormField(controller: _desc, decoration: const InputDecoration(labelText: 'Descripción (opcional)')),
          const SizedBox(height: 16),
          Text('Color', style: Theme.of(context).textTheme.labelLarge),
          const SizedBox(height: 8),
          Wrap(spacing: 8, children: AppConstants.defaultCategoryColors.map((c) {
            final hex = c['hex']!;
            final color = Color(int.parse('FF$hex', radix: 16));
            return GestureDetector(
              onTap: () => setState(() => _colorHex = hex),
              child: CircleAvatar(
                backgroundColor: color, radius: 18,
                child: _colorHex == hex ? const Icon(Icons.check, color: Colors.white, size: 18) : null,
              ),
            );
          }).toList()),
          const SizedBox(height: 16),
          Text('Ícono', style: Theme.of(context).textTheme.labelLarge),
          const SizedBox(height: 8),
          Wrap(spacing: 8, children: AppConstants.categoryIcons.map((name) {
            final selected = _iconName == name;
            return GestureDetector(
              onTap: () => setState(() => _iconName = name),
              child: Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  border: Border.all(color: selected ? Theme.of(context).colorScheme.primary : Colors.grey.shade300, width: 2),
                  borderRadius: BorderRadius.circular(8),
                  color: selected ? Theme.of(context).colorScheme.primary.withOpacity(0.1) : null,
                ),
                child: Icon(IconUtils.get(name), size: 24, color: selected ? Theme.of(context).colorScheme.primary : null),
              ),
            );
          }).toList()),
          const SizedBox(height: 24),
          SizedBox(width: double.infinity, child: FilledButton.icon(onPressed: _save, icon: const Icon(Icons.save), label: Text(widget.existing != null ? 'Guardar cambios' : 'Crear categoría'))),
          const SizedBox(height: 16),
        ]),
      ),
    );
  }
}
