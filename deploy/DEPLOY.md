# Guía de Despliegue — AWS EC2 (sa-east-1 · São Paulo)

**Instancia**: `t3a.xlarge` | 16 GiB RAM | 4 vCPU | 80 GB gp3 | Ubuntu 22.04 LTS

---

## Estructura esperada en el EC2

```
/home/ubuntu/
  politicas-de-negocio/       ← Backend (este repositorio)
    deploy/
      docker-compose.yml
      nginx/nginx.conf
      .env.production.example
      .env                     ← Creado a mano, NO en git
  ia-service/                  ← Repositorio ia-service
  ia-deep-learning-service/    ← Repositorio ia-deep-learning
  politicas-negocio-web/       ← Repositorio frontend
```

> **Importante**: El `docker-compose.yml` usa rutas relativas a los repositorios clonados.
> Todos los repos deben estar en el mismo directorio padre (p.ej. `/home/ubuntu/`).

---

## Paso 1 — Preparar el EC2

### 1.1 Conectarse a la instancia

```bash
# Desde tu máquina local (Windows/macOS/Linux)
ssh -i mi-clave-aws.pem ubuntu@TU-IP-EC2
```

### 1.2 Actualizar el sistema

```bash
sudo apt-get update && sudo apt-get upgrade -y
sudo apt-get install -y git curl wget unzip htop
```

### 1.3 Instalar Docker

```bash
# Instalar dependencias
sudo apt-get install -y ca-certificates curl gnupg lsb-release

# Agregar clave GPG de Docker
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Agregar repositorio de Docker
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Instalar Docker Engine + Compose plugin
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin

# Agregar usuario ubuntu al grupo docker (sin sudo)
sudo usermod -aG docker ubuntu
newgrp docker

# Verificar instalación
docker --version
docker compose version
```

---

## Paso 2 — Clonar los repositorios

```bash
cd /home/ubuntu

# Backend (repositorio principal con el deploy/)
git clone https://github.com/TU-USUARIO/politicas-de-negocio.git

# Frontend Angular
git clone https://github.com/TU-USUARIO/politicas-negocio-web.git

# Microservicio IA General
git clone https://github.com/TU-USUARIO/ia-service.git

# Microservicio IA Deep Learning
git clone https://github.com/TU-USUARIO/ia-deep-learning-service.git
```

---

## Paso 3 — Configurar variables de entorno

```bash
cd /home/ubuntu/politicas-de-negocio/deploy

# Copiar plantilla
cp .env.production.example .env

# Editar con los valores reales
nano .env
```

### Variables mínimas obligatorias a completar:

```bash
MONGODB_URI=mongodb://USUARIO:PASS@host1:27017,...?tls=true&...
DEEPSEEK_API_KEY=sk-...
SPRING_MAIL_USERNAME=tu-email@gmail.com
SPRING_MAIL_PASSWORD=xxxx-xxxx-xxxx-xxxx   # App Password de Gmail
S3_BUCKET=nombre-del-bucket
AWS_ACCESS_KEY_ID=AKIA...                  # O usar IAM Role (dejar vacío)
AWS_SECRET_ACCESS_KEY=...                  # O usar IAM Role (dejar vacío)
APP_PUBLIC_BASE_URL=http://TU-IP-EC2
APP_FRONTEND_URL=http://TU-IP-EC2
APP_CORS_ALLOWED_ORIGINS=http://TU-IP-EC2
ONLYOFFICE_CALLBACK_BASE_URL=http://TU-IP-EC2/api

# Firebase Web (si se usan notificaciones push en el frontend)
FIREBASE_WEB_API_KEY=AIzaSy...
FIREBASE_WEB_AUTH_DOMAIN=...
# etc.
```

---

## Paso 4 — Construir y levantar los servicios

### 4.1 Construir todas las imágenes

```bash
cd /home/ubuntu/politicas-de-negocio/deploy

# Construir todas las imágenes (puede tomar 10-20 minutos la primera vez)
docker compose build

# Construir un servicio específico (si necesitas rebuild parcial)
docker compose build backend
docker compose build frontend
docker compose build ia-service
docker compose build ia-deep-learning-service
```

### 4.2 Levantar en el orden correcto

```bash
# Opción A: Levantar todo de una vez (depends_on gestiona el orden)
docker compose up -d

# Opción B: Orden manual explícito (más control para debugging)
docker compose up -d onlyoffice
sleep 10
docker compose up -d ia-service
docker compose up -d ia-deep-learning-service
sleep 5
docker compose up -d backend
sleep 10
docker compose up -d frontend
docker compose up -d nginx
```

### 4.3 Verificar que todos los servicios estén corriendo

```bash
docker compose ps
```

Salida esperada (todos deben estar `Up`):
```
NAME                        IMAGE                          STATUS
backend                     politicas-backend:prod         Up
frontend                    politicas-frontend:prod        Up
ia-deep-learning-service    politicas-ia-dl-service:prod   Up
ia-service                  politicas-ia-service:prod      Up
nginx                       nginx:1.27-alpine              Up
onlyoffice                  onlyoffice/documentserver      Up
```

---

## Paso 5 — Verificar el despliegue

### 5.1 Health checks individuales

```bash
# Backend Spring Boot (desde dentro del EC2)
curl -s http://localhost:8080/api/actuator/health | python3 -m json.tool
# Esperado: {"status":"UP"}

# IA General FastAPI
curl -s http://localhost:8000/health | python3 -m json.tool
# Esperado: {"status":"ok","service":"ia-service"}

# IA Deep Learning FastAPI
curl -s http://localhost:8010/health | python3 -m json.tool
# Esperado: {"status":"ok"}

# OnlyOffice (healthcheck interno)
curl -s http://localhost/onlyoffice/healthcheck
# Esperado: true

# Frontend vía Nginx (acceso público)
curl -s http://TU-IP-EC2/ | grep -o '<title>.*</title>'
```

