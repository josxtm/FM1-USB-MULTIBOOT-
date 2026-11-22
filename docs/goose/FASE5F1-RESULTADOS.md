# FASE 5F.1 — corrección final de CI Android

## Causa 1: CRC16

El asset APP1 actual es el binario generado después de integrar MSC/BOT:

```text
size: 10308 bytes
SHA-256: 6ac61a7664677558113774a6b4b921cde3006180a721899e04778399fc3a7d67
trailer: 41 50 50 31 f6 bd 01 01
magic: APP1
CRC trailer LE: 0xbdf6
board/version: 1/1
```

`AppImageTest` calculaba CRC sobre `size - 8`, exactamente el rango que usa `ApprovedApp.inspect()`. El resultado correcto para el binario actual es `0xbdf6`. El valor `0xc951` correspondía al CRC del binario completo incluyendo el tráiler y no era el CRC almacenado en el tráiler. Se corrigió la expectativa a `0xbdf6`; no se cambió el SHA aprobado ni el asset para ocultar el fallo.

## Causa 2: bloques fragmentados

La imagen mide 10308 bytes. Con bloques de 512 bytes:

```text
ceil(10308 / 512) = 21 bloques
20 bloques completos + 1 bloque parcial de 68 bytes
```

`RomProtocolTest` esperaba 20 escrituras y 20 comandos `0xfb06`, omitiendo el último bloque parcial. Se corrigió a 21. Se conservaron la transferencia íntegra, los límites del slot, CRC, comandos y exactamente un salto `0xfb08`.

No se generó una APK nueva. No hubo cambios físicos ni de seguridad.
