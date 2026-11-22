# Fase 5Z · Selector nativo FM1 MULTIBOOT

El código `stg2_selector_start.S`, `stg2_selector_platform.c`, `stg2_selector.c` y `stg2_sd.c` enlaza a pi32v2 real. SPI1 se adapta a `block_device` de solo lectura. Se conservan el parser FAT32, el adaptador `mb_storage`, FM1CAT v1, SHA‑256, las teclas físicas existentes y los glifos/renderizador del selector textual. La biblioteca se carga de `FM1/INDEX.CSV`; las entradas apuntan a `FM1/FW/<ID>.BIN` o `<ID>.FWS`. No se incluye una biblioteca fija ni un motor musical de terceros.

- La LCD de 240 × 240 muestra siete entradas por página. OCT+/OCT− navegan y cambian de página al cruzar el borde. PLAY muestra el nombre, versión, archivo y tamaño de la entrada.
- PLAY en el detalle valida el tamaño y SHA‑256 por lectura secuencial. La compatibilidad se muestra como pendiente. El SHA‑256 del catálogo detecta cambios frente al manifiesto y no acredita por sí solo el origen de una imagen.
- REC en el detalle vuelve a la lista. Durante una verificación, REC cancela entre lecturas. REC en la lista devuelve el control a m‑bootloader. Se filtra la pulsación de PLAY que abrió el STG2.
- Sin tarjeta, con FAT incorrecto o índice ilegible, se muestra el motivo. PLAY reintenta y REC vuelve. Un catálogo parcialmente parseado no se ofrece para selección.
- El adaptador FAT32 mantiene un cursor por lectura secuencial, prueba el final de cadena y reinicia al cambiar archivo o cada lectura inicial. En el fixture 5Y de 8704 B el recuento del harness pasó de 221 a 51 lecturas de sector. Es una medida del simulador, no un tiempo físico.

El ejecutable no abre MSC/USB físico, no contiene un escritor Flash y no reinicia. `mb_prepare_launch()` conserva `MB_LAUNCH_BLOCKED`. `virtual_slotfile` sigue siendo el simulador independiente del proceso de selección, respaldo, instalación, verificación y recuperación sobre archivos. Ambos consumen las mismas rutas de FM1CAT; la UI no inicia el simulador ni escribe un plan sobre la tarjeta. [Instalador virtual 5Y](FASE-5Y-RESULTADOS.md#instalador-virtual)

El artefacto incluye `stg2-selector.ppm` generado por el mismo renderizador C sobre un catálogo FAT32 sintético. La captura acredita las filas y el resultado del código de interfaz. La señal SPI1, los GPIO y la pantalla real siguen pendientes.
