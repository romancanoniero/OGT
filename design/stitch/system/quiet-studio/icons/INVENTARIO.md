# Inventario de iconos de producto

Criterio pedido: **clay 3D Quiet Studio** (Harbor Navy `#0D3D4D` + gris mate) sobre **fondo blanco**, sin texto. No cuenta foto de seed, avatar ni captura de pantalla.

Leyenda: **OK** cumple · **FONDO** es 3D pero el lienzo no es blanco · **2D** no es clay 3D.

## Tiles 3D (héroes / alegorías)

| Asset | Uso | Estilo | Fondo | Estado |
|---|---|---|---|---|
| `parking_action_parked` | Estacioné, silueta auto | Clay 3D | Blanco | OK |
| `parking_action_yield` | Ceder | Clay 3D | Blanco | OK |
| `parking_action_search` | Buscar lugar | Clay 3D | Blanco (unificado) | OK |
| `parking_action_find` | Encontrar el auto | Clay 3D | Blanco (unificado) | OK |
| `parking_sign_plate` | Señal P de parking | Clay 3D | Blanco | OK |
| `anecdote_action` | Anécdota / relato en homenaje | Clay 3D | Transparente (PNG alfa) | OK |
| `publish_action_add` | Placeholder de media | Clay 3D | Transparente (PNG alfa) | OK |
| `onboard_feed` | Onboarding | Flat pastel | Transparente | 2D |
| `onboard_parking` | Onboarding | Flat pastel | Transparente | 2D |
| `onboard_pets` | Onboarding | Flat pastel | Transparente | 2D |
| `onboard_puntos` | Onboarding | Flat pastel | Transparente | 2D |
| `onboard_skills` | Onboarding | Flat pastel | Transparente | 2D |
| `gps_scope_allegory` | Diálogo de alcance GPS | Flat menta | Cream | 2D |
| `parking_maneuver_allegory` | Maniobra | Ilustración / foto | Color | 2D |
| `ogt_logo` / `feed_logo` | Marca | Logo 2D | Transparente | marca, no tile |

Hoja de referencia 3D (parking): `qs-3d-sheet.png`.

## Chrome Quiet Studio (glifos 20 px, se tiñen)

Hoy son **SVG line-art** (`qs_*.xml`). No son 3D. En botones de 44 px `OgtStitchIcon` les aplica `ColorFilter.tint`: un PNG 3D blanco no puede entrar ahí sin cambiar el componente.

| Asset | Significado | Dónde se ve |
|---|---|---|
| `qs_feed` | Feed / buena acción | Dock, publicar |
| `qs_search` | Pin / buscar lugar | Perdido, honor, origen GPS |
| `qs_lupa` | Lupa / buscar | Nav Buscar, campos de búsqueda |
| `qs_parking` | Placa P de estacionamiento | Dock parking, pin de plaza |
| `qs_parked` | Auto de perfil | Pin de vehículo |
| `qs_paw` | Animales / perdido | Dock, fichas |
| `qs_skills` | Ayuda | Dock, pines |
| `qs_wallet` | Puntos / ajustes | Dock |
| `qs_plus` | Agregar | Publicar, media |
| `qs_close` | Cerrar / quitar | Viewer, media |
| `qs_back` | Volver | Top bar |
| `qs_notifications` | Alertas | Header |
| `qs_clap` | Aplauso | Feed, detalle |
| `qs_heart` | Ternura | Detalle |
| `qs_comment` | Comentarios | Acciones, mapa |
| `qs_share` | Compartir | Acciones |
| `qs_send` | Enviar | Chat, comentario |
| `qs_invite` | Invitar / homenaje | Extras, fichas |
| `qs_map` | Mapa mascotas | Extra perdido |
| `qs_gps` | Pin / avistaje | Mapa, mensajes |
| `qs_yield` | Ceder | Pin fallback |
| `qs_adopt` | Adopción | Dock publicar |
| `qs_attend` | Convocatoria | Dock publicar |
| `qs_pets` | Ternura | Fichas |
| `qs_claim` | Reclamar | Parking |
| `qs_form` | Formulario | Chat, mensajes |
| `qs_anecdote` | Anécdota (papelito + comillas) | Homenaje, feed |
| `qs_eye` | Lo vio | Mapa, avistaje |
| `qs_expand` | Ampliar / play | Media, mapa |

**27 / 27 son 2D.** Hace falta un tile 3D por significado (fondo blanco) y un camino de render **sin tint** para no pintarlos de tinta. `anecdote_action` ya cubre el tile 3D de anécdota.

## Fuera de inventario (no son iconos)

Fotos `seed_*`, `feed_photo_*`, `feed_story_*`, `feed_avatar_*`, `publish_photo`, `publish_map`.
