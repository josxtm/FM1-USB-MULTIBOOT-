# Política de instalación Flash

## Política

`FlashRegionPolicy()` es fail-closed. Ninguna región es programable por defecto. Un perfil virtual explícito no es autorización física.

Siempre protegidos:

- `0x00000–0x03FFF` bootloader/configuración;
- registros y chip-key/configuración de unidad;
- VM, BTIF, USR y regiones desconocidas;
- `0xFF000–0x100000` key/MAC;
- cualquier región que el auditor no pueda clasificar.

El auditor Flash valida cabecera, tablas, ENC, CRC, offsets, límites y anidamientos. Reconocer `app.bin` no autoriza sustituirlo.

## Secuencia simulada

1. FM1CAT y formato.
2. Modelo/JEDEC/capacidad.
3. CRC/SHA y tablas.
4. plan de sectores virtual.
5. confirmación.
6. erase/program sólo en perfil sintético.
7. readback/CRC.
8. reinicio simulado.
9. resultado recovery/no recovery.

No se promete atomicidad ni rollback en banco único. No existe writer físico y `MB_LAUNCH_BLOCKED` permanece.
