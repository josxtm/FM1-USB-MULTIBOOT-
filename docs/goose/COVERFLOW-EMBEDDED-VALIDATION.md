# Cover Flow embebido — validación reproducible 5C.2

`tests/embedded_render_demo.c` utiliza el `mb_core` C99 real, no una máquina de estados paralela. La HAL de prueba expone `FM1/INDEX.CSV` y `FM1/FW/*.BIN`/`*.FWS` desde memoria; el renderer sólo escribe SVG y no conoce SPI, GPIO, ST7789 ni USB.

## Recorrido end-to-end

`build/embedded-e2e-trace.txt` registra:

```text
BOOT
CATALOG BROWSE 3
SELECT BROWSE TWO
INSPECT INSPECT
BACK BROWSE
VERIFY READY_FOR_SIMULATION integrity=1
RENDER 240x240
```

La secuencia cubre boot, catálogo, selección, inspección, retorno, hash/size completo y resultado. PLAY sólo muestra `INSPECT`; no lanza firmware.

## Matriz de tarjetas

`make render-matrix` carga el núcleo y verifica callbacks reales para 1, 2, 3 y 64 entradas. La política es 1 tarjeta para un elemento, central+adyacente para dos y tres tarjetas (lateral, central, lateral) para tres o más. No se accede fuera de `selected < count`.

## SVG y estados

La superficie es exactamente 240×240, con fondo oscuro, acentos turquesa y texto XML escapado (`&`, `<`, `>`, comillas y apóstrofes). `scripts/validate_embedded_render.py` parsea XML, comprueba dimensiones y exige `PLAY INSPECT ONLY`. Los campos FM1CAT son estrictamente ASCII según la especificación, por lo que el demo usa `TWO DEMO`; el backend conserva escape defensivo para cualquier texto que reciba.

Los estados públicos del core son BOOT, NO_STORAGE, LOADING_CATALOG, EMPTY, BROWSE, INSPECT, VERIFY, READY_FOR_SIMULATION, INCOMPATIBLE, ERROR y RECOVERY. Las rutas de error se ejercitan en las pruebas C (missing/disconnect, límites, metadata y verificación); RECOVERY es una reserva de integración futura y no se fuerza artificialmente a declarar una recuperación real. No se afirma que todos esos estados sean una captura de hardware.

## Limitaciones

El SVG es una evidencia de layout y callbacks, no una medición de legibilidad de LCD. El ELF pi32v2 sólo es un enlace aislado de demostración; su entry point no ejecuta este recorrido. No existe autorización para cargarlo, instalarlo o ejecutarlo en el FM-1.
