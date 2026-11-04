"""
Scraper multi-fuente - Región Metropolitana, Chile
Fuentes: OpenStreetMap Overpass API + fonoguia.cl + buscador.cl
"""

import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")

import requests, sqlite3, time, random, json, os, re
from datetime import datetime
from urllib.parse import quote

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "clientes.db")

HEADERS_BROWSER = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "es-CL,es;q=0.9",
    "Accept-Encoding": "gzip, deflate, br",
}

# ─── DB ───────────────────────────────────────────────────────────────────────

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

def guardar(conn, d, fuente):
    try:
        conn.execute("""
            INSERT OR IGNORE INTO clientes
            (nombre, rubro, telefono, email, direccion, comuna, latitud, longitud, url, fuente, fecha)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (d["nombre"], d["rubro"], d.get("telefono"), d.get("email"),
              d.get("direccion"), d.get("comuna"),
              d.get("latitud"), d.get("longitud"), d.get("url"),
              fuente, datetime.now().strftime("%Y-%m-%d %H:%M")))
        conn.commit()
        return conn.execute("SELECT changes()").fetchone()[0] > 0
    except:
        return False

def limpiar_telefono(tel):
    if not tel:
        return None
    tel = re.sub(r'[^\d+]', '', str(tel))
    return tel if len(tel) >= 7 else None

# ─── FUENTE 1: OpenStreetMap Overpass API ─────────────────────────────────────

# Bounding box Región Metropolitana ampliada
BBOX = "-33.80,-71.00,-33.10,-70.30"

OSM_RUBROS = [
    ("Ebanistería",                     "craft=joiner"),
    ("Barraca",                         "shop=timber"),
    ("Carpintería",                     "craft=carpenter"),
    ("Taller Automotriz",               "shop=car_repair"),
    ("Serviteca",                       "shop=car_repair"),
    ("Vulcanización",                   "shop=tyres"),
    ("Tapicería Automotriz",            "craft=upholsterer"),
    ("Funeraria",                       "shop=funeral_directors"),
    ("Funeraria",                       "amenity=funeral_hall"),
    ("Taller de Pintura",               "craft=painter"),
    ("Pintura Automotriz",              "craft=painter"),
    ("Desabolladura",                   "repair=body"),
    ("Hojalatería",                     "craft=sheetmetal_worker"),
    ("Metalmecánica",                   "craft=metal_construction"),
    ("Metalmecánica",                   "industrial=machine_shop"),
    ("Maestranzas",                     "craft=machine_shop"),
    ("Lubricentros",                    "shop=oil"),
    ("Lubricentros",                    "amenity=fuel"),
    ("Centros dentales",                "amenity=dentist"),
    ("Servicio tecnico de herramientas","repair=yes"),
]

def scrape_osm(conn):
    print("\n" + "="*65)
    print("  FUENTE: OpenStreetMap Overpass API")
    print("="*65)

    OVERPASS_URLS = [
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    ]

    total_nuevo = 0
    procesados  = set()

    for rubro_nombre, tag_str in OSM_RUBROS:
        key, val = tag_str.split("=")
        print(f"\n  --- {rubro_nombre} [{tag_str}] ---")
        nuevos_rubro = 0

        query = f"""[out:json][timeout:60];
(
  node["{key}"="{val}"]({BBOX});
  way["{key}"="{val}"]({BBOX});
);
out center tags;"""

        resultado = None
        for overpass_url in OVERPASS_URLS:
            try:
                r = requests.get(
                    overpass_url,
                    params={"data": query},
                    timeout=90,
                    headers={"Accept": "application/json"}
                )
                if r.status_code == 200:
                    resultado = r.json()
                    break
                print(f"    [{overpass_url.split('/')[2]}] HTTP {r.status_code}")
            except Exception as e:
                print(f"    [{overpass_url.split('/')[2]}] Error: {e}")
                time.sleep(3)

        if not resultado:
            print(f"    Sin respuesta de ningún servidor")
            continue

        elementos = resultado.get("elements", [])
        print(f"    Elementos OSM: {len(elementos)}")

        for el in elementos:
            osm_id = el.get("id")
            if osm_id in procesados:
                continue
            procesados.add(osm_id)

            tags   = el.get("tags", {})
            nombre = (tags.get("name") or "").strip()
            if not nombre or len(nombre) < 3:
                continue

            lat = el.get("lat") or el.get("center", {}).get("lat")
            lon = el.get("lon") or el.get("center", {}).get("lon")

            calle  = tags.get("addr:street", "")
            numero = tags.get("addr:housenumber", "")
            direccion = f"{calle} {numero}".strip() or None
            comuna    = tags.get("addr:city") or tags.get("addr:suburb") or tags.get("addr:district") or None
            telefono  = limpiar_telefono(tags.get("phone") or tags.get("contact:phone") or tags.get("contact:mobile"))
            email     = tags.get("email") or tags.get("contact:email")
            website   = tags.get("website") or tags.get("contact:website")

            d = {
                "nombre":    nombre,
                "rubro":     rubro_nombre,
                "telefono":  telefono,
                "email":     email,
                "direccion": direccion,
                "comuna":    comuna,
                "latitud":   float(lat)  if lat  else None,
                "longitud":  float(lon) if lon else None,
                "url":       website,
            }

            if guardar(conn, d, "openstreetmap"):
                nuevos_rubro += 1
                total_nuevo  += 1
                coords = f"{float(lat):.4f},{float(lon):.4f}" if lat else "sin coords"
                print(f"    + {nombre[:48]} | {telefono or '-':15} | {comuna or '':15} | {coords}")

        print(f"  Subtotal {rubro_nombre}: {nuevos_rubro}")
        time.sleep(random.uniform(4, 7))

    print(f"\n  OSM TOTAL NUEVOS: {total_nuevo}")
    return total_nuevo


# ─── FUENTE 2: fonoguia.cl ────────────────────────────────────────────────────

FONOGUIA_RUBROS = [
    {"nombre": "Barraca",                          "cat": "barracas-de-madera",            "slug": "barracas-de-madera"},
    {"nombre": "Pintura Automotriz",               "cat": "pintura-automotriz",            "slug": "pintura-automotriz"},
    {"nombre": "Desabolladura",                    "cat": "desabolladura-y-pintura",       "slug": "desabolladura-y-pintura"},
    {"nombre": "Carpintería",                      "cat": "carpintería",                   "slug": "carpinteria"},
    {"nombre": "Tapicería Automotriz",             "cat": "tapicería-automotriz",          "slug": "tapiceria-automotriz"},
    {"nombre": "Hojalatería",                      "cat": "hojalatería",                   "slug": "hojalateria"},
    {"nombre": "Funeraria",                        "cat": "funerarias",                    "slug": "funerarias"},
    {"nombre": "Fabricas de urnas",                "cat": "fabricas-de-urnas",             "slug": "fabricas-de-urnas"},
    {"nombre": "Taller de Pintura",                "cat": "talleres-de-pintura",           "slug": "talleres-de-pintura"},
    {"nombre": "Ebanistería",                      "cat": "ebanistería",                   "slug": "ebanisteria"},
    {"nombre": "Vulcanización",                    "cat": "vulcanización",                 "slug": "vulcanizacion"},
    {"nombre": "Taller Automotriz",                "cat": "talleres-automotrices",         "slug": "talleres-automotrices"},
    {"nombre": "Serviteca",                        "cat": "servitecas",                    "slug": "servitecas"},
    {"nombre": "Metalmecánica",                    "cat": "metalmecánica",                 "slug": "metalmecanica"},
    {"nombre": "Maestranzas",                      "cat": "maestranzas",                   "slug": "maestranzas"},
    {"nombre": "Lubricentros",                     "cat": "lubricentros",                  "slug": "lubricentros"},
    {"nombre": "Mecánico",                         "cat": "talleres-mecanicos",            "slug": "talleres-mecanicos"},
    {"nombre": "Centros dentales",                 "cat": "centros-dentales",              "slug": "centros-dentales"},
    {"nombre": "Servicio tecnico de herramientas", "cat": "servicio-tecnico-herramientas", "slug": "servicio-tecnico-herramientas"},
]

def scrape_fonoguia(conn):
    print("\n" + "="*65)
    print("  FUENTE: fonoguia.cl")
    print("="*65)

    session = requests.Session()
    session.headers.update(HEADERS_BROWSER)
    total_nuevo = 0

    for rubro_info in FONOGUIA_RUBROS:
        rubro_nombre = rubro_info["nombre"]
        slug         = rubro_info["slug"]
        nuevos_rubro = 0
        page = 1

        print(f"\n  --- {rubro_nombre} ---")

        while page <= 30:
            try:
                url = f"https://www.fonoguia.cl/{slug}/region-metropolitana/{page}/"
                r = session.get(url, timeout=20)

                if r.status_code in [404, 410]:
                    if page == 1:
                        # intentar URL alternativa
                        url2 = f"https://www.fonoguia.cl/buscar/?q={quote(rubro_info['cat'])}&region=rm&p={page}"
                        r = session.get(url2, timeout=20)
                        if r.status_code != 200:
                            break
                    else:
                        break

                if r.status_code != 200:
                    print(f"    HTTP {r.status_code}")
                    break

                texto = r.text

                # JSON-LD primero
                ld_blocks = re.findall(r'<script[^>]*type="application/ld\+json"[^>]*>(.*?)</script>', texto, re.DOTALL)
                encontrados = 0
                for block in ld_blocks:
                    try:
                        data = json.loads(block)
                        items = data if isinstance(data, list) else [data]
                        for item in items:
                            if item.get("@type") not in ("LocalBusiness","Organization","Store",
                                                          "AutoRepair","FuneralHome"):
                                continue
                            nombre = (item.get("name") or "").strip()
                            if not nombre:
                                continue
                            addr = item.get("address") or {}
                            geo  = item.get("geo") or {}
                            d = {
                                "nombre":    nombre,
                                "rubro":     rubro_nombre,
                                "telefono":  limpiar_telefono(item.get("telephone")),
                                "email":     item.get("email"),
                                "direccion": addr.get("streetAddress"),
                                "comuna":    addr.get("addressLocality"),
                                "latitud":   float(geo["latitude"])  if geo.get("latitude")  else None,
                                "longitud":  float(geo["longitude"]) if geo.get("longitude") else None,
                                "url":       item.get("url"),
                            }
                            if guardar(conn, d, "fonoguia.cl"):
                                nuevos_rubro += 1
                                total_nuevo  += 1
                                encontrados  += 1
                                coords = f"{d['latitud']:.4f},{d['longitud']:.4f}" if d["latitud"] else "sin coords"
                                print(f"    + {nombre[:48]} | {d.get('telefono') or '-':15} | {d.get('comuna') or '':15} | {coords}")
                    except:
                        continue

                # Regex fallback si no hay JSON-LD
                if not encontrados:
                    nombres_re = re.findall(
                        r'(?:class="[^"]*(?:nombre|name|title|empresa)[^"]*"[^>]*>|itemprop="name">)([^<]{4,80})<',
                        texto, re.IGNORECASE
                    )
                    telefonos_re = re.findall(r'\+56[\s\-]?[29]\s?[\d\s\-]{7,10}', texto)
                    comunas_re   = re.findall(r'(?:class="[^"]*(?:comuna|locality)[^"]*">)([^<]{3,40})<', texto, re.IGNORECASE)

                    for i, nombre in enumerate(nombres_re):
                        nombre = nombre.strip()
                        if len(nombre) < 4:
                            continue
                        d = {
                            "nombre":   nombre,
                            "rubro":    rubro_nombre,
                            "telefono": limpiar_telefono(telefonos_re[i]) if i < len(telefonos_re) else None,
                            "comuna":   comunas_re[i].strip() if i < len(comunas_re) else None,
                            "url":      url,
                        }
                        if guardar(conn, d, "fonoguia.cl"):
                            nuevos_rubro += 1
                            total_nuevo  += 1
                            print(f"    + {nombre[:50]}")

                    if not nombres_re and page > 1:
                        break

                page += 1
                time.sleep(random.uniform(1, 2.5))

            except Exception as e:
                print(f"    Error p{page}: {e}")
                break

        print(f"  Subtotal {rubro_nombre}: {nuevos_rubro}")
        time.sleep(random.uniform(1, 2))

    print(f"\n  FONOGUIA TOTAL NUEVOS: {total_nuevo}")
    return total_nuevo


# ─── FUENTE 3: buscador.cl ────────────────────────────────────────────────────

BUSCADOR_RUBROS = [
    {"nombre": "Barraca",                          "q": "barraca madera santiago"},
    {"nombre": "Pintura Automotriz",               "q": "pintura automotriz santiago"},
    {"nombre": "Desabolladura",                    "q": "desabolladura pintura santiago"},
    {"nombre": "Carpintería",                      "q": "carpinteria santiago"},
    {"nombre": "Tapicería Automotriz",             "q": "tapiceria automotriz santiago"},
    {"nombre": "Hojalatería",                      "q": "hojalateria santiago"},
    {"nombre": "Funeraria",                        "q": "funeraria santiago"},
    {"nombre": "Fabricas de urnas",                "q": "fabrica urnas santiago"},
    {"nombre": "Taller de Pintura",                "q": "taller pintura santiago"},
    {"nombre": "Ebanistería",                      "q": "ebanisteria santiago"},
    {"nombre": "Vulcanización",                    "q": "vulcanizacion santiago"},
    {"nombre": "Taller Automotriz",                "q": "taller automotriz santiago"},
    {"nombre": "Serviteca",                        "q": "serviteca santiago"},
    {"nombre": "Metalmecánica",                    "q": "metalmecanica santiago"},
    {"nombre": "Maestranzas",                      "q": "maestranza santiago"},
    {"nombre": "Lubricentros",                     "q": "lubricentro santiago"},
    {"nombre": "Mecánico",                         "q": "taller mecanico santiago"},
    {"nombre": "Centros dentales",                 "q": "centro dental santiago"},
    {"nombre": "Servicio tecnico de herramientas", "q": "servicio tecnico herramientas santiago"},
]

def scrape_buscador(conn):
    print("\n" + "="*65)
    print("  FUENTE: buscador.cl")
    print("="*65)

    session = requests.Session()
    session.headers.update(HEADERS_BROWSER)
    total_nuevo = 0

    for rubro_info in BUSCADOR_RUBROS:
        rubro_nombre = rubro_info["nombre"]
        q            = rubro_info["q"]
        nuevos_rubro = 0
        page = 1

        print(f"\n  --- {rubro_nombre} ---")

        while page <= 10:
            try:
                url = f"https://www.buscador.cl/search?q={quote(q)}&loc=region+metropolitana&page={page}"
                r = session.get(url, timeout=20)

                if r.status_code in [404, 410]:
                    break
                if r.status_code != 200:
                    print(f"    HTTP {r.status_code} en {url}")
                    break

                texto = r.text

                ld_blocks = re.findall(r'<script[^>]*type="application/ld\+json"[^>]*>(.*?)</script>', texto, re.DOTALL)
                encontrados = 0

                for block in ld_blocks:
                    try:
                        data = json.loads(block)
                        items = data if isinstance(data, list) else [data]
                        for item in items:
                            nombre = (item.get("name") or "").strip()
                            if not nombre or len(nombre) < 3:
                                continue
                            addr = item.get("address") or {}
                            geo  = item.get("geo") or {}
                            # Filtrar solo RM
                            region_item = addr.get("addressRegion", "")
                            if region_item and "Metropolitana" not in region_item and "RM" not in region_item:
                                continue
                            d = {
                                "nombre":    nombre,
                                "rubro":     rubro_nombre,
                                "telefono":  limpiar_telefono(item.get("telephone")),
                                "email":     item.get("email"),
                                "direccion": addr.get("streetAddress"),
                                "comuna":    addr.get("addressLocality"),
                                "latitud":   float(geo["latitude"])  if geo.get("latitude")  else None,
                                "longitud":  float(geo["longitude"]) if geo.get("longitude") else None,
                                "url":       item.get("url"),
                            }
                            if guardar(conn, d, "buscador.cl"):
                                nuevos_rubro += 1
                                total_nuevo  += 1
                                encontrados  += 1
                                coords = f"{d['latitud']:.4f},{d['longitud']:.4f}" if d["latitud"] else "sin coords"
                                print(f"    + {nombre[:48]} | {d.get('telefono') or '-':15} | {d.get('comuna') or '':15} | {coords}")
                    except:
                        continue

                if encontrados == 0 and page > 1:
                    break

                page += 1
                time.sleep(random.uniform(1, 2.5))

            except Exception as e:
                print(f"    Error p{page}: {e}")
                break

        print(f"  Subtotal {rubro_nombre}: {nuevos_rubro}")
        time.sleep(random.uniform(1, 2))

    print(f"\n  BUSCADOR TOTAL NUEVOS: {total_nuevo}")
    return total_nuevo


# ─── FUENTE 4: amarillas.cl (API pública, datos estructurados con coords) ─────

AMARILLAS_STATE     = "region-metropolitana"
AMARILLAS_API_URL   = "https://www.amarillas.cl/api/advertisements"
AMARILLAS_PAGE_SIZE = 20

AMARILLAS_RUBROS = [
    {"nombre": "Pintura Automotriz",    "query": "pintura automotriz"},
    {"nombre": "Funeraria",             "query": "funeraria"},
    {"nombre": "Barraca",               "query": "barraca"},
    {"nombre": "Mecánico",              "query": "mecanico"},
    {"nombre": "Ferretería",            "query": "ferreteria"},
    {"nombre": "Taller de Pintura",     "query": "taller de pintura"},
    {"nombre": "Desabolladura",         "query": "desabolladura"},
    {"nombre": "Taller Automotriz",     "query": "taller automotriz"},
    {"nombre": "Carpintería",           "query": "carpinteria"},
    {"nombre": "Ebanistería",           "query": "ebanisteria"},
    {"nombre": "Vulcanización",         "query": "vulcanizacion"},
    {"nombre": "Tapicería Automotriz",  "query": "tapiceria automotriz"},
    {"nombre": "Hojalatería",           "query": "hojalateria"},
    {"nombre": "Sandblasting",          "query": "sandblasting"},
    {"nombre": "Lubricentros",          "query": "lubricentro"},
    {"nombre": "Metalmecánica",         "query": "metalmecanica"},
    {"nombre": "Maestranzas",           "query": "maestranza"},
    {"nombre": "Centros dentales",      "query": "centro dental"},
]

def _amarillas_parsear(item, rubro_nombre):
    nombre = (item.get("name") or "").strip()
    if not nombre:
        return None

    addr = item.get("mainAddress") or {}

    calle  = (addr.get("streetName")  or "").strip()
    numero = (addr.get("streetNumber") or "").strip()
    direccion = f"{calle} {numero}".strip()

    comuna = (addr.get("localityForSEO") or addr.get("localityToShow") or "").strip()
    if " - " in comuna:
        comuna = comuna.split(" - ")[0].strip()

    addr_locality = addr.get("addressLocality") or ""
    if addr_locality and "Metropolitana" not in addr_locality:
        return None

    try:
        latitud  = float(addr.get("latitude")  or 0) or None
        longitud = float(addr.get("longitude") or 0) or None
    except (ValueError, TypeError):
        latitud = longitud = None

    telefono = None
    all_phones = addr.get("allPhones") or []
    if all_phones:
        telefono = (all_phones[0].get("number") or "").strip()
    if not telefono:
        phones = addr.get("phones") or []
        if phones:
            telefono = str(phones[0]).strip()
    telefono = limpiar_telefono(telefono)

    emails = item.get("emails") or []
    email = emails[0].strip() if emails else None

    info_url = item.get("infoUrl") or ""
    url = f"https://www.amarillas.cl{info_url}" if info_url else None

    return {
        "nombre":    nombre,
        "rubro":     rubro_nombre,
        "telefono":  telefono,
        "email":     email,
        "direccion": direccion or None,
        "comuna":    comuna or None,
        "latitud":   latitud,
        "longitud":  longitud,
        "url":       url,
    }

def scrape_amarillas(conn):
    print("\n" + "="*65)
    print("  FUENTE: amarillas.cl")
    print("="*65)

    session = requests.Session()
    session.headers.update({
        "User-Agent":      HEADERS_BROWSER["User-Agent"],
        "Accept":          "application/json, */*",
        "Accept-Language": "es-CL,es;q=0.9",
        "Referer":         "https://www.amarillas.cl/",
    })
    try:
        session.get("https://www.amarillas.cl/", timeout=10)
    except Exception:
        pass

    total_nuevo = 0

    for rubro_info in AMARILLAS_RUBROS:
        query        = rubro_info["query"]
        rubro_nombre = rubro_info["nombre"]
        nuevos_rubro = 0
        total_visto  = 0
        page = 1

        print(f"\n  --- {rubro_nombre} ({query}) ---")

        while True:
            params = {
                "searchWord": query,
                "state":      AMARILLAS_STATE,
                "page":       page,
                "size":       AMARILLAS_PAGE_SIZE,
            }
            try:
                r = session.get(AMARILLAS_API_URL, params=params, timeout=25)
                if r.status_code != 200:
                    print(f"    HTTP {r.status_code} en pagina {page} - terminando")
                    break

                data = json.loads(r.content.decode("utf-8", errors="replace"))
                resultados  = data.get("results", [])
                total_api   = data.get("total", 0)
                total_pages = -(-total_api // AMARILLAS_PAGE_SIZE)

                if not resultados:
                    break

                page_nuevos = 0
                for item in resultados:
                    total_visto += 1
                    d = _amarillas_parsear(item, rubro_nombre)
                    if d and guardar(conn, d, "amarillas.cl"):
                        nuevos_rubro += 1
                        total_nuevo  += 1
                        page_nuevos  += 1
                        coords = f"{d['latitud']:.4f},{d['longitud']:.4f}" if d["latitud"] else "sin coords"
                        print(f"    + {d['nombre'][:45]:45} | {d.get('telefono') or '-':15} | {coords}")

                print(f"    Pag {page}/{total_pages}: {page_nuevos} nuevos (API total {total_api})")

                if total_visto >= total_api or len(resultados) < AMARILLAS_PAGE_SIZE:
                    break
                page += 1
                time.sleep(random.uniform(0.5, 1.5))

            except requests.RequestException as e:
                print(f"    Error de red: {e}")
                break
            except Exception as e:
                print(f"    Error: {e}")
                break

        print(f"  Subtotal {rubro_nombre}: {nuevos_rubro}")
        time.sleep(random.uniform(1, 2))

    print(f"\n  AMARILLAS TOTAL NUEVOS: {total_nuevo}")
    return total_nuevo


# ─── FUENTE 5: Google Places (Text Search) ────────────────────────────────────
# Requiere GOOGLE_PLACES_API_KEY en el ambiente. Debe ser una key SIN restricción
# de HTTP referrer (las de apps móviles/web no sirven para llamadas server-side).
# Se puede restringir por IP del servidor que corre el scraper.

GOOGLE_PLACES_API_KEY = os.environ.get("GOOGLE_PLACES_API_KEY", "").strip()

GOOGLE_RUBROS = [rubro_nombre for rubro_nombre, _ in dict.fromkeys(
    [(r, None) for r in [
        "Taller Automotriz", "Mecánico", "Lubricentros", "Vulcanización",
        "Tapicería Automotriz", "Funeraria", "Taller de Pintura",
        "Pintura Automotriz", "Desabolladura", "Hojalatería", "Metalmecánica",
        "Maestranzas", "Centros dentales", "Carpintería",
        "Ebanistería", "Barraca", "Ferretería", "Sandblasting",
    ]]
).keys()]

def scrape_google_places(conn):
    print("\n" + "="*65)
    print("  FUENTE: Google Places (Text Search)")
    print("="*65)

    if not GOOGLE_PLACES_API_KEY:
        print("  GOOGLE_PLACES_API_KEY no configurada — omitiendo esta fuente.")
        print("  Set-Item Env:GOOGLE_PLACES_API_KEY 'tu-key'  (PowerShell)")
        return 0

    total_nuevo = 0
    session = requests.Session()

    for rubro_nombre in GOOGLE_RUBROS:
        print(f"\n  --- {rubro_nombre} ---")
        nuevos_rubro = 0
        query = f"{rubro_nombre} Región Metropolitana Chile"
        next_page_token = None
        pagina = 1

        while pagina <= 3:
            params = {
                "query": query,
                "key":   GOOGLE_PLACES_API_KEY,
                "region": "cl",
            }
            if next_page_token:
                params = {"pagetoken": next_page_token, "key": GOOGLE_PLACES_API_KEY}
                time.sleep(2.2)  # Google exige un pequeño delay antes de usar el token

            try:
                r = session.get(
                    "https://maps.googleapis.com/maps/api/place/textsearch/json",
                    params=params, timeout=20
                )
                data = r.json()
                status = data.get("status")

                if status not in ("OK", "ZERO_RESULTS"):
                    print(f"    Google status: {status} — {data.get('error_message', '')}")
                    break

                for item in data.get("results", []):
                    nombre = (item.get("name") or "").strip()
                    if not nombre:
                        continue
                    loc = (item.get("geometry") or {}).get("location") or {}
                    d = {
                        "nombre":    nombre,
                        "rubro":     rubro_nombre,
                        "telefono":  None,
                        "email":     None,
                        "direccion": item.get("formatted_address"),
                        "comuna":    None,
                        "latitud":   loc.get("lat"),
                        "longitud":  loc.get("lng"),
                        "url":       f"https://www.google.com/maps/place/?q=place_id:{item.get('place_id')}" if item.get("place_id") else None,
                    }
                    if guardar(conn, d, "google_places"):
                        nuevos_rubro += 1
                        total_nuevo  += 1
                        coords = f"{d['latitud']:.4f},{d['longitud']:.4f}" if d["latitud"] else "sin coords"
                        print(f"    + {nombre[:48]:48} | {coords}")

                next_page_token = data.get("next_page_token")
                if not next_page_token:
                    break
                pagina += 1

            except Exception as e:
                print(f"    Error: {e}")
                break

        print(f"  Subtotal {rubro_nombre}: {nuevos_rubro}")
        time.sleep(random.uniform(0.5, 1))

    print(f"\n  GOOGLE PLACES TOTAL NUEVOS: {total_nuevo}")
    return total_nuevo


# ─── Geocodificación de respaldo (Nominatim) ──────────────────────────────────
# Para filas que tienen dirección/comuna pero no coordenadas. Gratis, sin key,
# pero con política estricta de uso: max 1 req/seg y User-Agent identificable.

def geocodificar_faltantes(conn, limite=300):
    print("\n" + "="*65)
    print("  GEOCODIFICACIÓN DE RESPALDO (Nominatim / OSM)")
    print("="*65)

    filas = conn.execute("""
        SELECT id, nombre, direccion, comuna FROM clientes
        WHERE (latitud IS NULL OR longitud IS NULL)
          AND (direccion IS NOT NULL OR comuna IS NOT NULL)
        LIMIT ?
    """, (limite,)).fetchall()

    if not filas:
        print("  Nada que geocodificar.")
        return 0

    print(f"  {len(filas)} registros sin coordenadas (límite {limite})")

    headers = {"User-Agent": "jop-soluciones-scraper/1.0 (contacto: areatecnicajop@gmail.com)"}
    geocodificados = 0

    for id_, nombre, direccion, comuna in filas:
        partes = [p for p in [direccion, comuna, "Región Metropolitana", "Chile"] if p]
        query = ", ".join(partes)
        try:
            r = requests.get(
                "https://nominatim.openstreetmap.org/search",
                params={"q": query, "format": "json", "limit": 1, "countrycodes": "cl"},
                headers=headers, timeout=15
            )
            resultados = r.json()
            if resultados:
                lat = float(resultados[0]["lat"])
                lon = float(resultados[0]["lon"])
                conn.execute("UPDATE clientes SET latitud=?, longitud=? WHERE id=?", (lat, lon, id_))
                conn.commit()
                geocodificados += 1
                print(f"    + {nombre[:40]:40} -> {lat:.4f},{lon:.4f}")
        except Exception as e:
            print(f"    Error geocodificando '{nombre[:30]}': {e}")

        time.sleep(1.1)  # política de Nominatim: max 1 req/seg

    print(f"\n  GEOCODIFICADOS: {geocodificados}/{len(filas)}")
    return geocodificados


# ─── MAIN ─────────────────────────────────────────────────────────────────────

def main():
    print("=" * 65)
    print("  Scraper Multi-Fuente - JOP Compresores")
    print("  OpenStreetMap | fonoguia.cl | buscador.cl | amarillas.cl | Google Places")
    print(f"  {datetime.now().strftime('%Y-%m-%d %H:%M')}")
    print("=" * 65)

    conn = init_db()

    t1 = scrape_osm(conn)
    t2 = scrape_fonoguia(conn)
    t3 = scrape_buscador(conn)
    t4 = scrape_amarillas(conn)
    t5 = scrape_google_places(conn)

    total = t1 + t2 + t3 + t4 + t5

    print(f"\n{'='*65}")
    print(f"  TOTAL NUEVOS (todas las fuentes): {total}")
    print(f"{'='*65}\n")

    geocodificar_faltantes(conn)

    total_db  = conn.execute("SELECT COUNT(*) FROM clientes").fetchone()[0]
    con_coord = conn.execute("SELECT COUNT(*) FROM clientes WHERE latitud IS NOT NULL AND longitud IS NOT NULL").fetchone()[0]
    print(f"  TOTAL EN BD: {total_db}  |  CON COORDENADAS: {con_coord}  |  SIN COORDENADAS: {total_db - con_coord}\n")

    for r in conn.execute("""
        SELECT rubro, COUNT(*),
               SUM(CASE WHEN latitud IS NOT NULL THEN 1 ELSE 0 END),
               SUM(CASE WHEN telefono IS NOT NULL THEN 1 ELSE 0 END)
        FROM clientes GROUP BY rubro ORDER BY COUNT(*) DESC
    """):
        print(f"  {r[0]:25} total={r[1]:4}  coords={r[2]:4}  fono={r[3]:4}")

    conn.close()

if __name__ == "__main__":
    main()
