# FASE 5X — diagnóstico no destructivo, recuperación y validación técnica

## Resultado ejecutivo

FASE 5X prepara herramientas y procedimientos reproducibles para una futura identificación sólo de lectura, pero no obtiene nueva evidencia física porque no se conectó ni operó el FM-1.

La conclusión arquitectónica no cambia:

> **NO-GO para instalar firmware y recuperar autónomamente el selector mediante USB-C sin ordenador/móvil o modificación física.**

La fase sí mejora la preparación técnica: ahora existe un auditor offline de respaldos raw, una política Flash fail-closed, validación de formatos read-only y un procedimiento de diagnóstico separado de cualquier comando destructivo.

## Herramientas preparadas

### `scripts/audit_flash_backup.py`

Auditor offline que sólo abre un archivo host y:

- exige un respaldo completo de 1 MiB para un análisis completo;
- calcula SHA-256 y CRC32;
- valida la cabecera Flash cifrada con ENC `0xFFFF` y su CRC16/XMODEM;
- enumera entradas de tabla del boot area cuando sus CRC son válidos;
- marca el app table como desconocido cuando falta el chip key de la unidad;
- identifica explícitamente boot area `[0x00000,0x04000)` y key/MAC `[0xFF000,0x100000)`;
- conserva advertencias sobre restaurabilidad, cifrado y compatibilidad;
- nunca abre USB/MIDI, nunca envía opcodes y nunca escribe el respaldo.

La herramienta no descifra datos app con una clave inventada y no afirma que un archivo íntegro sea restaurable.

### Instalador virtual

`scripts/virtual_flash_installer.py` permanece fail-closed:

- `FlashRegionPolicy()` no autoriza rangos por defecto;
- los perfiles con `[0x4000,0xFF000)` son explícitamente sintéticos;
- boot area, configuración, calibración, MAC y áreas no autorizadas quedan protegidas;
- APP1 no se acepta como imagen Flash;
- sólo un `flash.bin` raw explícitamente identificado entra en el modelo;
- cortes, corrupción, verificación y `ALREADY_INSTALLED` continúan simulados.

### Modelo de recuperación

`scripts/boot_recovery_model.py` sigue rechazando una recuperación autónoma exitosa en el perfil real. Distingue aplicación instalada, corrupción, corte, stage-2 SPI temporal, ausencia de microSD y USB-C preboot no demostrado.

## Evidencia sobre el bootloader

La única evidencia física de la unidad continúa siendo la identidad obtenida por MIDI:

- m-bootloader `2.4-395E6CC`;
- protocolo 1;
- capacidad anunciada 1 MiB;
- JEDEC `0x856014`;
- RAM anunciada.

No se comprobó físicamente la tabla Flash, el app table, el chip key, la configuración, calibración, `VM`, `BTIF`, `USR`, el menú, stage-2 ni el modo ROM USB.

La evidencia pública de m-bootloader/M-Flasher permite preparar el diagnóstico, pero no sustituye una lectura autorizada de esta unidad. El contrato documentado mantiene separadas:

- stage-2 SPI/m-card;
- run-ram con descriptor ROM;
- aplicación Flash/XIP;
- ROM USB download y flash loader host.

Nuestro startup experimental no se declara compatible automáticamente con ninguno de ellos.

## Riesgo de opcodes

El proyecto no añade ni habilita comandos físicos de lectura, erase o programación.

- `0x12` sigue siendo la única consulta física previamente realizada y sólo devuelve identidad/protocolo.
- `0x21` aparece asociado a operaciones sensibles de actualización/Flash en la documentación pública y permanece bloqueado.
- `0x23` no tiene un contrato de lectura seguro establecido en el transporte utilizado por este proyecto; permanece bloqueado.
- La existencia de comandos ROM/flash-loader en una especificación no autoriza enviarlos al FM-1 instalado.
- No se debe probar un opcode ambiguo para “ver qué responde”: la respuesta podría depender del modo ROM, del loader o del firmware.

Antes de cualquier operación futura habría que demostrar, fuera del dispositivo, framing, dirección, longitud, sentido, CRC, límites, modo de entrada y comportamiento de rechazo. Esta fase no cruza ese umbral.

## Procedimiento físico propuesto, sólo para aprobación posterior

No es una instrucción de ejecución actual. Es un checklist que debe ser revisado y autorizado independientemente.

### Gate 0 — preparación

1. Confirmar recuperación independiente disponible.
2. Confirmar que el m-bootloader/DARK-1 originales no se modificarán.
3. Registrar unidad, versión, capacidad y JEDEC ya obtenidos.
4. Preparar alimentación y cableado aprobados; no conectar lector USB-C.
5. Tener una copia privada y verificada del firmware oficial, sin añadirla al repositorio.

### Gate 1 — identidad no destructiva

1. Usar únicamente la consulta de identidad previamente validada.
2. Confirmar versión, protocolo, capacidad, JEDEC y UID si el procedimiento aprobado lo permite.
3. Detenerse ante cualquier respuesta distinta, timeout, enumeración inesperada o reinicio.

### Gate 2 — lectura de respaldo

