"""
Scraper de clientes potenciales - Región Metropolitana, Chile
API: amarillas.cl /api/advertisements  (size=20, paginado)
Rubros: pintura automotriz, mueblería, funeraria, barraca, mecánico, ferretería
Output: clientes.xlsx + clientes.db (SQLite)
"""

import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

import requests
import sqlite3
import time
import random
import os
import json
from datetime import datetime
from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment
from openpyxl.utils import get_column_letter

# ─── Configuración ────────────────────────────────────────────────────────────

RUBROS = [
    {"nombre": "Pintura Automotriz",   "query": "pintura automotriz"},
    {"nombre": "Mueblería",            "query": "muebleria"},
    {"nombre": "Funeraria",            "query": "funeraria"},
    {"nombre": "Barraca",              "query": "barraca"},
    {"nombre": "Mecánico",             "query": "mecanico"},
    {"nombre": "Ferretería",           "query": "ferreteria"},
    # Nuevos rubros - usan compresor de aire
    {"nombre": "Taller de Pintura",    "query": "taller de pintura"},
    {"nombre": "Desabolladura",        "query": "desabolladura"},
    {"nombre": "Taller Automotriz",    "query": "taller automotriz"},
    {"nombre": "Carpintería",          "query": "carpinteria"},
    {"nombre": "Ebanistería",          "query": "ebanisteria"},
    {"nombre": "Vulcanización",        "query": "vulcanizacion"},
    {"nombre": "Tapicería Automotriz", "query": "tapiceria automotriz"},
    {"nombre": "Hojalatería",          "query": "hojalateria"},
    {"nombre": "Sandblasting",         "query": "sandblasting"},
]

STATE       = "region-metropolitana"
API_URL     = "https://www.amarillas.cl/api/advertisements"
PAGE_SIZE   = 20
OUTPUT_DIR  = os.path.dirname(os.path.abspath(__file__))
DB_PATH     = os.path.join(OUTPUT_DIR, "clientes.db")
EXCEL_PATH  = os.path.join(OUTPUT_DIR, "clientes.xlsx")

HEADERS = {
    "User-Agent":      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36",
    "Accept":          "application/json, */*",
    "Accept-Language": "es-CL,es;q=0.9",
    "Referer":         "https://www.amarillas.cl/",
}

DELAY_MIN = 0.5
DELAY_MAX = 1.5

# ─── Base de datos ─────────────────────────────────────────────────────────────

def init_db():
    conn = sqlite3.connect(DB_PATH)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS clientes (
            id        INTEGER PRIMARY KEY AUTOINCREMENT,
            nombre    TEXT,
            rubro     TEXT,
            telefono  TEXT,
            email     TEXT,
            direccion TEXT,
            comuna    TEXT,
            region    TEXT DEFAULT 'Región Metropolitana',
            latitud   REAL,
            longitud  REAL,
            url       TEXT,
            fuente    TEXT DEFAULT 'amarillas.cl',
            fecha     TEXT,
            UNIQUE(nombre, comuna)
        )
    """)
    conn.commit()
    return conn

def guardar(conn, d):
    try:
        conn.execute("""
            INSERT OR IGNORE INTO clientes
            (nombre, rubro, telefono, email, direccion, comuna, latitud, longitud, url, fecha)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (d["nombre"], d["rubro"], d.get("telefono"), d.get("email"),
              d.get("direccion"), d.get("comuna"),
              d.get("latitud"), d.get("longitud"),
              d.get("url"), datetime.now().strftime("%Y-%m-%d %H:%M")))
        conn.commit()
        return conn.execute("SELECT changes()").fetchone()[0] > 0
    except Exception:
        return False

# ─── Parser de un resultado de la API ─────────────────────────────────────────

