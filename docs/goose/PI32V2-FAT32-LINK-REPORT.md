# Enlace pi32v2 integrado core + FAT32

Se añadió un objetivo separado de los artefactos APP1 existentes:

```text
make TOOLCHAIN=toolchain fat32-pi32v2-object fat32-pi32v2-sim
```

El conjunto contiene `embedded_multiboot.c`, `embedded_fat32_storage.c`, `fat32.c`, `embedded_fat32_pi32v2_sim.c` y `runtime.c`. La HAL de bloques devuelve errores simulados y el renderer sólo usa callbacks; no se enlazan libc, USB, LCD, Flash ni GPIO.

El entry point es `embedded_fat32_sim_entry` en `0x01c31358`. El reporte CI final confirma ELF32 little-endian, máquina JieLi pi32v2, `.text=0x1412` (5138), `.rodata=0x2e0` (736), `.data=0` y `.bss=0x6001` (24577), total estático 30.451 bytes. No hay referencias externas sin resolver; `memcpy`/`memset` provienen del runtime. El mapa separado `build/embedded_fat32_pi32v2.map` y reportes `llvm-readelf` se publican como artefactos. El assert de `src/embedded_multiboot.ld` sigue limitando text+rodata+data+bss a 64 KiB.

El resultado del enlace debe leerse como evidencia de compilación, no como SRAM disponible, ABI, APP1 instalable ni ejecución del flujo FAT32 en AC791N. El APP1 existente no se modifica. Si una versión del toolchain excede el assert, el error es un bloqueo técnico reproducible y no se compensa relajando el límite.
