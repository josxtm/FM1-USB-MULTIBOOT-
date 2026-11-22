# FM1CAT v1 — matriz de conformidad extendida (5C.2)

La fuente de verdad sigue siendo `docs/FM1CAT-v1.md`. C99 (`src/embedded_multiboot.c`) y Android (`FirmwareCatalog.kt`) consumen los mismos fixtures bajo `tests/fixtures/fm1cat/`; Android los ejecuta en `FirmwareCatalogConformanceTest` y el núcleo C ejercita las mismas reglas en sus pruebas host.

| Caso | Resultado esperado |
|---|---|
| Cabeceras exactas `FM1CAT|1` y `ID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT` | aceptar |
| Catálogo vacío, LF | aceptar |
| Registro válido, SHA-256 hexadecimal minúsculo | aceptar |
| CRLF | aceptar; se normaliza sin cambiar campos |
| 64 registros únicos | aceptar; el registro 65 se rechaza |
| ID duplicado | rechazar (`invalid-duplicate.csv`) |
| `../`, separadores o nombre distinto de `ID.BIN`/`ID.FWS` | rechazar (`invalid-path.csv`) |
| LABEL 1–24, ASCII `[A-Za-z0-9 _.-]` | aceptar; control, `|`, no ASCII o longitud extrema inválida: rechazar |
| VERSION 1–16, ASCII `[A-Za-z0-9._-]` | aceptar; vacío, control, `|`, no ASCII o exceso: rechazar |
| SIZE decimal 1..4 MiB | aceptar; cero, negativo, no decimal o exceso: rechazar |
| SHA-256 exactamente 64 `[0-9a-f]` | aceptar; mayúsculas, longitud errónea o truncado: rechazar |
| Archivo ausente, truncado o con bytes extra | no es un error de metadata: verificación marca incompatible/error |
| Entrada sin salto final | el parser C la rechaza; el formato exportado usa líneas terminadas |
| Catálogo > 24 KiB | rechazar antes de mantener datos fuera del límite |

La ruta recibida por la HAL no es la del catálogo: el núcleo deriva exclusivamente `FM1/FW/<FILE>` después de validar el nombre. No se acepta una ruta absoluta, traversal ni un nombre con separadores. EOF es distinto de `OK`, missing, timeout y disconnect. La conformidad no concede compatibilidad de ejecución: `UNVERIFIED` permanece automático y `mb_prepare_launch()` sigue bloqueado.

La validación Kotlin conserva el mismo contrato de LABEL/VERSION y límites. La prueba Android de fixtures busca el directorio compartido desde el directorio de Gradle; por tanto no existe una copia divergente del catálogo de prueba. La cobertura de tamaños de imagen y exceso de bytes se completa en las pruebas C de verificación mediante almacenamiento simulado.
