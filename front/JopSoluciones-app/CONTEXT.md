# JOP Frontend — Contexto de la Aplicación

## Qué es

Panel de administración web para el equipo interno de JOP Soluciones.
Permite gestionar clientes, prospectos, pedidos y controlar la sincronización
de productos entre Laudus ERP y WooCommerce.

## Para qué está diseñado

- Ver y editar clientes y prospectos del sistema
- Visualizar prospectos en mapa interactivo
- Monitorear y ejecutar sincronizaciones Laudus → WooCommerce
- Consultar pedidos registrados
- Ver productos del catálogo

## Stack técnico

- **Angular 19** / TypeScript
- **Angular Material** — sistema de UI (único, no mezclar con otras libs)
- **Puerto local**: 4200
- **Backend**: `http://localhost:8080` (dev) / `https://apkjop.jopsoluciones.cl/api` (prod)
- **SSR**: configurado con `@angular/ssr` pero usado principalmente como SPA

## Seguridad

- `authGuard` protege todas las rutas excepto `/login`
- El token se almacena en el cliente y se envía en headers HTTP
- Sin sesión en servidor — stateless

## Estructura de rutas

| Ruta | Componente | Qué muestra |
|------|-----------|-------------|
| `/` | `HomePageComponent` | Panel principal (protegida) |
| `/login` | `LoginComponent` | Login |

## Estructura de módulos

```
sincronizacion/
├── pages/
│   ├── home-page/      ← panel principal con tabs
│   ├── clientes/       ← listado y edición de clientes
│   ├── prospectos/     ← listado + mapa de prospectos
│   ├── mapa/           ← mapa standalone
│   ├── productos/      ← catálogo de productos Laudus
│   └── categorias/     ← categorías de clientes
├── components/
│   ├── button-action/  ← botón de acción de sincronización
│   └── console-actions/← consola de logs de sincronización
├── interfaces/         ← modelos TypeScript
└── services/           ← llamadas al backend
    ├── api.service.ts
    ├── cliente.service.ts
    ├── prospecto.service.ts
    ├── pedido.service.ts
    └── categoria-cliente.service.ts
```

## Fuente de datos

Todo viene del backend JOP (`back/sincronizador`) via HTTP REST.
Los servicios en `sincronizacion/services/` encapsulan todas las llamadas — los componentes no llaman `HttpClient` directamente.

## Reglas de código

- Nunca `any` en TypeScript — siempre interfaces en `sincronizacion/interfaces/`
- Nunca `HttpClient` directo en componentes — solo a través de servicios
- Angular Material para UI — no agregar otras librerías de componentes
- URLs del backend siempre desde `environment.baseUrl`

## Comandos

```bash
# Desarrollo
npm start

# Build producción
ng build --configuration=production

# Tests
ng test --watch=false
```
