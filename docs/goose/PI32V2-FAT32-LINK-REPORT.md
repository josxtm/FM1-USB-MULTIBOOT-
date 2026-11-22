# Enlace pi32v2 integrado core + FAT32

Se añadió un objetivo separado de los artefactos APP1 existentes:

```text
make TOOLCHAIN=toolchain fat32-pi32v2-object fat32-pi32v2-sim
```

El conjunto contiene `embedded_multiboot.c`, `embedded_fat32_storage.c`, `fat32.c`, `embedded_fat32_pi32v2_sim.c` y `runtime.c`. La HAL de bloques devuelve errores simulados y el renderer sólo usa callbacks; no se enlazan libc, USB, LCD, Flash ni GPIO.

El entry point es `embedded_fat32_sim_entry`, enlazado con el mismo script aislado y un mapa separado `build/embedded_fat32_pi32v2.map`. El workflow publica objetos, ELF, mapa y reportes `llvm-readelf`. El assert de `src/embedded_multiboot.ld` sigue limitando text+rodata+data+bss a 64 KiB.

El resultado del enlace debe leerse como evidencia de compilación, no como SRAM disponible, ABI, APP1 instalable ni ejecución del flujo FAT32 en AC791N. El APP1 existente no se modifica. Si una versión del toolchain excede el assert, el error es un bloqueo técnico reproducible y no se compensa relajando el límite.
