# FASE 5R — resultados

## Implementación

Se añadió `scripts/inspect_firmware.py`, inspector JSON read-only para APP1, ELF32, BIN/FWS opacos y desconocidos. Calcula SHA-256, comprueba APP1/CRC/rango, identifica máquina ELF cuando existe y expone requisitos faltantes. No copia ni incorpora archivos inspeccionados.

Se amplió `mb_launch_policy` con el vocabulario `INSPECTION_ONLY`, `RAM_CANDIDATE`, `FLASH_INSTALL_CANDIDATE`, `ADAPTATION_REQUIRED`, `INCOMPATIBLE` y `UNKNOWN`, además de razones explicativas. Los nombres antiguos permanecen como aliases. No existe ejecutor; `MB_LAUNCH_BLOCKED` permanece.

Se añadió `scripts/virtual_flash_recovery.py` para simular commit, corte, corrupción, verificación y rollback en memoria virtual, preservando `[0,0x4000)`. No accede a Flash física.

## Decisión técnica

La ejecución directa desde microSD no está demostrada. La ejecución RAM sólo es candidata para imágenes con ABI, SRAM, stack, vectores y reloj demostrados. XIP no está documentado para este contexto. La arquitectura más viable para continuar es almacenar múltiples imágenes externamente y, si el mapa futuro lo confirma, instalar transaccionalmente una imagen en el área de aplicación con m-bootloader intacto y recuperación externa.

Esto no equivale a un multiboot físico instalado: no se conoce aún el mapa real de Flash ni si existe espacio de backup/rollback.

## Evidencia y limitaciones

APP1 propio: estructura verificable, ejecución no probada. BIN/FWS: bytes opacos sin cabecera pública suficiente. DARK-1, Melodee y Tessera: sin binarios propietarios incluidos y sin ABI de ejecución demostrado para esta unidad. Las fuentes públicas y documentación estudiadas distinguen protocolo, stage-2, run-ram y SD stage-2, pero no prueban ejecución directa desde microSD en una aplicación normal.

Pruebas reproducibles cubren inspector, formatos desconocidos, APP1 íntegro, política, corrupción, corte, restauración y protección virtual. Continúan las pruebas USB MSC/FAT32/Cover Flow de 5Q y los cuatro workflows.

## Seguridad

No se ejecutó firmware, no se escribió Flash, no se modificó DARK-1/m-bootloader, no se activó USB Host físico y no se añadió salto real. PR #8 permanece Draft.
