# FASE 5V — arquitectura de arranque y recuperación autónoma

## Resumen ejecutivo y decisión

La auditoría de las fuentes públicas actuales mejora de forma importante el conocimiento del formato y del bootloader, pero **no habilita un multiboot autónomo con retorno al selector bajo todas las restricciones solicitadas**.

**Decisión: NO-GO para selector persistente autónomo por USB-C después de reemplazar la aplicación.**

Sí existe una ruta documentada para un selector temporal mediante stage-2 desde microSD SPI/m-card, y existe recuperación del bootloader mediante menú, ROM USB y USB_KEY. Ninguna equivale a un selector residente persistente accesible desde un lector USB-C sin ordenador/móvil.

No se ejecutó hardware, no se escribió Flash y `MB_LAUNCH_BLOCKED` continúa vigente.

## Nueva evidencia documental

Se revisaron las fuentes públicas originales:

- [m-bootloader SPEC](https://github.com/jvitkauskas/m-bootloader/blob/main/docs/SPEC.md)
- [m-flasher formats.rs](https://github.com/jvitkauskas/m-flasher/blob/main/src/formats.rs)
- [m-card](https://github.com/jvitkauskas/m-card)

Estas fuentes describen AC791N/pi32v2, layouts, hand-offs de RAM, recuperación, formatos y la modificación m-card. Son evidencia documental de esos proyectos y versiones; no son una prueba adicional de la unidad física del propietario ni del comportamiento exacto de su `2.4-395E6CC`.

## Arquitectura de arranque auditada

El SPEC público describe:

1. máscara ROM que localiza y carga el bootloader desde SPI Flash;
2. bootloader que valida la tabla de aplicación y el CRC de los archivos;
3. arranque XIP de la aplicación cuando es válida;
4. menú de recuperación cuando no hay aplicación, hay CRC inválido, retorno de la aplicación o fallo de actualización;
5. carga de stage-2/RAM sin escritura Flash en contextos concretos;
6. ROM USB download mode para el host M-Flasher/USB_KEY.

La documentación también indica que una aplicación que retorna puede devolver al bootloader si preserva el contrato de caller. Una aplicación que altera el entorno montado, como el firmware en Flash, debe reiniciar; `run-ram` no ofrece retorno normal. Esto no proporciona un retorno automático a nuestro selector después de instalar una aplicación distinta.

## Mapa Flash con nivel de evidencia

| Región | Evidencia pública | Decisión del proyecto |
|---|---|---|
| `0x00000–0x03FFF` | Boot area de 16 KiB, cabecera, tabla, `uboot.boot`, `isd_config.ini`/registros | Proteger siempre |
| `0x00000–0x0001F` | Cabecera Flash | No escribir |
| desde `0x00020` | Tabla de archivos del boot area | No escribir |
| `0x000A0` | Ubicación publicada del contenedor boot | No escribir |
| `0x03FA0` / `0x03FDC` | Variantes documentadas del registro/configuración; la versión depende del bootloader | Preservar registro y blob de unidad |
| `0x04000–` | App area, tabla propia, `app.bin`, `cfg_tool.bin` y descriptores `VM`, `BTIF`, `USR`, etc. | Sólo modificar mediante formato/tabla oficial; no borrar globalmente |
| hasta `0xFF000` | El loader documentado limita el tamaño de paquete y deja el key area final | No inferir que cada región app es borrable |
| `0xFF000–0xFFFFF` | `key_mac`, MAC Bluetooth y página asociada al efuse | Proteger siempre |
| regiones `VM/BTIF/USR` | Direcciones y tamaños declarados por la tabla de cada firmware | Preservar salvo autorización específica del formato |

La fuente `m-flasher` describe `flash.bin` como imagen raw desde dirección cero, ya cifrada según las reglas del dispositivo. Para FM-1, el paquete documentado aporta `0x93000` bytes; no incluye necesariamente todos los datos específicos de la unidad. `ota.bin` es otro miembro de tipo de paquete y no se puede tratar como una imagen Flash raw.

El instalador virtual se corrigió para aplicar sólo el app-prefix del `flash.bin` y preservar boot area y key area. No se afirma que un `flash.bin` genérico sea seguro para la unidad concreta sin validar sus tablas, regiones y configuración.

## Recuperación del selector

### Alternativa A — selector residente

**Resultado: no demostrada.**

Aunque el app area comienza después de `0x4000`, las fuentes no demuestran una segunda aplicación residente que el bootloader invoque antes de cualquier firmware ni una región separada de tamaño suficiente para nuestro selector y sus dependencias. Sustituir `app.bin` puede eliminar el selector anterior.

### Alternativa B — stage-2 temporal

**Resultado: parcialmente documentada, no persistente.**

El SPEC documenta `/MBOOT/MENU.BIN` en FAT32 para una tarjeta microSD conectada por SPI1 mediante una modificación tipo m-card. El stage-2 se enlaza en el contexto publicado y no escribe necesariamente Flash. Esto puede proporcionar un selector temporal mientras el bootloader lo carga, pero:

- requiere m-card/SPI, no el lector USB-C previsto;
- no deja el selector residente tras el reinicio;
- no prueba que la unidad del propietario tenga la modificación;
- no autoriza usar nuestro APP1/linker como stage-2 compatible.

### Alternativa C — USB-C antes de la aplicación

**Resultado: no demostrada y separada de SPI/m-card.**

El SPEC documenta USB ROM/download y comandos del flash loader para un host. También documenta USB asociado al bootloader/ROM, pero no demuestra que el bootloader pueda enumerar un lector USB-C MSC externo, alimentar VBUS, negociar CC ni montar FAT32 desde ese lector antes de arrancar la aplicación.

El controlador USB Host del procesador no es evidencia de que la placa FM-1 tenga rol host, alimentación, pinmux, CC/VBUS y protección eléctrica adecuados. USB-C→MSC sigue siendo una hipótesis independiente de microSD SPI/m-card.

### Alternativa D — menú de recuperación

**Resultado: recuperación del bootloader, no retorno garantizado al selector.**

El menú puede detectar aplicación ausente/corrupta y ofrece decisiones como recuperación, descarga o `BOOT ANYWAY` según el perfil documentado. Eso mantiene disponible el bootloader, pero no instala ni invoca automáticamente nuestro selector. La recuperación documentada requiere controles, ROM USB, USB_KEY o un host según el caso; no cumple por sí sola “sin ordenador ni móvil y usando lector USB-C”.

### Alternativa E — aplicación cooperativa

**Resultado: no suficiente.**

Un firmware que conozca un protocolo podría reiniciar hacia el selector o pedir recovery. Un firmware corrupto, colgado o propietario no puede asumir ese contrato. Por eso no es un mecanismo fiable autónomo.

## Compatibilidad stage-2 y RAM

Se mantienen separados los contratos:

- **stage-2:** llamada C documentada con argumento de frecuencia LSB en MHz;
- **run-ram:** descriptor/puntero ROM, contexto distinto y sin retorno normal;
- **RAM app experimental del proyecto:** startup propio, entry `0x01C30000`, no validado como ninguno de los dos contratos.

APP1 sólo demuestra trailer, CRC, board, límites y hash. No demuestra:

- entry point aceptado por el caller;
- vectores;
- reservas SRAM;
- pila;
- reloj y watchdog;
- formato de tabla de aplicación;
- cifrado/ENC de Flash;
- retorno o recuperación.

No se adaptó el startup al bootloader ni se habilitó ejecución real.

## Modelo de recuperación simulado

Se añadió `scripts/boot_recovery_model.py` con `tests/boot_recovery_model_test.py`. El perfil predeterminado sólo permite afirmar:

| Escenario | Resultado del modelo |
|---|---|
| Firmware instalado y válido | Arranca firmware instalado; selector no disponible |
| Firmware corrupto | Fallo/menú de bootloader; recuperación no declarada exitosa |
| Corte durante actualización | Recuperación no demostrada; `NO-GO` |
| microSD ausente antes de la aplicación | No hay recuperación USB-C automática |
| stage-2 m-card/SPI | Selector temporal condicional; no persistente |
| stage-2 USB-C | No soportado por evidencia actual |
| perfil hipotético con entry de recovery | `CONDITIONAL_ONLY`, nunca evidencia física |

El modelo se niega deliberadamente a reportar recuperación exitosa en el perfil real por defecto.

## Pruebas y CI

Tests locales ejecutados correctamente:

```text
make boot-recovery-test
make virtual-installer-test
make firmware-tools-test
make test
python3 -m unittest discover -s tests -p '*test.py'
```

El descubrimiento completo pasó con 14 tests Python, además de las pruebas C existentes.

Los cuatro workflows deben mantenerse verdes en el commit final de esta fase:

- Embedded multiboot core;
- Cover Flow simulator, sólo regresión histórica;
- FM-1 RAM diagnostics;
- Android RAM Loader.

La validación de CI se ejecutará después de este commit. Ningún workflow implica ejecución física, escritura Flash o USB Host real.

## Requisitos concretos para FASE 5W

1. Obtener y congelar una copia de la especificación/versión exacta del bootloader instalado.
2. Confirmar mediante evidencia segura el layout de `app.bin`, `VM`, `BTIF`, `USR`, calibración, MAC y configuración de la unidad.
3. Confirmar si el menú de recovery puede seleccionar un stage-2 propio y bajo qué contrato.
4. Separar una prueba documental de m-card/SPI de cualquier investigación USB-C.
5. Definir un protocolo de sólo lectura para identificar Flash, UID, CRC y tabla sin escribir.
6. Verificar una ruta de recuperación independiente antes de cualquier instalación.
7. Sólo después, preparar un validador del formato oficial; no convertir APP1 en formato Flash por conveniencia.
8. Mantener `MB_LAUNCH_BLOCKED` hasta demostrar caller, mapa, recovery y autorización.

## Seguridad y entrega

No se modificó `main`, DARK-1 ni m-bootloader. No se escribieron ni borraron Flash, no se ejecutó RAM/APP1, no se activó USB Host físico y no se realizó trabajo VBUS/CC.

- Rama: `goose/fase3b-4a-preflight`.
- PR #8: OPEN/Draft, sin merge.
- Decisión multiboot autónomo bajo las restricciones actuales: **NO-GO**.
- Arquitectura de investigación recomendada: selector externo/temporal documentado por SPI/m-card o recuperación oficial del bootloader, no selector USB-C residente no probado.
