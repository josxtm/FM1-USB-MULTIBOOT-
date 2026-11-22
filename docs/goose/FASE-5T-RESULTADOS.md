# FASE 5T — arquitectura microSD → Flash permanente y recuperación

## Estado y alcance

FASE 5T deja implementado y probado el **flujo virtual** de instalación. No habilita programación Flash, ejecución de firmware ni USB Host físico. La arquitectura objetivo microSD → instalación persistente → reinicio es una especificación de diseño, no una capacidad demostrada del FM-1.

Se conservaron FM1CAT, MSC/BOT read-only, FAT32, SHA-256, selector textual, PRESETS, pi32v2, simuladores, `MB_LAUNCH_BLOCKED` y la protección del m-bootloader 2.4.

## Arquitectura de arranque seleccionada

La arquitectura segura actual es:

```text
USB-C + lector microSD (futuro, sin activar)
  → USB0/HUSB → MSC/BOT read-only
  → FAT32 → FM1/INDEX.CSV + FM1/FW/<file>
  → selector textual PRESETS/PLAY
  → inspección y SHA-256
  → planificador de instalación virtual
  → MB_LAUNCH_BLOCKED
```

La instalación persistente futura sólo puede ser un paso autorizado posterior a la validación del circuito, del ABI y del mapa Flash. En 5T el planificador termina en `CONFIRMATION_REQUIRED`, `COMMITTED` virtual o un estado de recuperación virtual; nunca llama a Flash real.

### Conflicto del selector residente

La evidencia existente del m-bootloader `2.4-395E6CC` confirma el protocolo de actualización y la protección operacional acordada, pero no proporciona un mapa público completo que demuestre simultáneamente:

- región de aplicación convencional;
- área reservada para un selector residente;
- vector o entrada de recuperación hacia ese selector;
- rollback físico transaccional;
- contrato para que una aplicación instalada devuelva el control al selector.

El perfil virtual usa `[0x0000,0x4000)` como región protegida y un área de aplicación virtual desde `0x4000` hasta `0x100000`. Esto es una **hipótesis de simulación**, no un mapa confirmado del dispositivo.

Si la aplicación nueva ocupa la única región arrancable, el selector anterior queda sobrescrito. El firmware recién instalado tampoco puede garantizar el regreso al selector si no coopera. Por ello no se declara viable un selector autónomo persistente en la Flash actual. Las arquitecturas que sí lo permitirían son:

1. un selector residente en una región documentada y no reemplazada, con ABI de salto y mecanismo de recuperación probados;
2. un bootloader que ofrezca una entrada de recuperación documentada hacia el selector;
3. reinstalación externa del selector mediante el flujo oficial del bootloader.

Ninguna de las tres está demostrada para el FM-1 instalado. La simulación de rollback no equivale a rollback físico.

## Instalador virtual

Se añadió `scripts/virtual_flash_installer.py`. Opera únicamente sobre una `bytearray` de 1 MiB y:

- acepta exclusivamente APP1-v1 estructuralmente válido para FM-1;
- comprueba tamaño, board, versión, CRC16-XMODEM y límites del área de aplicación;
- rechaza BIN/FWS opacos aunque la extensión coincida;
- permite comparar el SHA-256 esperado procedente del catálogo;
- requiere confirmación explícita antes de modificar la Flash virtual;
- simula PREPARED, ERASED, PROGRAMMED, VERIFIED y COMMITTED;
- detecta imagen ya instalada y devuelve `ALREADY_INSTALLED`;
- simula cortes antes del commit, después de erase y después de programación;
- simula corrupción posterior a la programación;
- verifica la región protegida en toda transacción;
- distingue `RECOVERED` de `RECOVERY_REQUIRED` cuando no existe backup virtual;
- devuelve un reinicio simulado con `selector_available: false`, sin inventar un selector residente.

Los estados de recuperación de la herramienta no constituyen una garantía sobre la Flash física. En particular, el backup usado por la prueba vive en RAM del proceso y no representa una segunda región disponible en el FM-1.

Los formatos `.BIN` y `.FWS` sin APP1 permanecen opacos. No se implementó autodetección ejecutable de `.fwsc`: la especificación pública suficiente para validar entry point, destino, relocaciones, ABI y recuperación no está disponible.

## Interfaz y estados

La interfaz de producción continúa siendo la lista textual de 240×240:

