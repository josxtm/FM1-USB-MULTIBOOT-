# Viabilidad de ejecución RAM

## Estado actual

El planificador `RamExecutionPlanner` valida en software el APP1 experimental aprobado: pi32v2, entry `0x01C30000`, formato APP1 v1, placa 1, CRC16, SHA-256 y límites del artefacto. El resultado es `APPROVED_FOR_SOFTWARE_SIMULATION`, nunca autorización física.

| Método | Estado FASE 4D | Motivo |
|---|---|---|
| RAM | Simulación solamente | Falta confirmar reservas SRAM, ABI, reloj y retorno del bootloader instalado |
| XIP | `NOT_EVIDENCED` | No se ha demostrado organización ni direcciones XIP compatibles |
| FLASH | Bloqueado | Riesgo para bootloader, DARK-1, datos de fábrica y recuperación |
| DESCONOCIDO | Bloqueado | Falta evidencia de formato y método |

## Rangos de referencia

- APP1: `[0x01C30000, 0x01C7FD80)`.
- BSS del artefacto: desde `0x01C16800`, antes de `0x01C1E000`.
- Pilas documentadas: `0x01C1F800` y `0x01C20000`.

Estas direcciones proceden del binario y su linker, no de una medición de la unidad física. No se debe enviar `0x25` ni `0x30`.

## Criterio futuro

Solo una revisión independiente del mapa RAM, ABI, estado del bootloader, apagado/retorno y recuperación podría convertir una simulación en una propuesta de prueba RAM. Ningún firmware completo DARK-1/tercero se presume relocable o ejecutable desde SRAM.
