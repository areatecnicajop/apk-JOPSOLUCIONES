import requests
from bs4 import BeautifulSoup
import re, json

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36",
    "Accept-Language": "es-CL,es;q=0.9",
}

session = requests.Session()
session.headers.update(headers)

url = "https://www.amarillas.cl/buscar/ferreteria/region-metropolitana"
r = session.get(url, timeout=20)
r.encoding = "utf-8"

# Guardar HTML completo
with open("D:/JOP/clientes-scraper/pagina.html", "w", encoding="utf-8") as f:
    f.write(r.text)
print(f"HTML guardado ({len(r.text)} chars)")

soup = BeautifulSoup(r.text, "lxml")

# Buscar el div.listing y ver su contenido
listing = soup.find("div", class_=re.compile(r"listing", re.I))
if listing:
    texto = listing.get_text(" ", strip=True)
    print(f"\n=== Contenido div.listing ({len(listing.find_all())} elementos hijos) ===")
    print(texto[:3000])
else:
    print("No se encontró div.listing")

# Ver si hay datos JSON embebidos (Next.js __NEXT_DATA__)
script = soup.find("script", id="__NEXT_DATA__")
if script:
    print("\n=== __NEXT_DATA__ encontrado ===")
    try:
        data = json.loads(script.string)
        print(json.dumps(data, indent=2, ensure_ascii=False)[:5000])
    except Exception as e:
        print(f"Error parseando JSON: {e}")
        print(script.string[:2000])
else:
    print("\n⚠️  No hay __NEXT_DATA__ (puede ser SSR o app router)")
    # Buscar otros scripts con datos
    scripts = soup.find_all("script", type="application/json")
    for s in scripts[:3]:
        print(f"\nScript JSON encontrado: {str(s)[:500]}")
