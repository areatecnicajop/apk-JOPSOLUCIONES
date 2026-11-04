import requests, json, re
from bs4 import BeautifulSoup

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36",
    "Accept-Language": "es-CL,es;q=0.9",
    "Accept": "application/json, text/html, */*",
}

session = requests.Session()
session.headers.update(headers)

# Probar el endpoint de búsqueda que usa el frontend
search_urls = [
    "https://www.amarillas.cl/api/search?q=ferreteria&region=region-metropolitana",
    "https://www.amarillas.cl/api/buscar?q=ferreteria&loc=region-metropolitana",
    "https://www.amarillas.cl/buscar/ferreteria",
    "https://www.amarillas.cl/b/ferreteria/region-metropolitana",
    "https://www.amarillas.cl/b/ferreteria",
]

for url in search_urls:
    try:
        r = session.get(url, timeout=15)
        r.encoding = "utf-8"
        print(f"\n[{r.status_code}] {url}")
        print(f"  Final URL: {r.url}")
        print(f"  Content-Type: {r.headers.get('Content-Type','')}")
        # Ver primeras líneas
        soup = BeautifulSoup(r.text, "lxml")
        title = soup.title.string if soup.title else ""
        print(f"  Title: {title}")
        # Contar LD+JSON
        ld_jsons = soup.find_all("script", type="application/ld+json")
        biz_count = 0
        for s in ld_jsons:
            try:
                d = json.loads(s.string or "")
                if isinstance(d, dict) and d.get("@type") == "LocalBusiness":
                    biz_count += 1
            except: pass
        print(f"  LocalBusiness LD+JSON: {biz_count}")
        # Keywords meta
        kw = soup.find("meta", attrs={"name":"keywords"})
        if kw:
            print(f"  Keywords: {kw.get('content','')[:200]}")
    except Exception as e:
        print(f"  ERROR: {e}")

# Buscar el JS bundle para encontrar el endpoint real
print("\n\n=== Buscando endpoint en JS ===")
r = session.get("https://www.amarillas.cl/buscar/ferreteria/region-metropolitana", timeout=20)
r.encoding = "utf-8"
soup = BeautifulSoup(r.text, "lxml")
# Buscar en scripts el patrón de API
for script in soup.find_all("script", src=True):
    src = script.get("src", "")
    if "_next/static" in src and "chunks" in src:
        try:
            js_r = session.get("https://www.amarillas.cl" + src, timeout=10)
            js_text = js_r.text
            # Buscar URLs de API
            matches = re.findall(r'["\'](/api/[^"\']{5,50})["\']', js_text)
            if matches:
                print(f"\nEn {src}:")
                for m in set(matches): print(f"  {m}")
        except: pass
