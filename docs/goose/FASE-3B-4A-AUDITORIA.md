# Auditoría FASE 3B → 4A — Goose

**Rama:** `goose/fase3b-4a-preflight`  
**Base:** `origin/feat/mboot24-midi-ram-android` / PR #7 (`78564ab`)  
**Fecha:** 2026-11-21  
**Alcance:** revisión estática y pruebas ejecutables disponibles en este entorno.

## Salvaguardas aplicadas

- La auditoría comenzó con `git status`, remoto, rama y commits. El directorio estaba limpio; no se modificó `main` ni se descartaron cambios.
- No se envían comandos físicos de lectura Flash, borrado, carga RAM o ejecución. La pantalla MIDI solo construye la consulta fija `0x12`.
- Se mantienen bloqueadas las operaciones `0x21`, `0x22`, `0x23`, `0x24`, `0x25` y `0x30`.
- No se accedió al FM-1 físico. Las afirmaciones de hardware de la documentación siguen separadas de las pruebas simuladas.

## Hallazgos

### MIDI m-bootloader

**Fortalezas:** marco F0/F7, flujo 8→7 little-endian, tamaño acotado, rechazo de estados MIDI dentro de SysEx, longitud y checksum del cuerpo, comando `0x12` fijo, capacidad `0x100000`, JEDEC `0x856014`, versión imprimible y ausencia de UID/chip key en `Identity` y logs.

**Riesgo:** el checksum del protocolo no cubre el opcode. Un cambio de un bit no debe convertirse en una orden permitida. El parser ya rechazaba comandos distintos de `0x12`; se añadió una comprobación de codificación canónica (re-encode exacto) y una regresión explícita de mutación de opcode. Esto es defensa de parser, no protección contra un dispositivo que interprete un paquete corrupto antes de responder.

**Pendiente:** comportamiento de respuesta real, fragmentación real MIDI, cancelación en el dispositivo, y confirmación física de que la opción MIDI corresponde al protocolo descrito.

### APP1 / RAM

El validador existente comprueba ELF32 pi32v2, máquina 241, entrada `0x01C30000`, dos segmentos, BSS antes de `0x01C1E000`, APP1 v1, placa 1, CRC16-XMODEM y rango `[0x01C30000,0x01C7FD80)`. Android además exige el SHA-256 del artefacto experimental aprobado. Esto es una política conservadora, no una prueba de que la ventana esté libre en un bootloader concreto.

No existe evidencia suficiente para habilitar `0x25`/`0x30`: faltan reservas SRAM confirmadas, ABI de transición, relojes/periféricos y retorno reproducible. Permanecen bloqueados.

### USB Host / MSC / FAT32

El código simula enumeración full-speed, MSC BOT, INQUIRY/REQUEST SENSE/READ CAPACITY/READ(10), MBR o volumen directo FAT32 y lectura acotada de `FM1/INDEX.CSV`. El único OUT es un CBW y no hay escritura de sectores. Hay límites de descriptor, bloques, cadenas FAT, directorios y preview.

El código no demuestra VBUS, CC, protección contra retorno, lector real, GPT/exFAT, hubs, UASP, nombres largos ni acceso físico. No se debe conectar un lector ni inyectar alimentación sin caracterización de placa.

### Catálogo / Android / Cover Flow

SAF exporta bytes sin transformarlos, relee SHA-256, evita sobrescritura y genera `FM1/INDEX.CSV`. Las pruebas de catálogo rechazan nombres/rutas peligrosas y estados no verificados. El simulador no ejecuta firmware. La APK comunica identificación MIDI y estados de software; no presenta carga RAM física como operativa.

## Riesgos no resueltos

1. Restauración integral de Flash no verificada; proteger `[0x00000,0x04000)` y DARK-1.
2. El formato APP1 y el mapa RAM dependen de referencias externas y no de una medición de la unidad instalada.
3. Un timeout o desconexión física puede dejar al dispositivo en un estado no observado; no se reintentan operaciones peligrosas.
4. La compilación Android requiere JDK/SDK no instalados en este entorno.
5. El simulador web requiere Node/npm, también ausentes.

## Archivos modificados en esta fase

- `src/mbl.h`: no redefine `NULL` cuando el host ya lo proporciona; corrige la compilación de tests con Clang.
- `android/.../MbootIdentityProtocol.kt`: rechaza codificaciones 8→7 no canónicas antes de aceptar una respuesta.
- `android/.../MbootIdentityProtocolTest.kt`: regresión de mutación de opcode y checksum no protector.
- `docs/goose/FASE-3B-4A-AUDITORIA.md` y `docs/goose/RAM-APP1-VALIDACION.md`.

## Actualización FASE 4A

Se añadió un emulador independiente del transporte físico y cobertura de padding no canónico, truncamiento, bytes adicionales, inicios simultáneos, límites de memoria, timeout y desconexión lógica. La aplicación evolucionó posteriormente a comp8; los resultados detallados están en `docs/goose/FASE-4A-RESULTADOS.md`.

## Resultado y siguiente paso

Software seguro para identificación `0x12`, catálogo y simulación: **implementado/verificable en software**. MIDI físico, APP1 físico, USB Host físico y multiboot: **pendiente de validación**. La siguiente fase requiere primero preservar/exportar una copia íntegra verificable, caracterizar USB-C/VBUS/CC y obtener autorización explícita para una prueba RAM limitada; no autoriza Flash ni sustitución de DARK-1.
