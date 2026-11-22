# Integración FAT32 → núcleo multiboot — FASE 5D

## Alcance verificado por simulación

`src/fat32.c` continúa siendo el único parser FAT32. `src/embedded_fat32_storage.c` es sólo un adaptador `struct mb_storage`: monta el volumen mediante `fat32_mount()`, localiza `FM1/INDEX.CSV` y `FM1/FW/<short-name>` mediante las funciones FAT existentes, y traduce `enum diag_result` a `MB_IO_*`. El núcleo no conoce sectores, FAT, USB ni SCSI.

`scripts/generate_fat32_image.py` genera una imagen FAT32 determinista superfloppy de 512 bytes, un sector por cluster, dos copias FAT y 65.525 clusters. Contiene `FM1`, `FM1/FW`, `INDEX.CSV` y archivos de prueba con cadenas de clusters deliberadamente fragmentadas. Los datos son sintéticos; sus SHA-256 se imprimen durante la generación.

`make fat32-integration` genera y monta imágenes con 0, 1, 2, 3 y 64 entradas, selecciona e inspecciona una imagen, lee bytes reales desde la imagen, verifica tamaño y SHA-256 y confirma `MB_LAUNCH_BLOCKED`. También inyecta desconexión y timeout del backend de bloques. La ejecución normal reporta 66.581 sectores y, para 64 entradas, 31 lecturas de sectores durante el recorrido.

## Seguridad del adaptador

Sólo se aceptan `FM1/INDEX.CSV` y `FM1/FW/<ID>.BIN`/`<ID>.FWS`; el nombre se vuelve a validar como 8.3 antes de buscarlo. No se aceptan rutas absolutas, traversal, separadores ni directorios arbitrarios. `fat32_read_at()` limita offset/capacidad al tamaño declarado y rechaza cadenas FAT inválidas, EOF prematuro y clusters fuera de volumen. `MB_IO_EOF` sólo se entrega en offset igual al tamaño.

## Estado de la evidencia

- **Simulado:** MBR/superfloppy, BPB FAT32, FAT, directorios anidados, cadenas fragmentadas, lectura por offset, catálogo y firmware.
- **Analizado estáticamente:** límites y validaciones en `fat32.c`, separación del HAL y ausencia de USB físico en el adaptador.
- **No verificado físicamente:** acceso USB Host, microSD, VBUS, controlador ST7789, rendimiento, desconexión eléctrica y cualquier ejecución.

El lector USB real (`storage.c`) no se enlaza al test: el backend es un dispositivo de bloques en memoria/archivo, evitando activar USB Host.
