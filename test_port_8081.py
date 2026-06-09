import requests
import json

url = "http://localhost:8081/api/admin/reportes-visuales/generar"
headers = {
    "Content-Type": "application/json",
    "X-Admin-User-Id": "69e5c510738e44073b70b010"
}

payload_mayo = {
    "prompt": "qué clientes iniciaron más políticas en el mes de mayo en pantalla",
    "usuarioId": "69e5c510738e44073b70b010",
    "iaPlus": False
}

payload_abril = {
    "prompt": "qué clientes iniciaron más políticas en el mes de abril en pantalla",
    "usuarioId": "69e5c510738e44073b70b010",
    "iaPlus": False
}

print("Testing Mayo prompt against port 8081:")
try:
    response = requests.post(url, json=payload_mayo, headers=headers, timeout=15)
    print("Status:", response.status_code)
    res_data = response.json()
    for bloque in res_data.get("bloques", []):
        print(f"Block: {bloque.get('titulo')}")
        print("Dataset rows count:", len(bloque.get("datos", {}).get("rows", [])))
except Exception as e:
    print("Error:", e)

print("\nTesting Abril prompt against port 8081:")
try:
    response = requests.post(url, json=payload_abril, headers=headers, timeout=15)
    print("Status:", response.status_code)
    res_data = response.json()
    for bloque in res_data.get("bloques", []):
        print(f"Block: {bloque.get('titulo')}")
        print("Dataset rows:", json.dumps(bloque.get("datos", {}).get("rows", []), indent=2))
except Exception as e:
    print("Error:", e)
