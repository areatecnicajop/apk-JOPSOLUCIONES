import requests
from bs4 import BeautifulSoup

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36",
    "Accept-Language": "es-CL,es;q=0.9",
    "Referer": "https://www.amarillas.cl/",
}

# Probar distintos formatos de URL
urls = [
    "https://www.amarillas.cl/search?q=ferreteria&where=region+metropolitana",
    "https://www.amarillas.cl/search?what=ferreteria&where=region-metropolitana",
    "https://www.amarillas.cl/buscar/ferreteria/region-metropolitana",
    "https://www.amarillas.cl/ferreteria/region-metropolitana",
    "https://www.amarillas.cl/?q=ferreteria&location=region-metropolitana",
]

session = requests.Session()
session.headers.update(headers)

# Primero cargar el home para obtener cookies
home = session.get("https://www.amarillas.cl/", timeout=15)
print(f"Home: {home.status_code} | cookies: {dict(home.cookies)}")

for url in urls:
    try:
        r = session.get(url, timeout=15, allow_redirects=True)
        print(f"\n[{r.status_code}] {url}")
        print(f"  -> Final URL: {r.url}")
        soup = BeautifulSoup(r.text, "lxml")
        title = soup.title.string if soup.title else "N/A"
        print(f"  -> Title: {title[:80]}")
        body_text = soup.body.get_text(" ", strip=True)[:200] if soup.body else ""
        print(f"  -> Body: {body_text[:200]}")
    except Exception as e:
        print(f"  ERROR: {e}")
