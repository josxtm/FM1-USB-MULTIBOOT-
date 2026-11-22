# Matriz de compatibilidad

| Formato/firmware | Integridad | Flash/XIP | ABI | Recovery autónomo | Política |
|---|---|---|---|---|---|
| APP1 experimental | estructural | no demostrado | no demostrado | no | RAM antecedente, bloqueado |
| `flash.bin` oficial | validable con tabla/chip key | depende de unidad/tabla | bootloader/firmware específico | no demostrado | sólo auditoría/fixture |
| `.fwsc` | contenedor/CRC validables | miembros y destino dependen paquete | no genérico | no | inspección |
| `ota.bin` | miembro de paquete | updater específico | propietario | no | opaco/inspección |
| DARK-1 | firmware de prueba | no autorizado | no reutilizar APIs | no | intacto |
| Felucca/SLOOP/Melodee/X0X/Official | no inferir por nombre | individual | individual | individual | `UNVERIFIED` |

SHA-256 correcto no demuestra compatibilidad. La inclusión en FM1CAT nunca habilita ejecución o programación.
