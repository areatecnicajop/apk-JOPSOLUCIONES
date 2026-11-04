# JOP APK — Contexto de la Aplicación

## Qué es

App móvil para vendedores de JOP Soluciones.
Permite gestionar clientes y prospectos con visualización en mapa interactivo,
registrar ubicaciones GPS y contactar directamente por llamada o WhatsApp.

## Para qué está diseñado

- Ver clientes y prospectos en un mapa (OpenStreetMap)
- Registrar y actualizar ubicaciones GPS de clientes
- Gestionar prospectos (crear, editar, filtrar)
- Contactar clientes por teléfono o WhatsApp directo desde la app
- Funcionar con datos locales (Hive) cuando no hay conexión

## Stack técnico

- **Flutter** / Dart SDK ^3.12.1
- **Riverpod** — gestión de estado
- **Hive** — persistencia local (funciona offline)
- **flutter_map** + OpenStreetMap — mapa interactivo
- **geolocator** — GPS del dispositivo
- **http** — llamadas al backend
- **url_launcher** — abrir llamadas, WhatsApp, Google Maps
- **Backend**: `https://apkjop.jopsoluciones.cl/api`

## Seguridad

- Comunicación con backend via HTTPS
- Sin autenticación propia implementada aún — acceso directo a la API
- Datos locales en Hive sin encriptación

## Estructura del proyecto

```
lib/
├── data/
│   ├── repositories/
│   │   └── client_repository_impl.dart   ← CRUD clientes contra backend
│   └── services/
│       └── prospecto_service.dart         ← gestión de prospectos
└── presentation/
    └── screens/
        └── prospectos/
            └── prospectos_screen.dart     ← pantalla principal de prospectos
```

## Fuente de datos

- **Backend JOP** (`https://apkjop.jopsoluciones.cl/api`) — clientes y prospectos
- **Hive local** — caché offline de datos
- **GPS del dispositivo** — ubicación actual del vendedor

## Bug conocido activo

`client_repository_impl.dart` y `prospecto_service.dart` construyen URLs incorrectas:
- `_baseUrl` = `https://apkjop.jopsoluciones.cl/api`
- Luego llaman `$_baseUrl/api/clientes` → resulta `/api/api/clientes` (incorrecto)
- Correcto: `$_baseUrl/clientes`

## Comandos

```bash
# Correr en dispositivo/emulador
flutter run

# Análisis estático
flutter analyze

# Tests
flutter test

# Build APK release
flutter build apk --release
```
