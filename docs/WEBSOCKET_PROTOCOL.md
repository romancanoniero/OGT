# Protocolo de tiempo real

Hay **dos capas**. La oficial de producto es la de `db-kmp-sdk`. Los envelopes `CONNECT_AUTH` / `PARKING_*` son el contrato de dominio que el backend también puede proyectar.

## Capa A — db-kmp-sdk (usar desde la UI)

Wire JSON, discriminador `"t"`. Cliente → servidor:

| `t` | Uso OGT |
|---|---|
| `auth` | Firebase JWT (`CONNECT_AUTH`) |
| `listen` | Feed de vacantes, alertas, posts |
| `set` / `update` | `LOCATION_TICK` en `ogt/parking/ticks/{spotId}/{role}` |
| `get` | Lectura one-shot |

Servidor → cliente: `value`, `child_added|changed|removed`, `ack`, `error`.

Reconexión: backoff 500 ms → 15 s + jitter. Heartbeat/ping lo cubre Ktor + `forceReconnect()` al volver a foreground.

## Capa B — eventos de dominio (proyección)

Payloads en `shared/.../protocol/frames`:

- `PARKING_BROADCAST` → nodo `ogt/parking/spots/{spotId}`
- `PARKING_CLAIM` → update del mismo nodo (`status=CLAIMED`, `version++`)
- `LOCATION_TICK` → `ogt/parking/ticks/...`
- `IMPACT_ALERT` → `ogt/alerts/{alertId}`

## Protobuf (mismo esquema, binario opcional)

Ver `docs/ogt_realtime.proto`. JSON es el default; Protobuf se habilita cuando el gateway lo negocie.
