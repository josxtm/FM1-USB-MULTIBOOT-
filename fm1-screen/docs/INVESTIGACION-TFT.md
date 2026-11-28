# Investigación documental del bus TFT

La HAL de DARK-1 identifica un LCD de clase ST7789 de 240 × 240 con SPI1. Controla las ventanas por columna y fila y envía RGB565 por DMA. Su autor distingue un reloj nominal de 60 MHz y aproximadamente 44 MHz efectivos en sus mediciones. [Controlador DARK-1](https://github.com/sparky480v/Dark-1/blob/6c0cc34677c4188575158c9ea5f53c95200be4a0/firmware/src/lcd.c), [HAL FM1 Open](https://github.com/m-vave/fm1_open/blob/4c7496daac04a95ff10d1afdcc8f9ead2dbabc41/app/board/fm1_lcd_hw.h)

Un puente documental observaría reloj, datos, selección y comando/dato con alta impedancia. Su decodificador seguiría las ventanas, orientación, formato, orden de bytes y cambios parciales. Un cuadro RGB565 ocupa 115.200 bytes. Los 30 y 60 FPS implican 3,46 y 6,91 MB/s respectivamente, antes de sobrecargas. USB Full Speed requiere reducir el tráfico mediante regiones o compresión. USB High Speed ofrece margen para un flujo completo.

La frecuencia SPI y la carga eléctrica condicionan una eventual captura por FPGA o periférico síncrono. Quedan por determinar revisión de placa, niveles, accesibilidad y tolerancias. Esta fase es documental.
