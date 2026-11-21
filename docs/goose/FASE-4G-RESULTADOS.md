# FASE 4G — Estabilización de Cover Flow, biblioteca SAF y diagnóstico

**Rama:** `goose/fase3b-4a-preflight`  
**PR:** #8 (Draft)  
**Build objetivo:** `FM1_RAM_Loader_v1.0_comp14.apk` / `versionCode 14`

## Cambios

- `CoverFlowModel` es la fuente de selección durante las recargas: conserva el ID actual, acepta el ID persistido tras recrear la actividad y elige un índice válido si la entrada desaparece.
- `SdLibraryActivity` ya no reinicia `librarySelection` a cero. La selección se obtiene de `coverFlow.selected`, se persiste como ID y se limita defensivamente al rango disponible.
- PLAY recibe la evaluación asociada a la entrada seleccionada; la tarjeta, el estado de integridad y el panel de inspección apuntan al mismo firmware.
- La verificación de cada archivo se aísla con `runCatching`: un archivo ausente, inaccesible, corrupto o con SHA incorrecto genera una advertencia específica sin descartar las demás entradas.
- Los errores de `INDEX.CSV` siguen un camino separado y llevan la biblioteca a `ERROR`; se conserva la posibilidad de reintentar mediante recarga.
- La interfaz visual trata específicamente 1, 2 y 3 o más firmwares, sin duplicar tarjetas artificialmente para catálogos pequeños.
- La carpeta FM1 y la selección se recuperan mediante URI persistente de SAF; no se guardan copias internas ni rutas absolutas.
- Se conserva la transacción `INDEX.NEW`/`INDEX.BAK`: el catálogo anterior no se elimina antes de que la copia, lectura posterior y nuevo índice hayan sido verificados.

## Pruebas

Se ampliaron las pruebas de modelo para recarga, eliminación, selección persistida, inspección con evaluación real y catálogos de 64 entradas. Se conservan las pruebas FM1CAT v1, SHA-256 real, archivos ausentes/corruptos, USB/FAT32, APP1, MIDI y simulador Cover Flow.

Las pruebas Compose/SAF instrumentadas requieren un dispositivo o emulador Android y no se presentan como pruebas físicas de tablet. La UI sigue siendo una integración Compose verificable por compilación, Lint y pruebas de modelo.

## Estado y limitaciones

La aplicación muestra `cargada`, `verificación parcial o con advertencias`, `entradas verificadas`, `EMPTY` o `ERROR`. Una retirada de la microSD durante una lectura SAF depende del proveedor: el índice falla de forma global y una entrada individual falla de forma aislada. No se implementa acceso raw a bloques ni recuperación automática fuera de SAF.

FM1CAT v1, `UNVERIFIED`, la separación entre integridad y ejecutabilidad y los bytes originales se mantienen sin cambios.

## Validación CI

- Android/Kotlin, Lint, pruebas unitarias y APK comp14: ✅ [run 44963884435](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44963884435)
- Diagnóstico RAM/pi32v2, USB/FAT32 y coherencia APP1: ✅ [run 44963887644](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44963887644)
- Simulador Cover Flow: ✅ [run 44963890621](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44963890621)
- Checks de PR Android y diagnóstico: ✅ [Android](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44963883061) · [diagnóstico](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44963883025)
- `make test` local USB/FAT32: ✅

## Seguridad

RAM continúa limitada a simulación; XIP carece de evidencia; Flash, lectura ambigua, escritura, borrado, `0x25`, `0x30`, ejecución APP1 y USB Host físico permanecen bloqueados. La única consulta física continúa siendo MIDI `0x12`. DARK-1, el m-bootloader y `[0x00000,0x04000)` no se modifican.
