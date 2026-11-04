import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:latlong2/latlong.dart';
import '../../data/repositories/client_repository_impl.dart';
import '../../data/repositories/category_repository_impl.dart';
import '../../data/services/location_service.dart';
import '../../domain/entities/client.dart';
import '../../domain/entities/category.dart';

// ─── Repositorios ───────────────────────────────────────────────────────────

final clientRepoProvider = Provider((_) => ClientRepositoryImpl());
final categoryRepoProvider = Provider((_) => CategoryRepositoryImpl());

// ─── Categorías ─────────────────────────────────────────────────────────────

final categoriesProvider = StateNotifierProvider<CategoriesNotifier, AsyncValue<List<Category>>>(
  (ref) => CategoriesNotifier(ref.read(categoryRepoProvider)),
);

class CategoriesNotifier extends StateNotifier<AsyncValue<List<Category>>> {
  final CategoryRepositoryImpl _repo;
  CategoriesNotifier(this._repo) : super(const AsyncLoading()) { load(); }

  Future<void> load() async {
    state = const AsyncLoading();
    try {
      state = AsyncData(await _repo.getAll());
    } catch (e, s) {
      state = AsyncError(e, s);
    }
  }

  Future<void> save(Category cat) async {
    await _repo.save(cat);
    await load();
  }

  Future<void> delete(String uuid) async {
    await _repo.delete(uuid);
    await load();
  }

  Category createNew({required String name, required String colorHex, String iconName = 'location_on', String? description}) =>
    _repo.createNew(name: name, colorHex: colorHex, iconName: iconName, description: description);
}

// ─── Clientes ───────────────────────────────────────────────────────────────

final clientsProvider = StateNotifierProvider<ClientsNotifier, AsyncValue<List<Client>>>(
  (ref) => ClientsNotifier(ref.read(clientRepoProvider)),
);

class ClientsNotifier extends StateNotifier<AsyncValue<List<Client>>> {
  final ClientRepositoryImpl _repo;
  ClientsNotifier(this._repo) : super(const AsyncLoading()) { load(); }

  Future<void> load() async {
    state = const AsyncLoading();
    try {
      state = AsyncData(await _repo.getAll());
    } catch (e, s) {
      state = AsyncError(e, s);
    }
  }

  Future<void> save(Client client) async {
    await _repo.save(client);
    await load();
  }

  Future<void> delete(String uuid) async {
    await _repo.delete(uuid);
    await load();
  }

  Client createNew({
    required String name, required double lat, required double lng,
    String? phone, String? email, String? address,
    String? categoryId, String? categoryName,
    String status = 'active', String? notes,
    Map<String, String> extraFields = const {},
  }) => _repo.createNew(
    name: name, lat: lat, lng: lng,
    phone: phone, email: email, address: address,
    categoryId: categoryId, categoryName: categoryName,
    status: status, notes: notes, extraFields: extraFields,
  );
}

// ─── Filtros ─────────────────────────────────────────────────────────────────

class ClientFilter {
  final String searchQuery;
  final String? categoryId;
  final String? status;
  const ClientFilter({this.searchQuery = '', this.categoryId, this.status});

  ClientFilter copyWith({String? searchQuery, String? categoryId, String? status, bool clearCategory = false, bool clearStatus = false}) =>
    ClientFilter(
      searchQuery: searchQuery ?? this.searchQuery,
      categoryId: clearCategory ? null : (categoryId ?? this.categoryId),
      status: clearStatus ? null : (status ?? this.status),
    );

  bool get isActive => searchQuery.isNotEmpty || categoryId != null || status != null;
}

final clientFilterProvider = StateNotifierProvider<FilterNotifier, ClientFilter>(
  (_) => FilterNotifier(),
);

class FilterNotifier extends StateNotifier<ClientFilter> {
  FilterNotifier() : super(const ClientFilter());
  void update(ClientFilter f) => state = f;
  void reset() => state = const ClientFilter();
}

final filteredClientsProvider = Provider<AsyncValue<List<Client>>>((ref) {
  final all = ref.watch(clientsProvider);
  final filter = ref.watch(clientFilterProvider);
  return all.whenData((clients) {
    var result = clients;
    if (filter.searchQuery.isNotEmpty) {
      final q = filter.searchQuery.toLowerCase();
      result = result.where((c) =>
        c.name.toLowerCase().contains(q) ||
        (c.phone?.toLowerCase().contains(q) ?? false) ||
        (c.email?.toLowerCase().contains(q) ?? false) ||
        (c.categoryName?.toLowerCase().contains(q) ?? false),
      ).toList();
    }
    if (filter.categoryId != null) {
      result = result.where((c) => c.categoryId == filter.categoryId).toList();
    }
    if (filter.status != null) {
      result = result.where((c) => c.status == filter.status).toList();
    }
    return result;
  });
});

// ─── Mapa ────────────────────────────────────────────────────────────────────

final mapCenterProvider = StateProvider<LatLng>((_) => const LatLng(-33.42719, -70.70964)); // JOP Soluciones
final mapZoomProvider = StateProvider<double>((_) => 16.0);
final selectedClientProvider = StateProvider<Client?>((_) => null);
final userLocationProvider = StateProvider<LatLng?>((_) => null);

final locationProvider = FutureProvider<LatLng?>((ref) async {
  return LocationService.getCurrentLocation();
});

// ─── Tema ────────────────────────────────────────────────────────────────────

final themeModeProvider = StateProvider((_) => false); // false = light
