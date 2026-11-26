# Generación completa de iOS — 26/11/2026

La compilación de `compileKotlinIosSimulatorArm64` comprueba código Kotlin, pero no genera por sí sola la aplicación Swift enlazada, firmada y empaquetada. Esta validación utiliza Xcode 26.2 y sus SDK 26.2 para generar ambos productos completos.

## Bloqueos corregidos

- El framework `ComposeApp` ya exportaba `:shared`, pero la dependencia estaba declarada como `implementation`. Kotlin/Native exige `api` para un módulo exportado. Se conserva la exportación y se corrige la declaración, sin cambiar módulos ni su código funcional.
- El optimizador Release agotaba el heap Gradle de 4 GB. La fase de Xcode usa 8 GB y dos workers **solamente en Release**. Puede sobrescribirse con `OGT_IOS_GRADLE_HEAP`. Debug y las compilaciones Android conservan su configuración habitual. No se desactivan optimizaciones.

## Productos verificados

- `build/ios-executable-validation/OGT.xcarchive`: archivo Release completo para iPhone ARM64.
- `build/ios-executable-validation/export/iosApp.ipa`: exportación local de desarrollo firmada; utiliza el perfil existente y sus dispositivos autorizados.
- `build/ios-executable-validation/Build/Products/Debug-iphonesimulator/iosApp.app`: aplicación Debug completa para simulador ARM64.

Los tres productos corresponden a `com.onlygoodthings.app`, versión 1.0, build 15, destino mínimo iOS 16.0. No se cambia su versión, identidad, certificados o permisos. No se instala ni se publica en Apple.

La validación comprueba el tipo ejecutable Mach-O y plataforma correcta, arquitectura ARM64, 143 recursos de Compose incluido el logo del feed, Assets.car, configuración Firebase válida y manifiesto de privacidad conservado. En iPhone verifica firma profunda/estricta, perfil vigente, identificador, equipo, entorno push y dominios asociados. El IPA pasa integridad ZIP y se verifica otra vez después de extraerlo. El UUID del ejecutable Release coincide con sus dSYM.

Se vuelve a comprobar `assembleDebug` y `testDebugUnitTest` de Android: compilación correcta; las 135 pruebas existentes mantienen un resultado válido sin fallos (la tarea de ejecución se reutiliza por entradas sin cambios).

Esta evidencia verifica generación, enlace, empaquetado y firma; no reemplaza una prueba funcional en un iPhone. El Pixel permanece sin modificar.

## Repetir la generación

Desde la raíz del proyecto, con el SDK privado y la configuración Firebase iOS existentes:

```sh
OGT_DB_KMP_SDK_PATH="$HOME/AndroidStudioProjects/db-kmp-sdk" xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' -derivedDataPath build/ios-executable-validation -archivePath build/ios-executable-validation/OGT.xcarchive -disableAutomaticPackageResolution -onlyUsePackageVersionsFromResolvedFile archive
```

Los resultados detallados y hashes se guardan localmente en `qa/artifacts/ios-executable-verification.json`; ese artefacto está excluido de Git. El archivo y el IPA se generan en `build/`, también excluido.

Referencias primarias: [exportar dependencias Kotlin/Native](https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html#export-dependencies-to-binaries) y [memoria de compilación nativa](https://kotlinlang.org/docs/native-improving-compilation-time.html#increase-gradle-heap-size).