1. Entrar sólo en el modo oficial de descarga/backup documentado por el bootloader o M-Flasher.
2. Confirmar previamente que el comando de lectura no comparte framing con erase/write.
3. Leer un respaldo completo de 1 MiB en host, sin modificar el dispositivo.
4. Calcular SHA-256 y CRC32 del archivo.
5. Ejecutar `audit_flash_backup.py` offline.
6. Verificar cabecera, tabla, regiones protegidas y tamaño.
7. Guardar el respaldo privado con UID, SHA-256 y fecha.

Si no puede verificarse que la operación sea sólo lectura, el procedimiento se detiene y no se envía.

### Gate 3 — ninguna instalación

La obtención de un respaldo válido no autoriza instalar nuestro selector ni firmware. Primero deben confirmarse mapa, tabla de aplicación, formato, caller, recovery y procedimiento de restauración.

## Respaldo y restauración

Un archivo DARK-1 o `flash.bin` descargado de una fuente oficial no demuestra restaurabilidad de esta unidad. La restauración futura tendría que comprobar:

- misma unidad o compatibilidad explícita;
- preservación de chip key, configuración y calibración;
- bootloader operativo después de la operación;
- CRC/readback posterior;
- comportamiento del menú y modo ROM;
- recuperación ante interrupción.

No se ejecutó restauración real ni se implementó un writer.

## USB-C Host

Para pasar a una prueba únicamente de lectura faltan evidencias de:

- rol USB Host de la placa;
- estado CC y orientación;
- generación y límite de VBUS;
- ausencia de backfeed;
- alimentación del lector y hub;
- pinmux y reloj HUSB;
- protección ESD y comportamiento ante desconexión;
- enumeración con un dispositivo conocido;
- separación segura entre el USB-C original y ROM USB download.

El driver HUSB y la simulación MSC/BOT prueban software, no electricidad ni topología de la placa. No se activó USB Host y no se conectó un lector.

## Contratos de ejecución

Se mantienen tres categorías independientes:

| Categoría | Estado |
|---|---|
| Stage-2 SPI/m-card | Documentado externamente como ruta temporal; no probado en esta unidad |
| Run-RAM | Contrato de descriptor ROM; no retorno normal; nuestro startup no está aprobado |
| Flash/XIP | Requiere tabla, cifrado, entry, SRAM, watchdog, reloj y recovery; no validado |

Compilar para pi32v2 sólo demuestra compilación. No se ejecutó APP1, no se envió RAM y `MB_LAUNCH_BLOCKED` permanece.

## Pruebas

Ejecutadas correctamente:

```text
make flash-backup-audit-test
make boot-recovery-test
make virtual-installer-test
make firmware-tools-test
make test
python3 -m unittest discover -s tests -p '*test.py'
```

Resultado: **17 tests Python OK**, además de la prueba C MSC/FAT32 y la suite existente.

Los cuatro workflows pasaron sobre el commit de implementación `870b680c81ac30385f7a38938059c5cdc67a12d2`:

- Embedded multiboot core — `45066724532` — success.
- Cover Flow simulator — `45066728549` — success, sólo regresión.
- FM-1 RAM diagnostics — `45066732256` — success.
- Android RAM Loader — `45066735861` — success.

Ningún workflow implica operación física.

## Bloqueos y evidencia faltante

| Bloqueo | Evidencia que falta |
|---|---|
| Tabla Flash de esta unidad | respaldo raw completo validado offline |
| Chip key/app table | lectura autorizada y parser compatible |
| Regiones fábrica/calibración/USR | tabla y backup comparado |
| Recovery autónomo | prueba documental/física del menú y entrada al selector |
| Stage-2 propio | contrato exacto de `MENU.BIN`, entry, RAM y retorno |
| USB-C Host | revisión eléctrica, VBUS/CC, rol, enumeración |
| Instalación segura | formato oficial, plan de sectores y recuperación independiente |
| Ejecución selector | caller, SRAM, stack, reloj, watchdog y retorno |

## Decisión y FASE 5Y

**NO-GO** para instalar firmware físicamente o prometer retorno autónomo al selector.

La siguiente fase sólo debe comenzar si aparece una de estas evidencias reproducibles:

1. respaldo completo obtenido mediante una lectura oficialmente validada;
2. contrato verificable para un stage-2 propio y temporal;
3. mecanismo documentado de recovery hacia el selector;
4. evidencia eléctrica aprobada para una lectura USB-C exclusivamente controlada.

Mientras ninguna exista, el siguiente experimento mínimo recomendado es únicamente revisar y aprobar el procedimiento de identificación/backup con un responsable de hardware. No ejecutar comandos ambiguos, no activar USB Host y no escribir Flash.

## Estado de entrega

- Rama: `goose/fase3b-4a-preflight`.
- Commit de implementación validado: `870b680c81ac30385f7a38938059c5cdc67a12d2`.
- PR #8: OPEN/Draft, sin merge.
- `main`: intacto.
- DARK-1 y m-bootloader: intactos.
- Flash física: sin acceso de lectura/escritura en esta fase.
- Selector textual, PRESETS, FM1CAT, MSC/BOT/FAT32 y pi32v2: conservados.
