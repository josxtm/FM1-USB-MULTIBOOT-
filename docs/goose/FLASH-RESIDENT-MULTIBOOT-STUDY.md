# Estudio de cargador multiboot residente en Flash

La autorización de diseño permite considerar reemplazar DARK-1, pero no autoriza tocar la Flash. Un diseño C conceptual requeriría: región de bootloader intacta, cargador dentro del área de aplicación, tabla de imágenes o catálogo externo, verificación antes de seleccionar, inicialización de pantalla/controles, backend de almacenamiento y una ruta de recuperación que no dependa del cargador recién escrito.

## Lo que sí se sabe

La identidad MIDI reporta Flash Puya `0x856014` de 1 MiB y m-bootloader `2.4-395e6cc`. `[0x00000,0x04000)` permanece protegida por política. El código existente produce APP1 y un ELF aislado, pero no proporciona el mapa real de particiones ni prueba que la Flash sea XIP para este ABI.

## Lo que falta

No están demostrados los límites de DARK-1, vectores, tabla de arranque, sectores protegidos adicionales, método de actualización, formato oficial, checksum exigido, reserva SRAM, inicialización ST7789, botones, watchdog, retorno ni recuperación ante alimentación interrumpida. La capacidad nominal de 1 MiB no es un mapa de aplicación.

## Prototipo permitido

La simulación FAT32 y el core verifican selección, tamaño, hash y bloqueo. Puede añadirse un simulador de transacción sobre archivos temporales con doble copia, marcador de commit y verificación posterior; no se interpreta como prueba de Flash física. Ningún comando 0x25/0x30, borrado, programación o instalación se ejecutó.

**Conclusión:** C es una arquitectura de investigación plausible, no una arquitectura instalada ni aprobada. La primera acción física debe ser una revisión de recuperación y mapa, no una escritura.
