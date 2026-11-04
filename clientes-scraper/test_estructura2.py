import requests
from bs4 import BeautifulSoup
import re

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36",
    "Accept-Language": "es-CL,es;q=0.9",
    "Accept-Encoding": "gzip, deflate, br",
}

session = requests.Session()
session.headers.update(headers)

url = "https://www.amarillas.cl/buscar/ferreteria/region-metropolitana"
r = session.get(url, timeout=20)
print(f"Status: {r.status_code}")
print(f"URL: {r.url}")

# Forzar encoding
r.encoding = "utf-8"
soup = BeautifulSoup(r.text, "lxml")

print(f"Title: {soup.title.string if soup.title else 'N/A'}")
print()

# Buscar TODAS las clases
print("=== Todas las clases con 'result','list','biz','company','empresa','card','item' ===")
all_classes = {}
for tag in soup.find_all(True):
    for cls in tag.get("class", []):
        key = f"{tag.name}.{cls}"
        all_classes[key] = all_classes.get(key, 0) + 1

for key, count in sorted(all_classes.items(), key=lambda x: -x[1]):
    for kw in ["result", "list", "biz", "company", "empresa", "card", "item", "search", "listing"]:
        if kw in key.lower():
            print(f"  {key}: {count}x")
            break

print("\n=== Primeros 5000 chars del HTML ===")
print(r.text[:5000])
