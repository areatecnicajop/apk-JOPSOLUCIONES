import requests, json, sys
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

headers = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36",
    "Accept": "application/json, */*",
}

session = requests.Session()
session.headers.update(headers)
session.get("https://www.amarillas.cl/", timeout=10)

def test(params):
    r = session.get("https://www.amarillas.cl/api/advertisements", params=params, timeout=20)
    r.encoding = "latin-1"
    try:
        d = r.json()
        res = d.get("results", [])
        print(f"  params={params} -> total={d.get('total')} results={len(res)}")
        if res:
            print(f"    1st: {res[0].get('name','?')}")
    except Exception as e:
        print(f"  ERROR: {e} | body: {r.text[:200]}")

# Probar distintos parámetros de paginación
base = {"searchWord": "ferreteria", "state": "region-metropolitana"}
test({**base, "page": 1})
test({**base, "page": 1, "rows": 20})
test({**base, "page": 1, "size": 20})
test({**base, "page": 1, "pageSize": 20})
test({**base, "page": 1, "limit": 20})
test({**base, "page": 1, "numResults": 20})
test({**base, "pageNumber": 1})
test({**base, "fieldRank": 1, "page": 1})
test({**base, "fieldRank": 1, "page": 2})
test({**base, "fieldRank": 1, "page": 3})
# También ver la segunda pagina sin fieldRank
test({**base, "page": 2})
test({**base, "page": 3})

# Revisar estructura completa del primer resultado
r = session.get("https://www.amarillas.cl/api/advertisements",
    params={**base, "page": 1}, timeout=20)
r.encoding = "latin-1"
d = r.json()
if d.get("results"):
    item = d["results"][0]
    addr = item.get("mainAddress", {}) or {}
    print("\nCampos de mainAddress:")
    for k, v in addr.items():
        print(f"  {k}: {v}")
    print("\nCampos del item:")
    for k in item.keys():
        print(f"  {k}: {str(item[k])[:80]}")
