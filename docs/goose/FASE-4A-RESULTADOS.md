# FASE 4A — Resultados de Goose

**Rama:** `goose/fase3b-4a-preflight`  
**PR:** #8 (Draft)  
**Build Android:** `1.0-comp8`, `versionCode 8`
**Fecha:** 2026-11-21

## Actualización comp8

- Corregida la etiqueta visible residual `Comp6` en `MbootMidiActivity`.
- La compatibilidad de versión se define explícitamente como m-bootloader `2.x` con minor numérico; una respuesta `1.0` se parsea, pero queda marcada como incompatible. Ya no depende de bytes nulos de relleno.
- Añadido `MbootQueryController`, independiente de Android, con reloj/timer inyectable para probar timeout, cancelación, desconexión y respuestas obsoletas.
- Añadida `AppTransferSimulation` para transferencias fragmentadas, cancelación y errores sin USB ni comandos `0x25`/`0x30`.

## Implementado

- `MbootIdentityCollector` ahora rechaza:
  - inicio SysEx simultáneo;
  - datos después de `F7` en el mismo lote;
  - truncamiento cuando el parser recibe una trama sin `F7`;
  - tamaños fuera de rango sin aritmética de suma vulnerable;
  - respuestas de más de 192 bytes.
- Nuevas pruebas para padding 8→7 no canónico, truncamiento, bytes adicionales, fragmentación, desconexión lógica y límite de memoria.
- Nuevo `MbootIdentityEmulator`, totalmente separado del transporte USB físico. Solo acepta la consulta fija `0x12`.
- Escenarios simulados: respuesta válida, JEDEC incorrecto, versión incompatible, checksum erróneo, payload corrupto, timeout y desconexión.
- Pruebas adversariales mantienen bloqueados los comandos de Flash y carga RAM.
- Incremento de APK a comp8 y actualización del workflow para publicar APK, SHA-256, XML de tests y reporte Lint como artefactos.

## Pruebas

### Ejecutadas localmente

```sh
make clean && make test
cc -std=c99 -O1 -g -fsanitize=address,undefined -Wall -Wextra -Werror \
  -Isrc tests/usb_storage_test.c src/storage.c src/fat32.c \
  -o build/usb_storage_test_sanitize
./build/usb_storage_test_sanitize
```

Ambas pruebas del diagnóstico USB/FAT32 pasan, incluyendo timeout, CSW inválido, FAT32 MBR/volumen directo y fichero fragmentado. La ejecución ASan/UBSan también pasa.

### CI requerida

El workflow Android ejecuta:

```sh
./gradlew --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

La compilación pi32v2 y la verificación de hashes continúan en `diagnostics.yml`. En el entorno Termux no hay JDK, Android SDK ni toolchain pi32v2, por lo que esas ejecuciones no se declaran como locales. Deben verificarse en GitHub Actions después del push.

## Estado funcional

- Identidad m-bootloader por consulta `0x12`: **software verificado; hardware pendiente**.
- Emulador MIDI: **software implementado y cubierto por tests**.
- APP1/RAM: **validación estática y simulación; ejecución física bloqueada**.
- USB Host/microSD: **simulación de solo lectura; VBUS/CC y lector real pendientes**.
- Cover Flow: **simulador sin ejecución de firmware**.
- Flash, bootloader, región `[0x00000,0x04000)` y DARK-1: **sin modificaciones**.

## Riesgos pendientes

El checksum de m-bootloader no cubre el opcode: el transporte físico sigue restringido a la consulta fija y no se implementan lectura Flash, borrado, programación, `0x25` ni `0x30`. La validación de software no prueba que el mapa RAM esté libre en el bootloader instalado ni proporciona recuperación integral.

## Pruebas físicas necesarias antes de una fase posterior

1. Confirmar la respuesta `0x12` en MIDI MODE y volver a DARK-1 mediante BOOT APP.
2. Exportar y verificar una copia íntegra de Flash, con restauración demostrada.
3. Caracterizar VBUS, CC y protección de retorno antes de conectar un lector.
4. Validar reservas RAM y ABI del m-bootloader instalado mediante revisión independiente.
5. Mantener cualquier ensayo futuro limitado a RAM, sin tocar la región protegida ni programar Flash.
