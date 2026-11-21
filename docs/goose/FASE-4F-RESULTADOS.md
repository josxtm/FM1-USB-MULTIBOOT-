# FASE 4F — Cover Flow visual y biblioteca Android

**Rama:** `goose/fase3b-4a-preflight`  
**PR:** #8 (Draft)  
**Build objetivo:** `FM1_RAM_Loader_v1.0_comp13.apk` / `versionCode 13`

## Implementación

- Se añadió `AndroidCoverFlow`, una interfaz Compose compacta para tablets con tarjeta central, tarjetas laterales, botones anterior/siguiente, gesto horizontal y animación de desplazamiento.
- Las tarjetas muestran nombre, versión, formato BIN/FWS y estado SHA real. La selección central reutiliza `CoverFlowModel`.
- PLAY abre únicamente el panel de inspección. No hay instalación, carga RAM, ejecución ni escritura del sintetizador.
- `CoverFlowModel` conserva la selección por ID al recargar un catálogo cuando la entrada sigue presente.
- Tras una exportación verificada, `SdLibraryActivity` recarga `INDEX.CSV`, vuelve a verificar `FM1/FW/` mediante SAF y actualiza automáticamente la lista y el Cover Flow.
- Se mantienen los límites de catálogo y firmware FM1CAT v1, la deduplicación y el estado `UNVERIFIED`.

## Integridad y errores

Cada archivo presente se lee de nuevo desde SAF y se compara por tamaño y SHA-256 con el registro. Los archivos ausentes permanecen como no verificados y generan advertencia; un error de lectura del catálogo lleva a `ERROR` sin presentar datos como íntegros. La integridad nunca se interpreta como compatibilidad o ejecutabilidad.

## Pruebas añadidas

- Navegación y wrap-around.
- Estados `NO_CARD`, `EMPTY`, `BROWSE`, `INSPECT` y `ERROR`.
- Preservación de selección después de recarga.
- Inspección usando la evaluación real del archivo.
- Contenido válido, corrupto, hash incorrecto y ausente.
- Catálogos FM1CAT v1 múltiples y vacíos.

## Validación CI

- Android/Kotlin, Lint, pruebas unitarias, compilación y APK comp13: ✅ [run 44962954196](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44962954196)
- Checks de PR Android posteriores al commit final: ✅ [run 44962957715](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44962957715)
- Diagnóstico RAM/pi32v2, USB/FAT32 y coherencia APP1: ✅ [run 44962957742](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44962957742)
- Simulador Cover Flow: ✅ [run 44962346975](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44962346975)

La primera ejecución de comp13 detectó una API de consumo de gestos no disponible en la versión Compose del proyecto; se corrigió eliminando esa llamada y se repitió la validación hasta obtener éxito. No se oculta ese fallo histórico.

## Seguridad

RAM continúa limitada a simulación; XIP carece de evidencia; Flash, lectura ambigua, escritura, borrado, `0x25`, `0x30`, ejecución APP1 y USB Host físico permanecen bloqueados. La única consulta física continúa siendo MIDI `0x12`. DARK-1, el m-bootloader y `[0x00000,0x04000)` no se modifican.
