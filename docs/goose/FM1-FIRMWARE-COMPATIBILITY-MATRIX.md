# Matriz de compatibilidad FASE 5R

| Clase | Reconocimiento | Evidencia mínima | Estado actual |
|---|---|---|---|
| `INSPECTION_ONLY` | formato conocido e íntegro | metadata/estructura, sin ABI | DARK-1, Melodee, Tessera, BIN/FWS |
| `RAM_CANDIDATE` | APP1/estructura RAM válida | ABI y SRAM aún deben confirmarse | APP1 experimental de este repositorio |
| `FLASH_INSTALL_CANDIDATE` | imagen íntegra y autorizada | transacción, mapa, recovery y autorización | ninguno |
| `ADAPTATION_REQUIRED` | XIP documentado | startup/adaptación pendiente | hipótesis XIP |
| `INCOMPATIBLE` | estructura o integridad inválida | fallo verificable | entradas corruptas |
| `UNKNOWN` | formato/contrato no reconocido | falta especificación | contenedores no documentados |

El motor C conserva `mb_classify_launch()` y añade nombres/razones de FASE 5R. `MB_LAUNCH_BLOCKED` sigue siendo incondicional. La clasificación explica evidencia faltante y no llama a un ejecutor.

No hay firmware real de DARK-1, Melodee o Tessera en el repositorio; por tanto su compatibilidad concreta permanece inspección documental, no ejecución demostrada.
