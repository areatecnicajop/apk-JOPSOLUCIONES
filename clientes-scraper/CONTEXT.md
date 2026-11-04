# JOP Scraper — Contexto

## Qué es

Scraper de clientes potenciales para el equipo comercial de JOP Soluciones.
Extrae datos de empresas desde **amarillas.cl** y los exporta a Excel y SQLite.

## Para qué está diseñado

- Encontrar empresas en rubros objetivo en la Región Metropolitana, Chile
- Extraer nombre, teléfono, dirección y categoría de cada empresa
- Generar un Excel listo para que el equipo comercial contacte prospectos
- Mantener una base de datos SQLite para consultas y deduplicación

## Stack técnico

- **Python 3**
- **requests** — llamadas HTTP a la API de amarillas.cl
- **sqlite3** — base de datos local de resultados
- **openpyxl** — generación del Excel de salida

## Fuente de datos

**API de amarillas.cl**: `GET /api/advertisements?q={rubro}&size=20&page={n}`
- Paginación automática hasta agotar resultados
- Rate limiting con `time.sleep` aleatorio entre requests (evita bloqueos)
- Sin autenticación requerida — API pública

## Rubros que scrapea (15)

Pintura automotriz · mueblería · funeraria · barraca · mecánico · ferretería ·
taller de pintura · desabolladura · taller automotriz · carpintería · ebanistería ·
vulcanización · tapicería automotriz · hojalatería · sandblasting

Todos orientados a negocios que usan compresores de aire — segmento objetivo de JOP.

## Output

| Archivo | Formato | Uso |
|---------|---------|-----|
| `clientes.db` | SQLite | Consultas, deduplicación, historial |
| `clientes.xlsx` | Excel | Entregable para el equipo comercial |

## Archivos principales

| Archivo | Qué hace |
|---------|---------|
| `scraper.py` | Scraper principal — itera rubros y páginas |
| `scraper_multi.py` | Versión alternativa multi-rubro |
| `export_excel.py` | Exporta `clientes.db` → `clientes.xlsx` |
| `test_*.py` | Tests de diagnóstico de la API y estructura |

## Seguridad / consideraciones

- No guarda credenciales — la API de amarillas.cl es pública
- El encoding UTF-8 está forzado desde el inicio del script (compatibilidad Windows)
- Los delays son obligatorios — sin ellos amarillas.cl bloquea la IP

## Cómo ejecutar

```bash
# Scraping completo
python scraper.py

# Solo exportar Excel desde la BD existente
python export_excel.py

# Tests
python -m pytest test_*.py -v
```
