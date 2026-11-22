# Flash virtual y recuperación

`scripts/virtual_flash_recovery.py` modela una Flash de 1 MiB en memoria Python. Protege `[0x0000,0x4000)`, usa área de aplicación desde `0x4000`, registra SHA y simula commit, corte antes de commit, corte tras datos y corrupción posterior.

La transacción virtual conserva copia previa, escribe sólo en el objeto de memoria, verifica y restaura ante fallo. Cada caso comprueba que la región protegida no cambia. No usa registros, USB, SFC ni comandos de programación.

Esto demuestra el modelo de estados `PREPARED → COMMITTED` o `ROLLED_BACK`, no disponibilidad de doble imagen. Con una Flash real de 1 MiB no se debe asumir espacio para backup, rollback o varios sistemas. M-Flasher y modo descarga son recuperación externa documentada, no rollback automático del FM1.

Antes de cualquier instalación real faltan mapa de particiones, sectores protegidos, formato de commit, corte de alimentación, recuperación probada y autorización separada. Ningún ejecutor ni escritura Flash se añadió.
