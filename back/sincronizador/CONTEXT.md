# JOP Sincronizador — Contexto del Backend

## Qué es

API REST que actúa como puente entre el ERP **Laudus** y tiendas **WooCommerce**.
Sincroniza productos, stock, categorías e imágenes desde Laudus hacia WordPress/WooCommerce.
También gestiona clientes, prospectos y pedidos del sistema comercial de JOP Soluciones.

## Para qué está diseñado

- Automatizar la sincronización de catálogo de productos Laudus → WooCommerce
- Gestionar clientes y prospectos captados por el scraper
- Registrar y consultar pedidos
- Proveer datos en tiempo real al frontend y a la APK via WebSockets

## Stack técnico

- **Spring Boot 3.5.3** / Java
- **MySQL** — base de datos `jop_soluciones`, puerto 3306
- **Puerto**: 8080 (local) / `https://apkjop.jopsoluciones.cl` (producción)
- **Lombok** — anotaciones en entidades
- **Gson** — serialización JSON para llamadas externas
- **WebSockets** — notificaciones en tiempo real al frontend
- **Cache** — cache en memoria para `catalogProducts`
- **Hikari** — pool de conexiones (max 10)

## Fuentes de datos

| Fuente | Qué provee | Cómo se conecta |
|--------|-----------|-----------------|
| Laudus ERP | Productos, stock, categorías, órdenes | API REST con token (`TokenLaudus`) |
| WooCommerce | Tienda online destino | API REST con credenciales en `ConfigWordPress` |
| MySQL local | Clientes, prospectos, pedidos, config | JPA/Hibernate directo |

## Seguridad

- Autenticación con Laudus vía `TokenLaudus` — token temporal almacenado en BD
- Credenciales WooCommerce guardadas en `ConfigWordPress` (consumer key + secret)
- CORS configurado en `CorsConfig.java` — permite llamadas desde el frontend Angular
- Sin autenticación propia aún — los endpoints son accesibles directamente (pendiente de implementar)

## Flujo principal de sincronización

```
1. Frontend solicita sincronización
2. Back obtiene token Laudus (si expiró, renueva)
3. Consulta catálogo de productos a Laudus API
4. Para cada producto: busca en WooCommerce por SKU
   - Si existe → actualiza precio, stock, imágenes
   - Si no existe → crea el producto en WooCommerce
5. Registra cambios en RegisterChange / CategorieRegisterChange
6. Notifica al frontend via WebSocket
```

## Entidades principales

| Entidad | Propósito |
|---------|-----------|
| `Cliente` | Clientes del sistema comercial |
| `Prospecto` | Leads captados por el scraper de amarillas.cl |
| `ConfigWordPress` | URL + credenciales de cada tienda WooCommerce |
| `TokenLaudus` | Token de acceso al ERP Laudus |
| `LaudusProduct` / `LaudusStock` | Productos y stock cacheados del ERP |
| `Pedido` | Pedidos sincronizados desde Laudus |
| `RegisterChange` | Log de cambios sincronizados |
| `CategoriaCliente` | Categorías para clasificar clientes |

## Endpoints principales

| Método | Ruta | Qué hace |
|--------|------|----------|
| GET/POST/PUT/DELETE | `/api/clientes` | CRUD clientes |
| GET/POST/PUT/DELETE | `/api/prospectos` | CRUD prospectos |
| GET | `/api/pedidos` | Consulta pedidos |
| GET | `/api/categorias-cliente` | Categorías de cliente |
| GET | `/` (HomeController) | Endpoints de sincronización principal |

## Scheduler

`StockSyncScheduler` — sincronización automática de stock en segundo plano (Spring `@Scheduled`).

## Comandos

```bash
# Arrancar
./mvnw spring-boot:run

# Tests
./mvnw test

# Build JAR producción
./mvnw clean package -DskipTests
```
