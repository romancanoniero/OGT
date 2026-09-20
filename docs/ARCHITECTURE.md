# Only Good Things — arquitectura fundacional

Monorepo modular. La UI Compose se construye en otro repositorio; este repo es el **contrato** (shared + backend + SQL).

## Stack

| Capa | Tecnología |
|---|---|
| Cliente compartido | KMP (`shared`): domain, REST, fachada de sockets |
| Sockets | [`db-kmp-sdk`](https://github.com/romancanoniero/db-kmp-sdk) → [`db-realtime-gateway`](https://github.com/romancanoniero/db-realtime-gateway) |
| Auth | Firebase Authentication (Google, Apple, email/magic link, SMS). Claims: `USER`, `COMPANY_ADMIN`, `COMMUNITY_MODERATOR` |
| API | Ktor 3 (modular, extraíble a microservicios) |
| Datos | PostgreSQL 16 + PostGIS, Redis |
| Web | El SDK de sockets hoy es **JS**, no Wasm. La UI web puede ser Wasm; el transporte de datos usa el target `js` de `shared`. |

## Módulos de dominio

1. Estacionamiento colaborativo — `parking_spots` + `ST_DWithin` + claim con `version`
2. Feed social — `social_posts` polimórfico USER/COMPANY
3. Referidos — `invite_code` + deep link `https://onlygoodthings.app/i/{code}`
4. Animales / voluntariado — listings georreferenciados + geocerca
5. Banco de tiempo — `skills_offered` / `skills_requested` + chat post-match
6. RSE — `campaigns` + `promo_codes`
7. Crowdfunding — `community_causes` + `donations` (gateway de pago desacoplado)

## Tiempo real

```
UI (otro repo)
  → OgtSdk.start(wss://…/db, firebaseIdToken)
  → OgtRealtime.observeParkingSpots() / observeImpactAlerts()
  → OgtRealtime.pushLocationTick(...)
```

Paths (`OgtDbPaths`):

- `ogt/parking/spots/{id}`
- `ogt/parking/ticks/{spotId}/{owner|claimant}`
- `ogt/alerts/{id}`
- `ogt/social/posts/{id}`
- `ogt/presence/{userId}`

Handshake del SDK: mensaje `auth` con JWT. Reconnect, backoff y re-auth ya viven en `RealtimeClient`.

## Confianza

- El cliente **nunca** cierra un claim ni suma puntos.
- `PARKING_CLAIM` / `POST /parking/claim` usa optimistic locking (`version` + `AVAILABLE`).
- Cesión verificada: ambas coordenadas a ≤ 40 m (`parkingProximityMeters`).
- Rate limit Redis en publicación de vacantes.

## Cómo levantar

```bash
docker compose up -d
# tokens de laboratorio: Authorization: Bearer dev.dev-user-ana.USER
./gradlew :backend:run
```
