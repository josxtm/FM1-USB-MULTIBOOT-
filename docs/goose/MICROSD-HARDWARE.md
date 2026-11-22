# microSD: USB-C frente a m-card

| Criterio | USB-C lector MSC | m-card SPI |
|---|---|---|
| Software | MSC/BOT/FAT32 ya simulado | stage-2 `/MBOOT/MENU.BIN` documentado |
| Evidencia placa FM1 | USB Host/CC/VBUS no verificados | diseño público, prototipo/modificación |
| Preboot | no demostrado | temporal desde menú |
| Cambios físicos | ninguno sólo en teoría, pero rol/alimentación desconocidos | requiere instalar la placa/modificación |
| Recuperación | no demostrada | no convierte el selector en residente |
| Estado | BLOQUEADO | no adoptado |

El controlador USB Host del AC791N no demuestra que la PCB proporcione VBUS, CC, rol Host, protección o backfeed seguro. No se activa USB Host ni se conecta lector. La tarjeta de 32 GB sólo existe en fixtures/software, no como evidencia eléctrica.

Antes de cualquier prueba se requiere revisión de esquemático/placa, continuidad, alimentación limitada, rol CC, enumeración controlada y recuperación independiente. No se inicia en esta fase.
