# Stitch → Compose

Proyecto `EcoAction Social App UI` (`11995265718269939633`).
Tema: `design/stitch/DESIGN.md` (**Quiet Studio**). Logo oficial: `logo-opcion-1/logo.svg`.
La referencia de estilo es un mood board: se adaptan paleta, pills, círculos y aire a los objetos OGT (parking, animales, skills, wallet), no se clona el catálogo demo.

## Convertidas (pantallas únicas)

Una pantalla Compose = un archivo en `composeApp/src/commonMain/kotlin/com/onlygoodthings/app/ui/screens/`.

| Stitch | Compose (`OgtRoute`) | Archivo |
|---|---|---|
| Animated SVG (logo) | `AnimatedSvg` | `AnimatedSvgScreen.kt` |
| Crear Cuenta de Vecino | `SignUp` | `SignUpScreen.kt` |
| Iniciar Sesión | `Login` | `LoginScreen.kt` |
| Verificación OTP | `Otp` | `OtpScreen.kt` |
| Recuperación de Contraseña | `Recover` | `RecoverScreen.kt` |
| Splash móvil / geolocalizado | `Splash` | `SplashScreen.kt` |
| Splash con sponsors (`9fd6b451…`) | `SplashSponsors` | `SplashSponsorsScreen.kt` |
| 4 funcionalidades (`5269ae9e…`) | `OnboardingFeatures` | `OnboardingFeaturesScreen.kt` |
| Onboarding paginado (`f79f5d5a…` + slides) | `Onboarding` | `OnboardingScreen.kt` |
| Permisos transparentes (`5d649deb…`) | `Permissions` | `PermissionsScreen.kt` |
| Diálogo GPS (`753c7d77…`) | `Gps` | `GpsDialogScreen.kt` |
| Verificación identidad (`811e94a9…`) | `Identity` | `IdentityScreen.kt` |
| Feed buenas acciones (`6b7ded9b…`) | `Feed` | `FeedScreen.kt` |
| Detalle + hilo (`7af56167…`) | `PostDetail` | `PostDetailScreen.kt` |
| Publicar buena acción (`923f2f78…`) | `Publish` | `PublishActionScreen.kt` |
| Asistente parking (`c1aa15ce…`) | `Parking` | `ParkingScreen.kt` |
| Confirmación parking (`ba731336…`) | `ParkingConfirm` | `ParkingConfirmScreen.kt` |
| Mapa barrial (`053607f8…`) | `Map` | `MapScreen.kt` |
| Animales y causas (`2e45991a…`) | `Animals` | `AnimalsScreen.kt` |
| Reportar animal (`2f8757ff…`) | `ReportAnimal` | `ReportAnimalScreen.kt` |
| Intercambio habilidades (`ce916428…`) | `Skills` | `SkillsScreen.kt` |
| Proponer intercambio (`d0f168b7…`) | `ProposeSkill` | `ProposeSkillScreen.kt` |
| Mensajes (`d6f6fe7a…`) | `Messages` | `MessagesScreen.kt` |
| Chat (`582daad6…`) | `Chat` | `ChatScreen.kt` |
| Billetera puntos (`d6dcd67b…`) | `Wallet` | `WalletScreen.kt` |
| Ranking (`4936a2dc…`) | `Ranking` | `RankingScreen.kt` |
| Notificaciones (`6b6cfa3a…`) | `Notifications` | `NotificationsScreen.kt` |
| Invitar amigos (`60de6552…`) | `Invite` | `InviteScreen.kt` |
| Configuración (`2b6b52b9…`) | `Settings` | `SettingsScreen.kt` |
| Sponsors RSE (`e3f22db9…`) | `Sponsors` | `SponsorsScreen.kt` |
| Hito / nivel (`41dd8c7f…`) | `Milestone` | `MilestoneScreen.kt` |

Fotos Stitch `172a0084…` y `23665364…`: assets en `design/stitch/social/`. Clip-art puntos `33857d97…`: `design/stitch/onboard/clip-puntos.svg`.

## No son pantallas de producto (assets)

Fotos de vecinos, avatares, clip-art 100×100 y **variantes Pastel** (`1fb7deb0…`, `3d58e82f…`, `5f1c29bd…`, `1eaf2112…`): se reutiliza la pantalla canónica + tokens Quiet Studio. Design System: `DESIGN.md`.
