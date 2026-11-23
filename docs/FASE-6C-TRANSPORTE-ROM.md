# Fase 6C · Transporte ROM de parada estricta

El motor construye CBW y comprueba CSW sobre un backend íntegramente en memoria. Sólo admite el APP1 6A por tamaño exacto, SHA-256, placa y CRC16. No lleva dependencias ni adaptadores de USB. El preflight 6B ejecuta este motor únicamente tras verificar las huellas del paquete, el APP1 y el ELF. Su decisión física sigue en `BLOCKED`. [Auditoría de referencia](FASE-6B-MFLASHER-AUDIT.md)

| Etapa | Condición para avanzar |
|---|---|
| Cualificación | Una única unidad, perfil descriptor `4C4A:8057` MSC/BOT y observación independiente de ROM fría. Estos hechos son fixtures del modelo. La identificación INQUIRY y la negativa FC40 no sustituyen por sí solas la cualificación. |
| SCSI INQUIRY | CDB `12 00 00 00 24 00`, 36 bytes, estado válido, `WL82 / UBOOT1.00`. |
| FC40 | Una única negativa inequívoca del perfil conservador: estado BOT 1, residuo 16 y fase de datos vacía. MFLR reconocido, éxito desconocido, timeout y longitud incorrecta cierran la sesión. El perfil debe contrastarse con hardware en una fase autorizada. |
| FB06 | Ocho bloques de 512 B y uno de 4 B, en orden desde `0x01C30000`, fin exclusivo `0x01C31004`. Codificación ROM por bloque y CRC del contenido transmitido. Los bytes de CBW, opcode, LUN, flags, longitudes, dirección, relleno y datos deben coincidir. |
| Confirmación | CBW completo, conteo OUT exacto, firma y tag de CSW, estado 0, residuo cero y longitud esperada. El modelo reconstruye SRAM y comprueba el SHA-256 completo antes de responder al salto. |
| FB08 | Un intento a `0x01C30000` con argumento 1 tras nueve bloques válidos. Se requieren eco correcto y fase IN completa. La pérdida del acuse se registra como ejecución sin confirmar. |

El motor consume la sesión antes de la primera comprobación y la cierra ante cualquier resultado. Incluso las llamadas concurrentes sólo obtienen una sesión. La lista positiva se limita a INQUIRY, FC40, FB06 y FB08 con argumentos fijados. No hay CDB genérico, comandos Flash, recuperación automática BOT, reinicio ni reintentos. El resultado `JUMP_ACKNOWLEDGED_IN_MODEL` describe un intercambio sintetizado. El modelo no ejecuta instrucciones pi32v2. No habilita un lanzamiento ni acredita retorno de DARK-1.

```sh
python3 scripts/strict_rom_transport.py fm1-rom-hardware-test.bin
python3 scripts/strict_rom_transport.py fm1-rom-hardware-test.bin --fault short-out
python3 -m unittest discover -s tests -p '*test.py'
```

CI y las pruebas locales ejercitan 60 pruebas Python, ocho nuevas de transporte con matrices adversariales. Cubren mutación de cada byte CBW y CDB prohibidos, tamaño/dirección/CRC, identidad ambigua, residente, CSW inválido, longitud corta, residuo, timeout, desconexión, salto sin acuse y consumo concurrente de sesión. La CI comprueba también el candidato 6A exacto y ejecuta las regresiones nativas. El vector de transformación se obtuvo con el algoritmo CRC16 bit a bit de la revisión auditada. [M‑Flasher fijado](https://github.com/jvitkauskas/m-flasher/blob/8fbece7348d758ac284325eed455781675ac95ff/src/jl.rs)

**Implementado y validado en software.** Quedan bloqueados un backend físico y la cualificación ROM en el anfitrión real. Antes de un ensayo deben existir autorización específica, un transporte con estas mismas garantías, recuperación comprobada y evidencias físicas según el [plan 6B](FASE-6B-PHYSICAL-TEST-PLAN.md).