def parsear(item, rubro_nombre):
    nombre = (item.get("name") or "").strip()
    if not nombre:
        return None

    addr = item.get("mainAddress") or {}

    # Dirección
    calle  = (addr.get("streetName")  or "").strip()
    numero = (addr.get("streetNumber") or "").strip()
    direccion = f"{calle} {numero}".strip()

    # Comuna (localityForSEO es la más limpia)
    comuna = (addr.get("localityForSEO") or addr.get("localityToShow") or "").strip()
    if " - " in comuna:
        comuna = comuna.split(" - ")[0].strip()

    # Filtrar solo RM (la API puede mezclar regiones)
    addr_locality = addr.get("addressLocality") or ""
    if addr_locality and "Metropolitana" not in addr_locality:
        return None  # descarta fuera de RM

    # Coordenadas
    try:
        latitud  = float(addr.get("latitude")  or 0) or None
        longitud = float(addr.get("longitude") or 0) or None
    except (ValueError, TypeError):
        latitud = longitud = None

    # Teléfono — usar allPhones que ya trae el número completo
    telefono = None
    all_phones = addr.get("allPhones") or []
    if all_phones:
        telefono = (all_phones[0].get("number") or "").strip()
    if not telefono:
        phones = addr.get("phones") or []
        if phones:
            telefono = str(phones[0]).strip()

    # Email
    emails = item.get("emails") or []
    email = emails[0].strip() if emails else None

    # URL
    info_url = item.get("infoUrl") or ""
    url = f"https://www.amarillas.cl{info_url}" if info_url else None

    return {
        "nombre":    nombre,
        "rubro":     rubro_nombre,
        "telefono":  telefono or None,
        "email":     email,
        "direccion": direccion or None,
        "comuna":    comuna or None,
        "latitud":   latitud,
        "longitud":  longitud,
        "url":       url,
    }

# ─── Scraping de un rubro ──────────────────────────────────────────────────────

