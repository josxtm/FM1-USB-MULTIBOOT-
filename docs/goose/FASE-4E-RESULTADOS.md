# FASE 4E — Biblioteca Android y Cover Flow

**Rama:** `goose/fase3b-4a-preflight`  
**PR:** #8 (Draft)  
**Commit:** `0c093cf`  
**Build:** `FM1_RAM_Loader_v1.0_comp12.apk` / `versionCode 12`

## Cambios

- `FirmwareAssessment` separa metadatos válidos, hash registrado, SHA-256 calculado, tamaño real, integridad verificada y compatibilidad.
- `FirmwareAssetVerifier` lee mediante Storage Access Framework con límite de 4 MiB y compara los bytes reales contra `INDEX.CSV`.
- Se distinguen archivos válidos, corruptos, ausentes y hashes incorrectos; los originales no se modifican.
- `SdLibraryActivity` abre `FM1/INDEX.CSV`, lee múltiples entradas, verifica `FM1/FW/`, conecta el resultado a `CoverFlowModel` y muestra navegación, tamaño, integridad, compatibilidad y advertencias.
- PLAY solo entra en `INSPECT`; nunca ejecuta firmware. El modelo recibe la evaluación calculada del archivo SAF, no una afirmación basada únicamente en metadatos.
- Los informes MIDI usan `Instant.now().toString()` en UTC ISO-8601 y conservan la versión real de la APK sin datos sensibles.
- Las pruebas cubren contenido válido, corrupción, hash incorrecto, archivo ausente, catálogo múltiple/vacío, estados Cover Flow y exportación de informes.

## Coherencia

Android y el simulador consumen FM1CAT v1, con las mismas cabeceras, nombres 8.3, límites, SHA-256 y estado `UNVERIFIED`. Android además puede leer y verificar los archivos mediante SAF; el simulador solo inspecciona el índice del navegador. El selector Android es una biblioteca de inspección y no es el menú instalado del FM-1; el futuro selector embebido requerirá evidencia y validación independiente.

## Validación reproducible

- Android/Kotlin, Lint, APK comp12 y pruebas unitarias: ✅ [run 44961404931](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44961404931)
- Diagnóstico RAM/pi32v2, USB/FAT32 y coherencia APP1: ✅ [run 44961407752](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44961407752)
- Simulador Cover Flow: ✅ [run 44961410558](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44961410558)
- `make test` local: ✅ pruebas USB/FAT32 host.
- En el entorno local no están instalados Java ni Node; las pruebas Android y del simulador se ejecutaron en CI, no se presentan como ejecuciones locales.

## Seguridad

La integridad no implica compatibilidad ni ejecutabilidad. RAM permanece en simulación; XIP no tiene evidencia; Flash, `0x25`, `0x30`, USB Host físico y escritura siguen bloqueados. La única consulta física del módulo m-bootloader continúa siendo `0x12`. DARK-1, bootloader y `[0x00000,0x04000)` permanecen intactos.
