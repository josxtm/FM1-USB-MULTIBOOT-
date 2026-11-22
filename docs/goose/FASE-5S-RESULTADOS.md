# FASE 5S — resultados

## Cambio de producto

Cover Flow fue retirado del binario full del selector. El nuevo renderer textual ST7789 muestra una lista de firmwares, marcador `>`, estado y ayuda PRESETS/PLAY. Usa una sola scanline RGB565 y no framebuffer ni gráficos decorativos. Los renderizadores Cover Flow quedan como regresión histórica.

## Implementación

* `embedded_lcd_list_renderer.c/.h`: lista textual 240x240 sobre las primitivas LCD existentes.
* `embedded_presets.c/.h`: decoder de cuadratura por tabla, cuatro transiciones por detente, rebotes/transiciones inválidas rechazados.
* `embedded_ram_app` acepta `encoder_scan` opcional y transforma detentes en NEXT/PREVIOUS; PLAY continúa por el adaptador de pulsación y el planificador.
* El target full ya no enlaza el renderer Cover Flow; mantiene core, USB MSC, FAT32 y seguridad.
* La autodetección `.BIN/.FWS/.fwsc` se dejó como inspección futura: falta especificación para convertir nombres en ejecutables.

## Evidencia

Host generó `build/lcd-selector-list.ppm` a 240x240 y validó catálogos/lista y encoder. La conexión física del encoder PRESETS/encoder 6 no se afirmó: no existe pinout verificable suficiente. La HAL física debe proporcionar A/B antes de una prueba autorizada.

## Arranque

PLAY solicita verificación y planificación; no ejecuta. RAM adaptada, módulos residentes e instalación son estrategias documentadas, pero ninguna tiene ABI/memoria/recuperación suficiente para habilitarse. La instalación en área de aplicación es la hipótesis más plausible si se confirma el mapa Flash, conservando m-bootloader.

## Seguridad

No se ejecutó FM1, no se activó USB Host, no se escribió Flash, no se modificó m-bootloader/DARK-1 y `MB_LAUNCH_BLOCKED` permanece. Sin hardware, las pruebas son host/emulación y no demuestran compatibilidad física.
