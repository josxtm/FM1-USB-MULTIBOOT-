# FASE 5H — resultados

## Implementado

Se añadió `embedded_lcd_renderer.c/.h`, que reutiliza el LCD existente y dibuja Cover Flow RGB565 por franjas de 480 bytes. Se añadieron `embedded_keys.c/.h`, un adaptador lógico con debounce/repetición que conserva `keys.c` como único escáner eléctrico. Se añadieron pruebas host, PPM 240×240 y objetivos pi32v2 de compilación de objetos.

## Validación

La prueba usa el `mb_core` real, catálogos 0/1/2/3/64, sink LCD falso y framebuffer solo del host. Comprueba render, límites y eventos. Genera `build/lcd-coverflow-{0,1,2,3,64}.ppm`. El backend físico no se inicializa.

## Límites

ST7789, SPI1, colores, orientación, timings, botones, backlight y stack/SRAM no tienen validación física. No se activó LCD, GPIO, USB Host, RAM, Flash, APP1 ni m-bootloader. `MB_LAUNCH_BLOCKED` permanece.
