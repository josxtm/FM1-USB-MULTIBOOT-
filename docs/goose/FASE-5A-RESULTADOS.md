# FASE 5A — Núcleo multiboot embebido portable

**Rama:** `goose/fase3b-4a-preflight`  
**PR:** #8 (Draft)  
**Alcance:** software host/simulación; ningún backend físico habilitado.

## Reconciliación

La comprobación inicial encontró HEAD local y remoto iguales en `d32e9c3` (`d32e9c37646d92000426e6cd39f902b88b231e15`), árbol limpio y ningún commit o archivo local de FASE 5A previo. La PR #8 sigue apuntando a la rama correcta y permanece Draft.

## Implementado

- Núcleo C99 portable en `src/embedded_multiboot.c/.h`.
- Máquina de estados BOOT, NO_STORAGE, LOADING_CATALOG, EMPTY, BROWSE, INSPECT, VERIFY, READY_FOR_SIMULATION, INCOMPATIBLE, ERROR y RECOVERY.
- Parser FM1CAT v1 con límite de 64 entradas y 24 KiB, validación de cabeceras, campos, nombres, tamaños, hashes, `UNVERIFIED`, duplicados y rutas.
- Lectura mediante HAL de almacenamiento simulado y hash SHA-256 incremental con buffer de 512 bytes.
- Navegación circular, inspección PLAY/REC, selección protegida y render callback central/lateral.
- Backend conceptual de arranque siempre bloqueado: `MB_LAUNCH_BLOCKED`.
- Pruebas deterministas host para catálogos, 64 entradas, navegación, SHA-256, desconexión, corrupción y límites.
- Workflow aislado del diagnóstico APP1 para evitar cambiar la identidad binaria aprobada.

## Validación

- `make core-test`: superado localmente.
- `make test`: USB/FAT32 existente superado localmente.
- Compilación host estricta del nuevo núcleo: superada localmente.
- No se ejecutó toolchain pi32v2 localmente en este entorno; el workflow de diagnóstico existente sigue siendo el gate de APP1/pi32v2.
- No se realizó prueba física de pantalla, USB Host, RAM o arranque.

## Límites y siguiente gate

El núcleo usa una implementación host acotada y no implica un port pi32v2 seguro. Faltan mapa SRAM independiente, ABI, reservas del m-bootloader, clocks, periféricos, retorno y recuperación autorizada antes de cualquier prueba RAM. Los firmwares completos, DARK-1 y terceros permanecen `UNVERIFIED`.

No se habilitaron `0x25`, `0x30`, Flash, saltos arbitrarios, carga RAM física, USB Host físico ni alimentación experimental. DARK-1, bootloader y `[0x00000,0x04000)` permanecen intactos.
