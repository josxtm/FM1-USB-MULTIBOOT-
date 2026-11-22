# FASE 5U — corrección de regiones Flash, formatos y recuperación

## Estado

FASE 5U corrige el supuesto peligroso de FASE 5T: ya no se borra por defecto todo `0x4000–0x100000`. El instalador virtual ahora exige una política explícita de regiones autorizadas. Con la información pública disponible, la política predeterminada no autoriza ningún borrado.

Se mantienen el selector textual PRESETS/PLAY, FM1CAT, USB MSC/BOT read-only, FAT32, SHA-256, pi32v2, `MB_LAUNCH_BLOCKED`, los simuladores anteriores y la protección del m-bootloader 2.4.

## Mapa Flash y evidencias

| Región | Estado | Tratamiento |
|---|---|---|
| `0x0000–0x3FFF` | Región protegida usada por el modelo previo y compatible con la reserva operacional del bootloader | Siempre protegida; nunca se borra |
| `0x4000–0x100000` | Mapa físico detallado no demostrado | Protegida por defecto; no se borra |
| Área de aplicación real | Desconocida para la unidad instalada | Sólo puede habilitarse mediante una política explícita basada en evidencia futura |
| Fábrica/calibración/identificadores | Límites y offsets no publicados de forma suficiente | Se consideran protegidos/desconocidos |
| Configuración/usuario | Límites y esquema no demostrados | Se consideran protegidos/desconocidos |
| Región de backup/rollback | No demostrada | No se asume existente |

El valor de 1 MiB y la reserva inicial de `0x4000` son parámetros del modelo de trabajo, no una confirmación de que todos los bytes posteriores sean aplicación. El perfil de test usa artificialmente `0x8000–0xF0000` como región autorizada y deja `0x4000–0x7FFF` y `0xF0000–0xFFFFF` protegidas; esto sólo demuestra el mecanismo, no propone offsets físicos.

## Corrección del instalador

`scripts/virtual_flash_installer.py` incorpora `FlashRegionPolicy`:

- la política por defecto tiene cero regiones autorizadas;
- cualquier región autorizada debe ser explícita y no puede solaparse con `0x0000–0x3FFF`;
- el borrado se limita únicamente al intervalo autorizado que contiene la imagen;
- el backup virtual cubre sólo el destino autorizado;
- todos los complementos de la Flash permanecen protegidos;
- se verifica la preservación de bootloader, fábrica, calibración, identificadores y configuración simulados;
- `ALREADY_INSTALLED` evita erase/program cuando el contenido coincide;
- se mantienen confirmación, SHA, CRC, cortes, corrupción, verificación posterior y recuperación virtual;
- una política sin mapa produce `REJECTED: no documented authorized erase region`.

Esto elimina la posibilidad de interpretar la simulación como autorización para borrar toda la Flash. La bytearray sigue siendo exclusivamente virtual.

## Formatos reales y compatibilidad

El inspector read-only ahora distingue conservadoramente:

- `APP1-v1`: formato experimental del proyecto; CRC, board y trailer pueden validarse estructuralmente, pero no prueban que sea firmware Flash arrancable;
- `ELF`: inspección de magic/machine, sin autorización de ejecución;
- `.BIN`: `BIN-OPAQUE` salvo que contenga una estructura reconocida independiente;
- `.FWS`: `FWS-OPAQUE`;
- `.fwsc`: `FWSC-OPAQUE`;
- `flash.bin`: `FLASH.BIN-OPAQUE`;
- `ota.bin`: `OTA.BIN-OPAQUE`.

Para los últimos contenedores faltan especificaciones públicas suficientes sobre firma, tabla de destinos, versión, entry point, ABI, layout, regiones reservadas y recuperación. Los nombres no se convierten en permiso de instalación. No se almacenó firmware propietario ni información privada del dispositivo.

APP1 continúa siendo candidato de RAM/inspección según la política previa, no candidato automático de Flash. La clasificación de integridad no equivale a compatibilidad de arranque.

## Sustitución y recuperación

El modelo virtual sigue el flujo:

```text
microSD emulada → FM1CAT → selección → SHA/compatibilidad
→ confirmación → erase sólo autorizado → program → verify
→ reboot simulado → clasificación de recuperación
```

El reinicio simulado no inventa un selector residente. El m-bootloader permanece intacto en el modelo, pero no existe evidencia suficiente de que el bootloader 2.4 pueda devolver el control a un selector instalado sin cooperación del firmware. Tampoco se demostró una ruta USB-C original que proporcione recuperación autónoma sin ordenador o móvil.

Las capacidades asociadas a modificación SPI, extracción o herramientas externas no se consideran disponibles por el USB-C original. Ninguna prueba autoriza modificar SPI, m-bootloader o DARK-1.

Por tanto, estos requisitos no están demostrados simultáneamente:

1. instalar firmware completo en Flash;
2. conservar m-bootloader;
3. recuperar automáticamente el selector sin ordenador/móvil;
4. leer microSD mediante lector USB-C después del reinicio;
5. no ampliar ni modificar físicamente la memoria.

La alternativa más viable sigue siendo un flujo externo de reinstalación/recovery respaldado por el bootloader oficial, condicionado a obtener su contrato y mapa real. No es una recuperación autónoma demostrada.

## Pruebas

Se añadieron y pasan:

- política por defecto sin regiones autorizadas;
- rechazo de imagen APP1 sin región documentada;
- preservación de bootloader;
- preservación de regiones de fábrica/configuración simuladas;
- borrado limitado a la región autorizada;
- `ALREADY_INSTALLED`;
- confirmación y SHA;
- CRC incorrecto y contenedor opaco;
- cortes antes/después de erase/program;
- corrupción posterior;
- recuperación disponible y `RECOVERY_REQUIRED`;
- inspector read-only de `.fwsc`, `flash.bin` y `ota.bin`.

Comandos locales relevantes:

```text
make test
make firmware-tools-test
make virtual-installer-test
python3 -m unittest discover -s tests -p '*test.py'
```

No se activa USB Host físico y no se ejecuta programación Flash real.

## Bloqueos pendientes

- mapa Flash documentado de la unidad concreta;
- offsets reales de fábrica, calibración, configuración y aplicación;
- formato firmado de M-Flasher/`fwsc`/`flash.bin`/`ota.bin`;
- ABI de stage-2, actualización y recuperación del m-bootloader 2.4;
- existencia de una entrada autónoma al selector tras sustituir la aplicación;
- SRAM, stack, watchdog, reloj y entry point de firmware propietarios;
- circuito USB-C, VBUS, CC, rol, alimentación y ausencia de backfeed;
- procedimiento físico de sólo lectura previo a cualquier instalación.

## Siguiente fase recomendada

No habilitar todavía instalación física. La siguiente fase debe obtener primero documentación verificable del mapa Flash y del contrato de recovery, o diseñar una prueba de lectura no destructiva y autorizada. Hasta entonces, conservar la política sin regiones autorizadas, el selector externo/virtual y `MB_LAUNCH_BLOCKED`.

## Entrega

- Rama: `goose/fase3b-4a-preflight`.
- PR #8: OPEN/Draft, sin merge.
- Commit de implementación validado: `1c42d7a65e1ebcd0c405ad237eb38deb3a32abf9`.
- Workflows validados sobre ese commit:
  - Embedded multiboot core — `45061512321` — success.
  - Cover Flow simulator — `45061516324` — success, sólo regresión.
  - FM-1 RAM diagnostics — `45061520228` — success.
  - Android RAM Loader — `45061524505` — success.
- No se modificaron `main`, DARK-1 ni m-bootloader.
- No se escribió ni borró Flash física.
