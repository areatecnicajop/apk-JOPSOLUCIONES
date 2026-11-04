"""Test rápido: 2 páginas de ferretería"""
import requests, json

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36",
    "Accept": "application/json, */*",
    "Accept-Language": "es-CL,es;q=0.9",
}

session = requests.Session()
session.headers.update(headers)
session.get("https://www.amarillas.cl/", timeout=10)

r = session.get("https://www.amarillas.cl/api/advertisements",
    params={"searchWord": "ferreteria", "state": "region-metropolitana", "page": 1},
    timeout=20)

r.encoding = "latin-1"
data = r.json()

print(f"Total disponibles: {data['total']}")
print(f"Resultados en página 1: {len(data['results'])}")
print()

for item in data["results"][:5]:
    nombre = item.get("name", "")
    addr = item.get("mainAddress") or {}
    calle = addr.get("streetName", "")
    num = addr.get("streetNumber", "")
    locality = addr.get("localityForSEO") or addr.get("localityToShow","")
    phones = addr.get("allPhones") or addr.get("phones") or []
    tel = str(phones[0]) if phones else "sin tel"
    print(f"  📌 {nombre}")
    print(f"     Dir: {calle} {num}, {locality}")
    print(f"     Tel: {tel}")
    print()
