"""
Importa clientes.db -> prospectos (MySQL local) vía /api/prospectos.
- Solo importa filas CON coordenadas (latitud y longitud no nulas).
- No duplica: compara contra lo que ya existe en /api/prospectos por (nombre, comuna) normalizados.
- Requiere el backend corriendo en local (mvnw spring-boot:run, puerto 8080).
"""

import sqlite3, os, requests, sys

DB_PATH  = os.path.join(os.path.dirname(os.path.abspath(__file__)), "clientes.db")
API_BASE = "http://localhost:8080/api/prospectos"
BATCH    = 200

def clave(nombre, comuna):
    return (nombre or "").strip().lower(), (comuna or "").strip().lower()

def main():
    try:
        r = requests.get(API_BASE, timeout=10)
        r.raise_for_status()
    except Exception as e:
        print(f"No pude conectar al backend local en {API_BASE}: {e}")
        print("Levanta el backend primero (cd back/sincronizador && ./mvnw spring-boot:run).")
        sys.exit(1)

    existentes = {clave(p.get("nombre"), p.get("comuna")) for p in r.json()}
    print(f"Prospectos ya existentes en local: {len(existentes)}")

    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    filas = conn.execute("""
        SELECT nombre, rubro, telefono, email, direccion, comuna, region,
               latitud, longitud, url, fuente, fecha
        FROM clientes
        WHERE latitud IS NOT NULL AND longitud IS NOT NULL
    """).fetchall()
    conn.close()

    print(f"Candidatos con coordenadas en clientes.db: {len(filas)}")

    nuevos = []
    vistos_en_este_batch = set()
    for f in filas:
        k = clave(f["nombre"], f["comuna"])
        if k in existentes or k in vistos_en_este_batch:
            continue
        vistos_en_este_batch.add(k)
        nuevos.append({
            "nombre":    f["nombre"],
            "rubro":     f["rubro"],
            "telefono":  f["telefono"],
            "email":     f["email"],
            "direccion": f["direccion"],
            "comuna":    f["comuna"],
            "region":    f["region"] or "Región Metropolitana",
            "latitud":   f["latitud"],
            "longitud":  f["longitud"],
            "url":       f["url"],
            "fuente":    f["fuente"],
            "fecha":     f["fecha"],
        })

    print(f"Nuevos a importar (sin duplicar): {len(nuevos)}")
    if not nuevos:
        print("Nada nuevo que importar.")
        return

    importados = 0
    for i in range(0, len(nuevos), BATCH):
        lote = nuevos[i:i+BATCH]
        resp = requests.post(f"{API_BASE}/bulk", json=lote, timeout=60)
        if resp.status_code in (200, 201):
            importados += len(lote)
            print(f"  Lote {i//BATCH + 1}: {len(lote)} importados (acumulado {importados})")
        else:
            print(f"  Lote {i//BATCH + 1} FALLÓ: HTTP {resp.status_code} - {resp.text[:200]}")

    print(f"\nTOTAL IMPORTADO: {importados}/{len(nuevos)}")

if __name__ == "__main__":
    main()
