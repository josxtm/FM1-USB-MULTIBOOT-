# RAM-only — auditoría de stack y memoria

## Regiones del linker experimental

```text
APP   0x01C30000 .. 0x01C7FD78   imagen enlazada
LOW   0x01C16800 .. 0x01C1E000   BSS permitido
stack 0x01C1F800                 límite superior documentado
sstack 0x01C20000                límite superior documentado
retained desde 0x01C7FD80        no disponible
```

El linker comprueba que `.bss` termine antes de `0x01C1E000` y que `.image` no supere la región APP. El validador Python comprueba las mismas relaciones sobre el ELF generado.

## Objetos medidos

Mediciones host existentes:

```text
struct mb_entry          140 bytes
struct mb_catalog       8964 bytes
struct mb_core          9096 bytes
struct mb_lcd_renderer   552 bytes
struct mb_key_adapter     20 bytes
SHA buffer              512 bytes
LCD scanline            480 bytes
render framebuffer        0 bytes
```

El `mb_core` contiene el catálogo completo y el buffer estático de carga de catálogo de 24577 bytes pertenece al core/código de carga. El renderer sólo conserva una scanline RGB565. En 5N.1 el `mb_core`, renderer, adaptador y reloj de la aplicación se almacenan como estado estático controlado, evitando colocar aproximadamente 10 KB de core en el stack de `embedded_ram_app_entry`.

## Stack

El ELF no proporciona una medición dinámica de profundidad de stack. El mapa muestra símbolos y contribuciones de funciones, pero no demuestra el máximo de llamadas anidadas en todos los estados. La reserva de stack documentada en el linker no se interpreta como SRAM libre verificada.

Las funciones de mayor interés para una futura auditoría ABI son:

- `embedded_ram_app_entry`;
- `mb_load_catalog`;
- `mb_verify_selected` y su SHA-256;
- `mb_render`;
- `fill`, `text` y `card` del renderer;
- `plat_menu_scan`;
- transporte SPI/LCD.

No se cambian direcciones de memoria por aproximación ni se declara un margen de stack físico.

## Colisiones

El preflight verifica por software:

- APP separado de memoria retenida.
- BSS antes del límite bajo documentado.
- Imagen plana idéntica al segmento ejecutable.
- Ausencia de símbolos indefinidos.
- Ausencia de símbolos de programación/borrado prohibidos.

Pendiente de hardware: tamaño real de SRAM, stack heredado, buffers del bootloader, vectores y memoria retenida del `2.4-395E6CC`.
