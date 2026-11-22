# Cover Flow ST7789 embebido

`src/embedded_lcd_renderer.c/.h` reutiliza `lcd_window()`, `lcd_pixels()` y `lcd_end()` de `src/lcd.c`; no crea un controlador SPI/ST7789 alternativo. El backend dibuja rectángulos, texto y estados por scanlines RGB565 big-endian usando un buffer de 480 bytes (240 píxeles × 2), sin framebuffer 240×240.

La tarjeta central se destaca; las laterales se reducen; se muestran label, versión, estado `OK/CHECK`, estado del core y `PLAY INSPECT ONLY`. La política de `mb_render()` mantiene 0/1/2/3 tarjetas visibles y soporta catálogos de 64 mediante selección circular. El sink físico solo se obtiene mediante `mb_lcd_physical_sink()`; las pruebas usan un sink falso y no tocan registros.

La inicialización física sigue siendo `lcd_init(BRD_FM1)` y no se invoca en las pruebas. La pantalla real, colores, orientación y timings permanecen sin verificar.
