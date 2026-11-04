import requests
from bs4 import BeautifulSoup
import re

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36",
    "Accept-Language": "es-CL,es;q=0.9",
}

url = "https://www.amarillas.cl/busqueda/ferreteria/region-metropolitana"
r = requests.get(url, headers=headers, timeout=20)
print(f"Status: {r.status_code}")
print(f"URL final: {r.url}")

soup = BeautifulSoup(r.text, "lxml")
title = soup.title.string if soup.title else "N/A"
print(f"Title: {title}")

# Buscar clases con keywords
all_classes = set()
for tag in soup.find_all(class_=True):
    for cls in tag.get("class", []):
        all_classes.add(cls)

keywords = ["listing", "result", "business", "empresa", "card", "item", "search"]
print("\nClases relevantes encontradas:")
for cls in sorted(all_classes):
    for kw in keywords:
        if kw in cls.lower():
            items = soup.find_all(class_=cls)
            print(f"  .{cls}: {len(items)} elementos")
            break

# Ver si hay datos
print("\n--- Primeros 3000 chars del body ---")
body = soup.body.get_text(" ", strip=True) if soup.body else ""
print(body[:3000])
