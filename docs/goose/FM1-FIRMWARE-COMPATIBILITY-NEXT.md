# Preparación FASE 5R — compatibilidad de firmwares

La matriz de compatibilidad conserva una separación estricta entre integridad y ejecutabilidad:

| Familia/formato | Evidencia útil | Decisión actual |
|---|---|---|
| DARK-1 | nombre/identidad documentada; binario propietario no incorporado | inspección solamente |
| Melodee | fuentes públicas GPL-3.0 y contratos de hardware distintos | adaptación requerida; no copiar código |
| Tessera | evidencia pública/documental separada | inspección/simulación |
| BIN/FWS | FM1CAT, tamaño y SHA | verificación de integridad, no ejecución |
| APP1 | trailer, CRC, SHA, máquina y rango | simulación estructural solamente |

Para cada futuro firmware se deben obtener formato, cabecera, regiones, entry, relocación/XIP, tamaño, reloj, stack, vectores, dependencias y licencia. Ninguna SHA valida por sí sola la ABI. No se distribuyen binarios propietarios ni se copian fuentes GPL incompatibles.

Las decisiones posibles siguen siendo RAM, XIP, adaptación o instalación controlada; ninguna ruta de lanzamiento se implementa mientras el ABI y la recuperación no estén demostrados. `MB_LAUNCH_BLOCKED` permanece permanente.