def scrape_rubro(conn, rubro_info):
    query        = rubro_info["query"]
    rubro_nombre = rubro_info["nombre"]
    total_nuevo  = 0
    total_visto  = 0
    page         = 1

    print(f"\n{'='*65}")
    print(f"  RUBRO: {rubro_nombre}  ({query})")
    print(f"{'='*65}")

    session = requests.Session()
    session.headers.update(HEADERS)
    session.get("https://www.amarillas.cl/", timeout=10)

    while True:
        params = {
            "searchWord": query,
            "state":      STATE,
            "page":       page,
            "size":       PAGE_SIZE,
        }
        try:
            r = session.get(API_URL, params=params, timeout=25)

            if r.status_code != 200:
                print(f"   HTTP {r.status_code} en pagina {page} - terminando")
                break

            # No forzar encoding — dejar que requests use el charset del header (utf-8)
            data = json.loads(r.content.decode("utf-8", errors="replace"))

            resultados = data.get("results", [])
            total_api  = data.get("total", 0)
            total_pages = -(-total_api // PAGE_SIZE)  # ceil division

            if not resultados:
                print(f"   Sin resultados en pagina {page}")
                break

            page_nuevos = 0
            for item in resultados:
                total_visto += 1
                d = parsear(item, rubro_nombre)
                if d:
                    if guardar(conn, d):
                        total_nuevo += 1
                        page_nuevos += 1
                        tel = d.get("telefono") or "-"
                        com = d.get("comuna") or ""
                        print(f"   + {d['nombre'][:45]:45} | {tel:16} | {com}")

            print(f"   Pag {page}/{total_pages}: {page_nuevos} nuevos  "
                  f"(acum {total_nuevo}, API total {total_api})")

            if total_visto >= total_api or len(resultados) < PAGE_SIZE:
                break

            page += 1
            time.sleep(random.uniform(DELAY_MIN, DELAY_MAX))

        except requests.RequestException as e:
            print(f"   Error de red: {e}")
            time.sleep(8)
            break
        except Exception as e:
            print(f"   Error: {e}")
            import traceback; traceback.print_exc()
            break

    print(f"\n  Total {rubro_nombre}: {total_nuevo} guardados")
    return total_nuevo

# ─── Exportar Excel ────────────────────────────────────────────────────────────

def exportar_excel(conn):
    print("\nExportando a Excel...")

    wb = Workbook()
    ws = wb.active
    ws.title = "Clientes Potenciales"

    cols   = ["#", "Nombre", "Rubro", "Teléfono", "Email", "Dirección", "Comuna", "Región", "URL", "Fecha"]
    fill_h = PatternFill("solid", fgColor="1F4E79")
    font_h = Font(bold=True, color="FFFFFF", size=11)

    for c, h in enumerate(cols, 1):
        cell = ws.cell(row=1, column=c, value=h)
        cell.fill = fill_h
        cell.font = font_h
        cell.alignment = Alignment(horizontal="center", vertical="center")
    ws.row_dimensions[1].height = 22

    colores = {
        "Pintura Automotriz":   "FFE0B2",
        "Mueblería":            "E8F5E9",
        "Funeraria":            "EDE7F6",
        "Barraca":              "FFF9C4",
        "Mecánico":             "E3F2FD",
        "Ferretería":           "FCE4EC",
        "Taller de Pintura":    "FFF3E0",
        "Desabolladura":        "F3E5F5",
        "Taller Automotriz":    "E1F5FE",
        "Carpintería":          "F9FBE7",
        "Ebanistería":          "E8EAF6",
        "Vulcanización":        "FFEBEE",
        "Tapicería Automotriz": "E0F7FA",
        "Hojalatería":          "FBE9E7",
        "Sandblasting":         "EFEBE9",
    }

    rows = conn.execute("""
        SELECT id, nombre, rubro, telefono, email, direccion, comuna, region, url, fecha
        FROM clientes ORDER BY rubro, nombre
    """).fetchall()

    for ri, row in enumerate(rows, 2):
        rubro = row[2] or ""
        fill  = PatternFill("solid", fgColor=colores.get(rubro, "FFFFFF"))
        for ci, val in enumerate(row, 1):
            cell = ws.cell(row=ri, column=ci, value=val)
            cell.fill = fill
            cell.alignment = Alignment(vertical="center")
            if ci == 9 and val:  # URL
                cell.font = Font(color="0563C1", underline="single")

    anchos = [5, 38, 18, 15, 28, 30, 20, 20, 45, 16]
    for i, w in enumerate(anchos, 1):
        ws.column_dimensions[get_column_letter(i)].width = w
    ws.freeze_panes = "A2"
    ws.auto_filter.ref = f"A1:{get_column_letter(len(cols))}1"

    # Hoja resumen
    ws2 = wb.create_sheet("Resumen")
    ws2.append(["Rubro", "Total", "Con teléfono", "Con email"])
    for c in range(1, 5):
        ws2.cell(row=1, column=c).font = Font(bold=True)
    total_g = 0
    for r in conn.execute("""
        SELECT rubro,
               COUNT(*),
               SUM(CASE WHEN telefono IS NOT NULL THEN 1 ELSE 0 END),
               SUM(CASE WHEN email IS NOT NULL THEN 1 ELSE 0 END)
        FROM clientes GROUP BY rubro ORDER BY COUNT(*) DESC
    """):
        ws2.append(list(r))
        total_g += r[1]
    ws2.append(["TOTAL", total_g, "", ""])
    ws2.cell(row=ws2.max_row, column=1).font = Font(bold=True)
    ws2.cell(row=ws2.max_row, column=2).font = Font(bold=True)
    for col in ["A","B","C","D"]:
        ws2.column_dimensions[col].width = 22

    wb.save(EXCEL_PATH)
    print(f"   Excel: {EXCEL_PATH}")

    # Imprimir resumen
    print()
    for r in conn.execute("""
        SELECT rubro, COUNT(*),
               SUM(CASE WHEN telefono IS NOT NULL THEN 1 ELSE 0 END)
        FROM clientes GROUP BY rubro ORDER BY COUNT(*) DESC
    """):
        print(f"   {r[0]:22} {r[1]:5} negocios  |  {r[2]} con teléfono")
    total_g = conn.execute("SELECT COUNT(*) FROM clientes").fetchone()[0]
    print(f"\n   TOTAL: {total_g} negocios en la base de datos")

# ─── Main ──────────────────────────────────────────────────────────────────────

def main():
    print("=" * 65)
    print("  Scraper Clientes Potenciales - JOP Compresores")
    print("  Region Metropolitana  |  amarillas.cl")
    print(f"  {datetime.now().strftime('%Y-%m-%d %H:%M')}")
    print("=" * 65)

    conn  = init_db()
    total = 0

    for rubro in RUBROS:
        t = scrape_rubro(conn, rubro)
        total += t
        print(f"\n  --- Pausa entre rubros ---")
        time.sleep(random.uniform(3, 5))

    print(f"\n{'='*65}")
    print(f"  SCRAPING COMPLETO  |  Total nuevos: {total}")
    print(f"{'='*65}")

    exportar_excel(conn)
    conn.close()

    print(f"\n  Archivos generados:")
    print(f"    {DB_PATH}")
    print(f"    {EXCEL_PATH}")

if __name__ == "__main__":
    main()
