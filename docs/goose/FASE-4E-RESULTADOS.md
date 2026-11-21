# FASE 4E — Biblioteca Android y Cover Flow

**Rama:** `goose/fase3b-4a-preflight`  
**PR:** #8 (Draft)  
**Build:** `FM1_RAM_Loader_v1.0_comp12.apk` / `versionCode 12`

## Cambios

- `FirmwareAssessment` separa metadatos válidos, hash registrado, SHA-256 calculado, tamaño real, integridad verificada y compatibilidad.
- `FirmwareAssetVerifier` lee mediante Storage Access Framework con límite de 4 MiB y compara los bytes reales contra `INDEX.CSV`.
- Se distinguen archivos válidos, corruptos, ausentes y hashes incorrectos; los originales no se modifican.
- `SdLibraryActivity` abre `FM1/INDEX.CSV`, lee múltiples entradas, verifica `FM1/FW/`, conecta el resultado a `CoverFlowModel` y muestra navegación, tamaño, integridad, compatibilidad y advertencias.
- PLAY solo entra en `INSPECT`; nunca ejecuta firmware.
- Los informes MIDI usan `Instant.now().toString()` en UTC ISO-8601 y conservan la versión real de la APK sin datos sensibles.
- Las pruebas cubren contenido válido, corrupción, hash incorrecto, archivo ausente, catálogo múltiple/vacío, estados Cover Flow y exportación de informes.

## Coherencia

Android y el simulador consumen FM1CAT v1, con las mismas cabeceras, nombres 8.3, límites, SHA-256 y estado `UNVERIFIED`. Android además puede leer y verificar los archivos mediante SAF; el simulador solo inspecciona el índice del navegador. Ninguno es el menú instalado del FM-1.

## Seguridad

La integridad no implica compatibilidad ni ejecutabilidad. RAM permanece en simulación; XIP no tiene evidencia; Flash, `0x25`, `0x30`, USB Host físico y escritura siguen bloqueados. DARK-1, bootloader y `[0x00000,0x04000)` permanecen intactos.
