# Only Good Things

Plataforma social de impacto positivo. Este repo es el **núcleo** (contrato KMP, API, SQL). Las pantallas Compose se construyen en otro repositorio.

## Qué hay acá

- `shared` — dominio, REST y fachada `OgtSdk` / `OgtRealtime` sobre **db-kmp-sdk**
- `backend` — Ktor: Firebase, PostGIS, módulos 1–7
- `database/migrations` — PostgreSQL + PostGIS
- `docs/` — arquitectura, protocolo WS, Postman

## Boot del cliente (para el repo de UI)

```kotlin
OgtSdk.start(
    gatewayEndpoint = "wss://db.tudominio.com/db",
    tokenProvider = { forceRefresh -> firebaseAuth.getIdToken(forceRefresh) },
)
val live = OgtRealtime()
live.observeParkingSpots().collect { spot -> /* pintar pin */ }
```

Tokens de laboratorio (sin Firebase): `dev.dev-user-ana.USER`

## Levantar backend

```bash
docker compose up -d
./gradlew :backend:run
```

Colección Postman: `docs/postman/OnlyGoodThings.postman_collection.json`
