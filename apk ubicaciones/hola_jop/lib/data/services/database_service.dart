import 'package:hive_flutter/hive_flutter.dart';

class DatabaseService {
  static const String clientsBox = 'clients';
  static const String categoriesBox = 'categories';

  static Future<void> init() async {
    await Hive.initFlutter();
    await Hive.openBox<Map>(clientsBox);
    await Hive.openBox<Map>(categoriesBox);
  }

  static Box<Map> get clients => Hive.box<Map>(clientsBox);
  static Box<Map> get categories => Hive.box<Map>(categoriesBox);
}
