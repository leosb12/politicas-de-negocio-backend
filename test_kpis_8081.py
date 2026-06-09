import requests
import json

url = "http://localhost:8081/api/admin/reportes-visuales/generar"
headers = {
    "Content-Type": "application/json",
    "X-Admin-User-Id": "69e5c510738e44073b70b010"
}

payload_politicas = {
    "prompt": "total de politicas",
    "usuarioId": "69e5c510738e44073b70b010",
    "iaPlus": False
}

payload_tramites = {
    "prompt": "total de tramites",
    "usuarioId": "69e5c510738e44073b70b010",
    "iaPlus": False
}

print("Testing 'total de politicas':")
try:
    response = requests.post(url, json=payload_politicas, headers=headers, timeout=15)
    print("Status:", response.status_code)
    res_data = response.json()
    for bloque in res_data.get("bloques", []):
        print(f"Block: {bloque.get('titulo')} (tipo: {bloque.get('tipo')})")
        print("Dataset:", json.dumps(bloque.get("datos"), indent=2))
except Exception as e:
    print("Error:", e)

print("\nTesting 'total de tramites':")
try:
    response = requests.post(url, json=payload_tramites, headers=headers, timeout=15)
    print("Status:", response.status_code)
    res_data = response.json()
    for bloque in res_data.get("bloques", []):
        print(f"Block: {bloque.get('titulo')} (tipo: {bloque.get('tipo')})")
        print("Dataset:", json.dumps(bloque.get("datos"), indent=2))
except Exception as e:
    print("Error:", e)
