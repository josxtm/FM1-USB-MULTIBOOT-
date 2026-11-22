# FM1 — viabilidad de adaptadores

## Decisión

No se justifica diseñar un adaptador USB-C nuevo antes de conocer la PCB real. Sí existe un diseño público de referencia para microSD SPI: [m-card](https://github.com/jvitkauskas/m-card), CC0-1.0. Debe tratarse como referencia documental/prototipo, no como hardware aprobado.

## Referencia SPI preliminar

Arquitectura publicada por m-card:

- PCB de dos capas, 30 × 25,2 mm.
- Socket microSD Hirose DM3CS-SF.
- Dos conectores FPC 10 pines y cable aproximado de 80 mm.
- 74LVC1G04 para invertir CS.
- 220 Ω en DO y 470 Ω en CLK/DI.
- Pull-ups/pull-downs de 47 kΩ.
- 10 µF + 100 nF en alimentación.
- Alimentación documentada de 3,3 V desde J12.
- Sin card-detect.

BOM publicada incluye JLCPCB/LCSC, pero el BOM no prueba disponibilidad de espacio, corriente o compatibilidad de la unidad del propietario. La licencia del diseño es CC0-1.0; no se copia código ni se declara que el circuito esté validado.

## Esquema funcional, no de fabricación

```text
FM1 J12 LCD-side                 m-card                 LCD
CLK  -----------------------------470R------------------ CLK
MOSI -----------------------------470R------------------ MOSI
MISO / LCD D/C ----220R---------- SD DAT0
LCD CS ------------> inverter ---> SD CS
3V3 ----------------------------- SD VDD + decoupling
GND ----------------------------- SD GND
``` 

Este diagrama sólo resume la documentación pública. No debe usarse para soldar: hay que confirmar orientación de FFC, numeración de J12, revisión y niveles en la unidad concreta.

## USB-C adaptador hipotético

Un adaptador USB-C necesitaría como mínimo:

- receptáculo y protección ESD USB 2.0;
- red CC que corresponda al rol host confirmado;
- fuente VBUS con switch current-limited y protección contra backfeed;
- desacoplo y retorno de masa adecuados;
- conectividad D+/D− sin asumir el pinout del AC791N;
- preferiblemente hub/lector alimentado externamente si el FM-1 no puede entregar corriente;
- desconexión segura y medición de corriente.

No se propone BOM ni PCB USB-C: faltan la revisión de placa, el esquema de VBUS/CC y la capacidad de la fuente.

## Compatibilidad software futura

### SPI

Crear una HAL de bloques SD SPI read-only, con inicialización acotada, timeout y traducción a `MB_IO_*`; reutilizar `fat32.c`, `embedded_fat32_storage.c`, FM1CAT y el renderer existente. La HAL debe mantener el LCD seleccionado/deseleccionado correctamente y no compartir el bus durante una transacción de pantalla.

### USB-C

Reutilizar `husb.c`, `storage.c`, `msc_bot.c` y `fat32.c` únicamente después de validar la capa eléctrica. El primer firmware diagnóstico debe prohibir WRITE(10), FORMAT, ERASE y comandos desconocidos, y detenerse antes de FAT32 si enumeración, capacidad o lectura fallan.

## Bloqueo de fabricación

No ordenar ni instalar `m-card`, no soldar FFC y no conectar lector/hub hasta completar las fotografías y las etapas de continuidad del plan de medición. La recuperación del m-bootloader y el acceso físico al LCD deben seguir disponibles en todo momento.
