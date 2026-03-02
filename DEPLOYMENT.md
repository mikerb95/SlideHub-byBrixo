# DEPLOYMENT.md — Guía de Despliegue SlideHub en Render

## Requisitos previos

- ✅ Dominio personalizado: `slide.lat` (comprado en Namecheap)
- ✅ Cuenta en **Render** (render.com)
- ✅ Cuenta en **Aiven** para PostgreSQL
- ✅ Cuenta en **MongoDB Atlas** para MongoDB
- ✅ Cuentas de IA: **Gemini API** + **Groq API**
- ✅ Cuentas sociales: **GitHub OAuth**, **Google OAuth**
- ✅ **AWS S3** para almacenamiento de assets
- ✅ **Resend** para envío de emails (opcional)

---

## Opción A: Despliegue Automático (Recomendado)

### Paso 1: Preparar el repositorio

```bash
# Sube el código a GitHub
git add .
git commit -m "Add Dockerfiles and render.yaml"
git push origin main
```

### Paso 2: Crear desde Blueprint en Render

1. Ve a **Render Dashboard** → **Blueprints**
2. Click **New from Blueprint**
3. Pega la URL de tu repo: `https://github.com/tu-usuario/SlideHub.git`
4. Render detectará automáticamente el `render.yaml`
5. Rellena los valores que te pide (GITHUB_CLIENT_ID, etc.)
6. Click **Deploy**

Render desplegará automáticamente los 4 servicios y los vinculará internamente.

---

## Opción B: Despliegue Manual (Más control)

### Paso 1: Crear 4 Web Services en Render

Para cada servicio (gateway, state, ui, ai):

1. **Render Dashboard** → **+ New** → **Web Service**
2. Conecta tu repo GitHub
3. **Name**: `slidehub-gateway` (ajusta por servicio)
4. **Environment**: Docker
5. **Region**: Oregon (u otra de tu preferencia)
6. **Branch**: main
7. **Build Command**: (dejar vacío — usa Dockerfile)
8. **Start Command**: (dejar vacío — usa Dockerfile)
9. Click **Create Web Service**

### Paso 2: Agregar variables de entorno

**En cada servicio**, ve a **Settings** → **Environment**:

**slidehub-gateway:**
```
SPRING_PROFILES_ACTIVE=prod
STATE_SERVICE_URL=https://slidehub-state.onrender.com
UI_SERVICE_URL=https://slidehub-ui.onrender.com
AI_SERVICE_URL=https://slidehub-ai.onrender.com
```

**slidehub-state:**
```
SPRING_PROFILES_ACTIVE=prod
REDIS_HOST=(obtén de tu proveedor Redis o Render Redis)
REDIS_PORT=6379
```

**slidehub-ui:**
```
SPRING_PROFILES_ACTIVE=prod
STATE_SERVICE_URL=https://slidehub-state.onrender.com
AI_SERVICE_URL=https://slidehub-ai.onrender.com
BASE_URL=https://slide.lat
DATABASE_URL=(DSN completo de Aiven con ?sslmode=require)
DB_DRIVER=org.postgresql.Driver
JPA_DIALECT=org.hibernate.dialect.PostgreSQLDialect
GITHUB_CLIENT_ID=(de GitHub Developer Settings)
GITHUB_CLIENT_SECRET=(de GitHub Developer Settings)
GOOGLE_CLIENT_ID=(de Google Cloud Console)
GOOGLE_CLIENT_SECRET=(de Google Cloud Console)
RESEND_API_KEY=(de Resend)
AWS_ACCESS_KEY_ID=(de AWS IAM)
AWS_SECRET_ACCESS_KEY=(de AWS IAM)
AWS_S3_BUCKET=slidehub-assets
AWS_REGION=us-east-1
```

**slidehub-ai:**
```
SPRING_PROFILES_ACTIVE=prod
MONGODB_URI=(connection string de MongoDB Atlas con ?retryWrites=true)
GEMINI_API_KEY=(de Google AI Studio)
GROQ_API_KEY=(de Groq console)
GROQ_MODEL=llama3-8b-8192
```

### Paso 3: Configurar dominio personalizado

En **slidehub-gateway** → **Settings** → **Custom Domains**:
- Click **Add Custom Domain**
- Introduce `slide.lat`
- Render genera un CNAME

En **Namecheap**:
- Advanced DNS → Host Records
- Crea un registro: `@ CNAME slidehub-gateway.onrender.com`
- Aguarda propagación DNS (5 min - 48 horas)

Verifica con: `nslookup slide.lat`

### Paso 4: Actualizar OAuth2 callbacks

**GitHub** (github.com/settings/developers → edit OAuth App):
```
Authorization callback URL:
  https://slide.lat/login/oauth2/code/github
```

**Google** (cloud.google.com → Credentials → edit OAuth 2.0):
```
Authorized redirect URIs:
  https://slide.lat/login/oauth2/code/google
```

### Paso 5: Configurar dominio verificado en Resend (opcional)

Si usas Resend para emails:
1. Resend → Domains → **Add Domain** → `slide.lat`
2. Resend te da registros SPF, DKIM, DMARC
3. En Namecheap → Advanced DNS → agrega los 3 registros TXT
4. Cuando Resend verifique, actualiza: `slidehub.resend.from=noreply@slide.lat`

---

## Monitoreo después del despliegue

### Health Checks

Cada servicio expone `/actuator/health`:
- `https://slide.lat/actuator/health` (gateway)
- `https://slidehub-state.onrender.com/actuator/health` (state)
- `https://slidehub-ui.onrender.com/actuator/health` (ui)
- `https://slidehub-ai.onrender.com/actuator/health` (ai)

### Logs

En **Render Dashboard** → cada servicio → **Logs**:
- Ver errores de compilación y runtime
- Busca `ERROR` o `Exception`

### Redeploy manual

Si necesitas recompilar después de cambios:
- Ve al servicio → **Manual Deploy** → **Deploy latest commit**

---

## Solución de problemas

### Error: "Build command exited with status 1"

**Causa:** Maven build falló
**Solución:** 
```bash
# Compila localmente para verificar
./mvnw clean compile -pl gateway-service -am
```

Si falla localmente, falla también en Render.

### Error: "Connection refused: state-service"

**Causa:** Las URLs internas están mal configuradas
**Solución:** Verifica que `STATE_SERVICE_URL` = `https://slidehub-state.onrender.com` (no `localhost`)

### PostgreSQL timeout

**Causa:** `DATABASE_URL` está mal
**Solución:** Verifica el DSN de Aiven: debe incluir `?sslmode=require`

### MongoDB connection timeout

**Causa:** Connection string de Atlas está incompleta
**Solución:** Usa format: `mongodb+srv://user:pass@cluster.mongodb.net/slidehub?retryWrites=true`

---

## Rollback

Si algo sale mal y quieres desplegar una versión anterior:

1. En Render → servicio → **Deployments** → selecciona una anterior
2. Click **Redeploy**

---

## Próximos pasos

1. ✅ Desplegar gate service
2. ✅ Verificar que el dominio resuelva a `slide.lat`
3. ✅ Probar login local en `https://slide.lat/auth/login`
4. ✅ Probar OAuth2 (GitHub/Google)
5. ✅ Probar upload de slides a S3
6. ✅ Verificar generación de notas con Gemini + Groq
