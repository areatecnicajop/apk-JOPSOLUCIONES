import 'package:flutter/material.dart';
import 'map/map_screen.dart';
import 'clients/clients_list_screen.dart';
import 'categories/categories_screen.dart';
import 'rutas/rutas_screen.dart';
import 'orders/all_orders_screen.dart';
import 'settings/settings_screen.dart';

class HomeShell extends StatefulWidget {
  const HomeShell({super.key});
  @override
  State<HomeShell> createState() => _HomeShellState();
}

class _HomeShellState extends State<HomeShell> {
  int _index = 0;

  final _screens = const [
    MapScreen(),
    ClientsListScreen(),
    CategoriesScreen(),
    RutasScreen(),
    AllOrdersScreen(),
    SettingsScreen(),
  ];

  @override
  Widget build(BuildContext context) {
    final isDesktop = MediaQuery.of(context).size.width > 720;

    if (isDesktop) {
      return Scaffold(
        body: Row(children: [
          NavigationRail(
            selectedIndex: _index,
            onDestinationSelected: (i) => setState(() => _index = i),
            labelType: NavigationRailLabelType.all,
            leading: Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: Column(children: [
                Icon(Icons.location_on, color: Theme.of(context).colorScheme.primary, size: 28),
                const SizedBox(height: 4),
                Text('JOP', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: Theme.of(context).colorScheme.primary)),
              ]),
            ),
            destinations: const [
              NavigationRailDestination(icon: Icon(Icons.map_outlined), selectedIcon: Icon(Icons.map), label: Text('Mapa')),
              NavigationRailDestination(icon: Icon(Icons.people_outline), selectedIcon: Icon(Icons.people), label: Text('Clientes')),
              NavigationRailDestination(icon: Icon(Icons.category_outlined), selectedIcon: Icon(Icons.category), label: Text('Categorías')),
              NavigationRailDestination(icon: Icon(Icons.route_outlined), selectedIcon: Icon(Icons.route), label: Text('Rutas')),
              NavigationRailDestination(icon: Icon(Icons.receipt_long_outlined), selectedIcon: Icon(Icons.receipt_long), label: Text('Pedidos')),
              NavigationRailDestination(icon: Icon(Icons.settings_outlined), selectedIcon: Icon(Icons.settings), label: Text('Config')),
            ],
          ),
          const VerticalDivider(width: 1),
          Expanded(child: _screens[_index]),
        ]),
      );
    }

    return Scaffold(
      body: _screens[_index],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _index,
        onDestinationSelected: (i) => setState(() => _index = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.map_outlined), selectedIcon: Icon(Icons.map), label: 'Mapa'),
          NavigationDestination(icon: Icon(Icons.people_outline), selectedIcon: Icon(Icons.people), label: 'Clientes'),
          NavigationDestination(icon: Icon(Icons.category_outlined), selectedIcon: Icon(Icons.category), label: 'Categorías'),
          NavigationDestination(icon: Icon(Icons.route_outlined), selectedIcon: Icon(Icons.route), label: 'Rutas'),
          NavigationDestination(icon: Icon(Icons.receipt_long_outlined), selectedIcon: Icon(Icons.receipt_long), label: 'Pedidos'),
          NavigationDestination(icon: Icon(Icons.settings_outlined), selectedIcon: Icon(Icons.settings), label: 'Config'),
        ],
      ),
    );
  }
}
