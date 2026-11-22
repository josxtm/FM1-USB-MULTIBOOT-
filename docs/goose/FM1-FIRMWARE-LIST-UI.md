# Selector textual de firmwares

El backend `embedded_lcd_list_renderer` sustituye Cover Flow en la imagen full del selector. Usa las primitivas ST7789 existentes y una única línea RGB565 de 480 bytes; no usa framebuffer, imágenes, carrusel ni animación.

La pantalla muestra título, hasta tres entradas visibles con `>` en la selección, estado y ayuda `PRESETS ELEGIR`. El core conserva navegación circular y limita el catálogo a 64 entradas. Labels largos se truncan al ancho de texto disponible.

Los errores de almacenamiento/compatibilidad deben conservar el estado real del core: sin tarjeta, USB no disponible, FAT32 inválida, catálogo vacío, formato desconocido, SHA incorrecto o arranque no disponible. PLAY sigue pasando por el planificador; no salta a una dirección.

Se generó `build/lcd-selector-list.ppm` como captura host 240x240. Los renderizadores Cover Flow permanecen sólo como regresión histórica y no se enlazan en `ram-app-pi32v2-full-image`.
