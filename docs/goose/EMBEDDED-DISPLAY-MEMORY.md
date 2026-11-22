# Memoria del display embebido

El backend no reserva framebuffer completo. Usa:

```text
scanline RGB565: 240 × 2 = 480 bytes
struct mb_lcd_renderer medido por host: 552 bytes (incluye sink/renderer + 480 bytes de línea)
```

El host genera PPM de 240×240 solo para validación visual. El target pi32v2 compila el renderer y el adaptador de teclas como objetos freestanding; la dirección `0x01C30000` y el límite virtual de 64 KiB no prueban SRAM física.

El coste de SPI/ventana es deliberadamente conservador: cada fila abre una ventana y envía una franja. El rendimiento real, tiempo de cuadro, consumo y disponibilidad de SPI1 deben medirse posteriormente en hardware autorizado.
