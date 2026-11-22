# Cover Flow integrado desde FAT32

La fuente del catálogo no está embebida en esta prueba: `scripts/generate_fat32_image.py` crea los sectores y `tests/fat32_multiboot_integration.c` los entrega por `struct block_device` al lector FAT32 existente. El adaptador convierte el resultado en `struct mb_storage`; el renderer recibe las entradas almacenadas por el `mb_core`.

Para 0, 1, 2, 3 y 64 entradas se generan SVGs independientes. La salida se valida con `scripts/validate_embedded_render.py`: XML bien formado, dimensiones exactas 240×240 y texto `PLAY INSPECT ONLY`. Para 1/2/3/64 la matriz de tarjetas es 1/2/3/3. El SVG de cero entradas muestra estado EMPTY sin acceder a una selección inválida.

La secuencia comprobada es:

```text
FAT32 mount → INDEX.CSV → BROWSE → NEXT → INSPECT → REC
→ FM1/FW/F02.FWS → SHA/size/EOF → READY_FOR_SIMULATION
→ render → MB_LAUNCH_BLOCKED
```

Todo el flujo usa la máquina de estados C99 existente. No hay simulación paralela de navegación ni backend de pantalla física.
