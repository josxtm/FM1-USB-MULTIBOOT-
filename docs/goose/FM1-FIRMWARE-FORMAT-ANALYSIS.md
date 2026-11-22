# FASE 5R — análisis de formatos

## Evidencia disponible

`FM1CAT|1` define sólo metadatos: ID, etiqueta, versión, `ID.BIN`/`ID.FWS`, tamaño, SHA-256 y `UNVERIFIED`. No define cabecera ejecutable, entry, relocación ni mapa de memoria.

| Formato | Reconocimiento | Lo que demuestra | Lo que no demuestra |
|---|---|---|---|
| APP1 v1 | trailer `APP1`, CRC16, placa 1, versión 1 | estructura, hash, rango APP | caller ABI, SRAM física o ejecución |
| ELF32 pi32v2 | magic ELF y máquina 241 | contenedor/arquitectura declarada | vectores, relocación y boot contract |
| `.BIN` | extensión y bytes opacos | sólo identidad/tamaño/hash | cabecera, entry o memoria |
| `.FWS` | extensión y bytes opacos | sólo identidad/tamaño/hash | especificación de contenedor |
| desconocido | fallback | hash y tamaño | cualquier compatibilidad |

La herramienta `scripts/inspect_firmware.py` es de sólo lectura, no incorpora archivos al repositorio y genera JSON. Un BIN/FWS no se eleva a ejecutable por tener un SHA correcto. Un APP1 estructuralmente válido se marca `RAM_CANDIDATE`, no como ejecución autorizada.

No se distribuyen DARK-1 ni binarios propietarios. Melodee/Tessera sólo se referencian como investigación documental; no se copió código GPL.
