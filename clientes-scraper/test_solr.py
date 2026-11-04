import requests, json

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36",
    "Accept": "application/json",
    "Referer": "https://www.amarillas.cl/b/ferreteria/region-metropolitana",
}

session = requests.Session()
session.get("https://www.amarillas.cl/", headers=headers, timeout=10)

# Intentar API Solr directamente
apis = [
    "https://www.amarillas.cl/api/solr/search?searchWord=ferreteria&state=region-metropolitana&page=1&rows=20",
    "https://www.amarillas.cl/api/advertisements?searchWord=ferreteria&state=region-metropolitana&page=1",
    "https://www.amarillas.cl/api/advertisements?fieldRank=1&searchWord=ferreteria&state=region-metropolitana",
    "https://www.amarillas.cl/api/solr/suggestions/search-words?searchWord=ferreteria",
]

for url in apis:
    try:
        r = session.get(url, headers=headers, timeout=15)
        r.encoding = "utf-8"
        ct = r.headers.get("Content-Type", "")
        print(f"\n[{r.status_code}] {url}")
        print(f"  CT: {ct}")
        if "json" in ct.lower():
            try:
                d = r.json()
                print(f"  JSON keys: {list(d.keys()) if isinstance(d,dict) else type(d).__name__}")
                print(json.dumps(d, indent=2, ensure_ascii=False)[:1500])
            except Exception as e:
                print(f"  Parse error: {e}")
                print(r.text[:500])
        else:
            print(f"  Body: {r.text[:300]}")
    except Exception as e:
        print(f"  ERROR: {e}")

# Probar paginación de la URL /b/
print("\n\n=== Paginación /b/ferreteria/region-metropolitana ===")
from bs4 import BeautifulSoup

for page in [1, 2, 3]:
    url = f"https://www.amarillas.cl/b/ferreteria/region-metropolitana?page={page}"
    r = session.get(url, timeout=15)
    r.encoding = "utf-8"
    soup = BeautifulSoup(r.text, "lxml")
    ld = soup.find_all("script", type="application/ld+json")
    biz = [json.loads(s.string) for s in ld if s.string and "LocalBusiness" in s.string]
    print(f"  Página {page}: {len(biz)} negocios | URL final: {r.url}")
    if biz:
        print(f"    Primero: {biz[0].get('name')} | Tel: {biz[0].get('telephone')} | Dir: {biz[0].get('address')}")
        print(f"    Ultimo: {biz[-1].get('name')} | Tel: {biz[-1].get('telephone')}")
