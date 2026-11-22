# Integración de controles físicos

`src/keys.c/.h` conserva el escaneo eléctrico existente, incluyendo shift registers, snapshots/restauración y LEDs. `src/embedded_keys.c/.h` es únicamente el adaptador lógico: recibe la máscara producida por el escáner y entrega `MB_EVENT_PREVIOUS/NEXT/PLAY/REC`.

La adaptación tiene debounce de 20 ms, primera pulsación y repetición a 400 ms/100 ms. La prioridad es REC, PLAY, anterior y siguiente. No modifica GPIO, pinmux, secuencias del m-bootloader ni recuperación. Los tests usan máscaras simuladas y no llaman `plat_menu_scan()`.

El acoplamiento con un bucle físico requiere una futura integración controlada que llame a `plat_menu_scan()`, pase su máscara al adaptador y bloquee entradas durante verificación. Esa integración no se ejecuta ni se instala en el FM-1.
