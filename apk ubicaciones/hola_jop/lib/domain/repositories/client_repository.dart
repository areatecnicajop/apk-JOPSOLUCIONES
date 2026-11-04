import '../entities/client.dart';

abstract class ClientRepository {
  Future<List<Client>> getAll();
  Future<Client?> getById(String uuid);
  Future<void> save(Client client);
  Future<void> delete(String uuid);
  Future<List<Client>> search(String query);
  Future<List<Client>> filterByCategory(String categoryId);
  Future<List<Client>> filterByStatus(String status);
}
