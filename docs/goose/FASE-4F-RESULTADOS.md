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

## Seguridad

RAM continúa limitada a simulación; XIP carece de evidencia; Flash, lectura ambigua, escritura, borrado, `0x25`, `0x30`, ejecución APP1 y USB Host físico permanecen bloqueados. La única consulta física continúa siendo MIDI `0x12`. DARK-1, el m-bootloader y `[0x00000,0x04000)` no se modifican.