- PRESETS: anterior/siguiente;
- PLAY: verifica y consulta la política;
- REC/CANCEL: vuelve a la lista en estados seguros;
- footer `PRESETS: ELEGIR` y `PLAY: INICIAR`.

En esta fase la política debe mostrar estados diferenciados sin sugerir una instalación real: archivo seleccionado, verificación, incompatibilidad, confirmación requerida, instalación simulada, verificación posterior, error y recuperación necesaria. El renderer existente muestra los estados de verificación/bloqueo; el instalador virtual expone los estados transaccionales en JSON para pruebas y futura integración visual.

## Memoria

La imagen 5S.1 mide `.image=23724`, APP1 `23732` y BSS `25896` bytes. El instalador virtual es una herramienta host y no añade buffers de firmware al selector pi32v2. No se reserva espacio RAM para imágenes completas: la verificación usa el camino de lectura acotado existente y el instalador virtual usa memoria del proceso de prueba.

La RAM del sintetizador sigue siendo una restricción independiente. La medición del linker pi32v2 no demuestra que el firmware propietario conserve suficientes buffers DSP/audio; faltan mapa SRAM, contrato de stack, watchdog, reloj y ABI de cada imagen candidata.

## Pruebas

`make virtual-installer-test` cubre:

- biblioteca/imagen inválida y BIN opaco;
- CRC incorrecto;
- confirmación ausente;
- instalación válida y SHA esperado;
- firmware ya instalado;
- reinicio simulado sin selector residente;
- corte antes del commit;
- corte después de erase;
- corte después de programación;
- corrupción posterior a programación;
- recuperación no disponible;
- protección de `[0x0000,0x4000)`.

Las pruebas existentes mantienen los casos de catálogo 0/1/2/3/64, FAT32 fragmentado, desconexión, timeout, hash incorrecto, USB emulado, PRESETS y PLAY bloqueado. El recorrido completo en software es:

```text
microSD emulada → MSC/BOT → FAT32 → FM1CAT → lista
→ PRESETS → PLAY → SHA-256 → política
→ instalador virtual → reinicio simulado → recuperación clasificada
```

## Compatibilidad y bloqueos técnicos

Un SHA-256 correcto sólo demuestra integridad del archivo. No demuestra entry point, vectores, ABI, SRAM, reloj, watchdog, XIP, instalación ni capacidad de retorno. APP1 propio es estructuralmente reconocible y queda como candidato de simulación/inspección; BIN/FWS y `.fwsc` no autorizan ejecución por nombre.

Pendiente para una primera prueba física segura:

1. confirmar el circuito USB-C, VBUS, CC, alimentación, rol y ausencia de backfeed;
2. confirmar enumeración del lector con una prueba eléctrica autorizada y limitada;
3. obtener mapa Flash/retained region del bootloader instalado;
4. obtener contrato documentado de entrada, retorno y recuperación;
5. confirmar SRAM, stack, reloj y watchdog del firmware candidato;
6. definir una prueba de lectura solamente antes de cualquier operación de instalación;
7. obtener autorización explícita y un procedimiento de recuperación independiente antes de programar.

No se debe usar una copia en microSD como única garantía de recuperación: el lector puede no estar disponible antes de que arranque la aplicación instalada.

## Seguridad y recomendación

No se escribió ni borró Flash, no se activó USB Host físico, no se ejecutó código en el FM-1, no se modificó m-bootloader/DARK-1/main y no se habilitó ningún salto real.

La recomendación para la siguiente fase es resolver primero el **contrato de recuperación y el mapa Flash** mediante documentación o evidencia segura. Hasta entonces, mantener el selector externo/virtual, la instalación bloqueada y `MB_LAUNCH_BLOCKED`. No presentar `COMMITTED` virtual como una instalación persistente real.

## Entrega

- Rama: `goose/fase3b-4a-preflight`.
- PR #8: OPEN/Draft, sin merge.
- Commit de implementación validado: `07b52cd2670cdf53fcfc4713142b70f725b333ef`.
- Workflows validados sobre ese commit:
  - Embedded multiboot core — `45060432811` — success.
  - Cover Flow simulator — `45060436477` — success, sólo regresión.
  - FM-1 RAM diagnostics — `45060440205` — success.
  - Android RAM Loader — `45060444391` — success.
- El commit posterior que actualice este informe será documental; no cambia el código ejecutable validado.
