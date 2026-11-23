# Fase 6B · Resultados y decisión

| Pregunta | Resultado |
|---|---|
| A. Integridad | **VALIDADO EN SOFTWARE.** ZIP original y ocho archivos de referencia anclados por SHA-256. BIN exactamente 4.100 B, APP1, FM-1, CRC16 `0xB786`, SHA-256 `a0012d9f32b414ee38aa8b76e0f412b64a77c4163001037a44b77ac8eda91cbb`. |
| B. Memoria y ejecución | **VALIDADO EN SOFTWARE.** ELF pi32v2, entrada ROM `0x01C30000`, zona de trabajo baja `0x01C16800–0x01C18038`, dos pilas privadas e imagen sin solapamiento con las zonas reservadas. Trampolín de ROM y espera terminal preservados. |
| C. Comandos | INQUIRY `12 00 00 00 24 00`, sonda `FC40`, nueve `FB06` a SRAM y un `FB08`. Windows puede enviar otra INQUIRY al abrir. libusb tiene control BOT y limpieza de endpoints en caso de error. |
| D. Flash | La rama estándar `run-ram` y el APP1 no ordenan programación Flash. **BLOQUEADO** en el equipo hasta resolver residente, validación de longitudes y efectos del SO. |
| E. Anfitrión | Linux x86_64 local y dedicado es el recomendado. macOS y Windows tienen rutas upstream diferentes, sin verificar físicamente. Android/Termux **NO VIABLE** con este backend. |
| F. Condiciones | Autorización, copia externa recuperable, estado de arranque, menú sin actualización pendiente, alimentación estable, ROM cualificada y transporte exclusivo que aplique la lista positiva y pare en el primer fallo. |
| G. Regreso | **PENDIENTE DE ENSAYO FÍSICO.** REC o fin de sesión, apagado real, arranque sin teclas, verificar DARK-1 y controles, y arranque con PLAY para comprobar menú y versión. |
| H. microSD | USB‑C MSC sigue **BLOQUEADO**. Para SPI1 faltan m-card compatible, FFC, microSD confirmada FAT32 y validación mecánica y eléctrica. |
| I. Próximo paso | Calificar una ruta de transporte que cierre ante una sonda ambigua y valide todos los bloques antes del salto. Validar ese transporte en simulación y después aprobar por separado la sesión de hardware. |

**IMPLEMENTADO.** Preflight offline con informe JSON reproducible, comparaciones del candidato 6A, auditoría M-Flasher y simulación de la política de transporte. CI ejecuta las 34 regresiones Python anteriores y 18 pruebas nuevas, las pruebas C, el puente del STG2 y los validadores del candidato. La hoja de ensayo distingue logs automáticos de observaciones físicas. El candidato 6A permanece inalterado.

**NO-GO físico.** Ningún artefacto de esta fase autoriza el arranque en RAM. La ruta upstream puede continuar con sonda de residente ambigua y aceptar bloques cortos. Falta el transporte de parada estricta y la autorización del propietario. La prueba sobre el FM‑1 queda pendiente.

[Preflight](FASE-6B-PREFLIGHT.md) · [Auditoría](FASE-6B-MFLASHER-AUDIT.md) · [Plan de ensayo](FASE-6B-PHYSICAL-TEST-PLAN.md) · [Recuperación y registro](FASE-6B-RECOVERY.md) · [MicroSD](FASE-6B-MICROSD-HARDWARE.md)
