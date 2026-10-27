# Only Good Things

Plataforma social de impacto positivo. Este monorepositorio contiene el contrato KMP, las aplicaciones móviles, la web, los CRM, la API y SQL.

## Qué hay acá

- `shared` — dominio, REST y fachada `OgtSdk` / `OgtRealtime` sobre **db-kmp-sdk**
- `backend` — Ktor: Firebase, PostGIS, módulos 1–7
- `composeApp` — interfaz compartida Compose para Android e iOS
- `iosApp` — host nativo, autenticación y herramientas de video de iOS
- `web/app` — web pública, Control y portal de empresas
- `database/migrations` — PostgreSQL + PostGIS
- `qa` — pruebas y servicios locales aislados; no usa cuentas de producción
- `scripts` — verificación, empaquetado y despliegue
- `docs/` — arquitectura, protocolo WS, Postman

## Boot del cliente

```kotlin
OgtSdk.start(
    gatewayEndpoint = "wss://db.tudominio.com/db",
    tokenProvider = { forceRefresh -> firebaseAuth.getIdToken(forceRefresh) },
)
val live = OgtRealtime()
live.observeParkingSpots().collect { spot -> /* pintar pin */ }
```

Los tokens de laboratorio (por ejemplo, `dev.dev-user-ana.USER`) requieren habilitación explícita en un entorno local. No se admiten en producción. La QA integral utiliza Firebase Auth emulado con tokens de laboratorio deshabilitados.

## Levantar backend

```bash
docker compose up -d
./gradlew :backend:run
```

Colección Postman: `docs/postman/OnlyGoodThings.postman_collection.json`
