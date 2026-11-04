import '../entities/category.dart';

abstract class CategoryRepository {
  Future<List<Category>> getAll();
  Future<Category?> getById(String uuid);
  Future<void> save(Category category);
  Future<void> delete(String uuid);
}