### 5.2 Comunicación entre contenedores (desde dentro de la red Docker)

```bash
# Backend → IA General
docker compose exec backend curl -s http://ia-service:8000/health

# Backend → IA Deep Learning
docker compose exec backend curl -s http://ia-deep-learning-service:8010/health

# Backend → OnlyOffice
docker compose exec backend curl -s http://onlyoffice:80/healthcheck

# IA General → IA Deep Learning
docker compose exec ia-service curl -s http://ia-deep-learning-service:8010/health
```

### 5.3 Acceso público

```bash
# Abrir en el navegador:
# http://TU-IP-EC2/           → Frontend Angular
# http://TU-IP-EC2/api/       → Backend API
# http://TU-IP-EC2/onlyoffice/ → OnlyOffice
# http://TU-IP-EC2/ia/        → IA General (docs Swagger)
# http://TU-IP-EC2/ia-dl/     → IA Deep Learning (docs Swagger)
```

---

## Paso 6 — Comandos de monitoreo

```bash
# Ver logs de todos los servicios en tiempo real
docker compose logs -f

# Logs de un servicio específico
docker compose logs -f backend
docker compose logs -f ia-deep-learning-service
docker compose logs -f nginx

# Ver últimas 100 líneas
docker compose logs --tail=100 backend

# Uso de recursos de todos los contenedores
docker stats

# Uso de memoria del sistema
free -h

# Uso de disco
df -h

# Verificar espacio de imágenes Docker
docker system df
```

---

## Paso 7 — Operaciones comunes

### Reiniciar un servicio

```bash
docker compose restart backend
docker compose restart nginx
```

### Actualizar código y redeploy

```bash
cd /home/ubuntu/politicas-de-negocio
git pull origin main

cd deploy
docker compose build backend
docker compose up -d backend
```

### Parar todo el sistema

```bash
docker compose down
```

### Parar y eliminar volúmenes (⚠️ DESTRUCTIVO — elimina datos OnlyOffice y modelos DL)

```bash
docker compose down -v
```

### Limpiar imágenes no usadas

```bash
docker image prune -f
docker system prune -f
```

---

## Paso 8 — HTTPS con Certbot (paso posterior)

> Este paso es opcional para la presentación. Realizarlo cuando tengas un dominio configurado.

```bash
# Instalar Certbot
sudo apt-get install -y certbot

# Obtener certificado (detener nginx temporalmente)
docker compose stop nginx
sudo certbot certonly --standalone -d TU-DOMINIO.com

# Descomentar líneas HTTPS en nginx.conf y docker-compose.yml
# Levantar nginx nuevamente
docker compose up -d nginx
```

---

## Checklist Final — ¿Está todo funcionando?

### ✅ Infraestructura

- [ ] `docker compose ps` muestra todos los contenedores `Up`
- [ ] No hay reinicios inesperados (`Restarting`)
- [ ] `free -h` muestra al menos 2–3 GiB libres
- [ ] `df -h` muestra al menos 20 GiB libres en disco

### ✅ Backend

- [ ] `GET /api/actuator/health` retorna `{"status":"UP"}`
- [ ] El backend se conecta a MongoDB Atlas (logs sin errores de conexión)
- [ ] El backend puede subir archivos a S3 (si `APP_STORAGE_TYPE=s3`)
- [ ] El backend puede enviar emails (probar con endpoint de notificaciones)

### ✅ Microservicios IA

- [ ] `GET /health` en IA General retorna `{"status":"ok"}`
- [ ] `GET /health` en IA Deep Learning retorna `{"status":"ok"}`
- [ ] El backend puede llamar al IA General via red interna Docker
- [ ] El backend puede llamar al IA Deep Learning via red interna Docker

### ✅ Frontend

- [ ] El frontend carga en `http://TU-IP-EC2/`
- [ ] El login funciona (comunicación frontend → backend → MongoDB)
- [ ] Las peticiones API van a `/api/` sin errores CORS
- [ ] `runtime-config.js` tiene las variables correctas (abrir en el navegador)

### ✅ OnlyOffice

- [ ] `GET /onlyoffice/healthcheck` retorna `true`
- [ ] Se puede abrir un documento en el editor
- [ ] Los cambios se guardan correctamente (callback al backend)

### ✅ Seguridad

- [ ] No hay credenciales en `application.properties`
- [ ] El archivo `.env` real NO está en el repositorio git
- [ ] Las API keys están solo en el `.env` del servidor
- [ ] Los Security Groups de EC2 solo tienen abiertos los puertos 80 y 443 (y 22 para SSH)

---

## Troubleshooting común

### "Cannot connect to MongoDB"
- Verificar que `MONGODB_URI` en `.env` es la URI directa `mongodb://` (no `+srv`)
- Verificar que la IP del EC2 está en el whitelist de MongoDB Atlas Network Access

### "ia-service no responde"
- `docker compose logs ia-service` para ver errores
- Verificar que el puerto 8000 está correcto en el Dockerfile

### "OnlyOffice no carga los documentos"
- `ONLYOFFICE_CALLBACK_BASE_URL` debe ser la IP pública del EC2, no `backend:8080`
- OnlyOffice necesita acceder externamente a los documentos para procesarlos

### "TensorFlow tarda en iniciar"
- Normal. El primer arranque de `ia-deep-learning-service` puede tomar 60–120 segundos
- Usar `docker compose logs -f ia-deep-learning-service` para monitorear

### "No hay espacio en disco"
```bash
docker system prune -f        # Limpiar capas no usadas
docker image prune -a -f      # Eliminar imágenes no usadas
```
