# FASE 5W — evidencia definitiva del bootloader, recuperación y seguridad Flash

## Resumen

FASE 5W no revoca el NO-GO de FASE 5V. La revisión de las fuentes públicas aporta detalles nuevos y verificables sobre el layout Flash, `flash.bin`, `.fwsc`, el menú de recovery y el stage-2 SPI, pero no demuestra un retorno autónomo al selector mediante USB-C después de sustituir la aplicación.

**Decisión actual: NO-GO para multiboot autónomo USB-C sin ordenador/móvil ni modificación física.**

La única evidencia física de esta unidad continúa siendo la consulta MIDI de identidad: m-bootloader `2.4-395E6CC`, capacidad 1 MiB, JEDEC `0x856014`, protocolo compatible y RAM anunciada. No se ejecutó el menú, no se cargó stage-2, no se leyó Flash y no se probó recuperación física.

## Evidencia específica de m-bootloader 2.4

Se contrastaron las fuentes originales públicas:

- [m-bootloader SPEC](https://github.com/jvitkauskas/m-bootloader/blob/main/docs/SPEC.md)
- [m-flasher formats.rs](https://github.com/jvitkauskas/m-flasher/blob/main/src/formats.rs)
- [m-card](https://github.com/jvitkauskas/m-card)

El SPEC público describe el flujo ROM → bootloader → aplicación XIP, el menú de recuperación, el modo ROM USB download, las transferencias de stage-2/RAM y los formatos de Flash. La versión exacta instalada del propietario no se ha comparado mediante ejecución o extracción: la coincidencia de identidad/versionado no convierte todas las observaciones del SPEC en evidencia física de esta unidad.

### Capacidades documentadas

- El bootloader valida tabla de aplicación y CRC antes de continuar.
- Una aplicación ausente, dañada, incompleta o que retorna conduce al menú de recuperación.
- `BOOT ANYWAY` es una decisión volátil de un solo arranque, no una instalación permanente.
- ROM USB download puede forzar una sesión de actualización mediante host/USB_KEY según el camino utilizado.
- Una aplicación puede devolver sólo si respeta el contrato de caller; si altera el entorno Flash montado debe reiniciar.
- `run-ram` no proporciona un retorno normal al ROM después de cambiar sus dependencias.
- El bootloader no se convierte automáticamente en un selector residente de terceros.

### Capacidades no demostradas para el FM-1 instalado

- que `2.4-395E6CC` cargue nuestro selector como stage-2 sin adaptar su formato y entry;
- que el bootloader enumere un lector USB-C MSC antes de arrancar la aplicación;
- que exista un slot residente independiente con espacio suficiente;
- que el menú invoque automáticamente una aplicación ubicada en la biblioteca USB;
- que un firmware sustituido pueda devolver el control si está corrupto o colgado.

## Auditoría de seguridad Flash

El mapa público distingue:

| Región | Tratamiento |
|---|---|
| `0x00000–0x03FFF` boot area | Siempre protegida |
| cabecera y tabla del boot area | Preservar |
| contenedor `uboot.boot` | Preservar |
| configuración/registro en variantes `0x3FA0`/`0x3FDC` | Preservar según la versión instalada |
| app area desde `0x4000` | Sólo mediante formato y tabla oficial |
| `VM`, `BTIF`, `USR`, calibración y recursos | No borrar globalmente; requieren política por región |
| `0xFF000–0xFFFFF` `key_mac`/página asociada | Siempre protegida |
| regiones no identificadas | Protegidas por defecto |

La revisión corrigió `scripts/virtual_flash_installer.py` para que `FlashRegionPolicy()` sea **fail-closed**:

- ninguna región está autorizada por defecto;
- el CLI sin `--authorize-range` rechaza la instalación;
- los perfiles con rangos autorizados son explícitos y sólo de simulación;
- el perfil de test documenta `[0x4000,0xFF000)` como modelo del app span público, no como autorización física;
- boot area, configuración y `key_mac` se comprueban en cada transacción;
- se mantienen cortes, corrupción, verificación, rollback virtual y `ALREADY_INSTALLED`.

La bytearray del simulador no representa un backup físico ni garantiza rollback en un chip real.

## Formatos y validadores read-only

El análisis público de `m-flasher` establece:

- `.fwsc`: paquete UFW con identidad entrelazada, cabecera cifrada, lista de archivos, CRC de lista y miembros como `flash.bin` y `ota.bin`;
- `flash.bin` tipo 0: imagen raw desde dirección cero, con boot area y app area almacenadas según las reglas de cifrado, y CRC de bytes almacenados;
- `ota.bin`: miembro diferente, con convenciones propias que no se deben generalizar desde `flash.bin`;
- app table: `app.bin`, `cfg_tool.bin` y descriptores como `VM`, `BTIF` y `USR`;
- configuración y regiones de datos: límites de cifrado y CRC distintos del app stream.

El inspector actual:

- identifica `.fwsc` como contenedor estructural, pero no lo instala;
- valida estructuralmente el tamaño/alineación de `flash.bin`, sin autenticar chip key ni tablas de unidad;
- conserva `ota.bin` como opaco;
- reconoce APP1 sólo como formato experimental, nunca como imagen Flash autorizada;
- deja ELF/BIN/FWS no reconocidos en inspección.

El instalador sólo simula un `flash.bin` raw explícitamente nombrado. No instala APP1, `.fwsc` ni `ota.bin` y no descifra ni interpreta automáticamente datos propietarios de `VM`, `BTIF` o `USR`.

## Recuperación del selector

### Programa temporal desde bootloader

El stage-2 documentado para el FM-1 usa microSD por SPI1 mediante una modificación tipo m-card y un archivo `/MBOOT/MENU.BIN`. Es temporal y se usa con el menú abierto; no constituye persistencia de nuestro selector.

No se puede sustituir esa ruta por USB-C: el SPEC no demuestra rol host, VBUS, CC, enumeración MSC ni FAT32 desde un lector externo antes de la aplicación.

### Menú de recuperación

El menú puede detectar una aplicación inválida y ofrecer recuperación/descarga/decisiones de arranque. Eso conserva una vía del bootloader, pero no prueba que pueda cargar automáticamente la biblioteca USB-C ni nuestro selector textual.

### Recuperación desde memoria externa

ROM USB/USB_KEY y M-Flasher permiten una recuperación con host o herramienta externa según el procedimiento. No cumplen el requisito de recuperación autónoma sin ordenador/móvil. La presencia de USB ROM download tampoco prueba USB Host MSC del FM-1.

### Retorno desde firmware instalado

Sólo sería posible si el firmware instalado coopera mediante un contrato de retorno explícito. No es fiable para firmware propietario, firmware corrupto o un firmware que no conoce el selector. `run-ram` tampoco es una ruta de retorno normal.

## Modelo y pruebas

`scripts/boot_recovery_model.py` continúa rechazando una recuperación exitosa en el perfil real:

- reinicio normal: arranca la aplicación instalada y el selector no está disponible;
- corrupción/corte: fallo o recovery del bootloader, pero sin selector garantizado;
- microSD ausente antes de la aplicación: no existe fallback USB-C automático;
- m-card/SPI: selector temporal condicional y no persistente;
- USB-C preboot: no demostrado;
- perfil hipotético con callback de recovery: sólo `CONDITIONAL_ONLY`.

Tests ejecutados:

```text
make boot-recovery-test
make virtual-installer-test
make firmware-tools-test
make test
python3 -m unittest discover -s tests -p '*test.py'
```

El conjunto Python pasó con 15 tests después de añadir la regresión fail-closed. Se conservan las pruebas C de MSC/BOT/FAT32, selector, PRESETS, política y pi32v2.

## CI

Los cuatro workflows pasaron sobre el commit de implementación `ac3ae6bd553c689ce2b06f15870d18a47429461e`:

- Embedded multiboot core — `45064373936` — success.
- Cover Flow simulator — `45064391282` — success, sólo regresión.
- FM-1 RAM diagnostics — `45064447149` — success.
- Android RAM Loader — `45064654125` — success.

Los workflows no implican Flash física, ejecución FM1 ni USB Host real.

## Decisión GO/NO-GO

### GO

- continuar con validadores read-only;
- conservar la lista textual, PRESETS, PLAY, FM1CAT, MSC/BOT/FAT32;
- mantener perfiles virtuales explícitos para estudiar formatos y transacciones;
- documentar recovery oficial como ruta externa condicionada.

### NO-GO

- instalar físicamente firmware;
- escribir o borrar Flash;
- tratar APP1 como firmware Flash;
- asumir que `.fwsc`, `flash.bin` u `ota.bin` autorizan ejecución;
- asumir USB-C host preboot;
- declarar selector residente o rollback autónomo.

## Condiciones para FASE 5X

1. Obtener documentación o evidencia aprobada de la versión exacta `2.4-395E6CC` y sus tablas de unidad.
2. Confirmar mediante procedimiento sólo lectura el boot area, app table, chip key, key/MAC, configuración y regiones `VM/BTIF/USR`.
3. Confirmar el caller exacto de stage-2 y el formato/entry de `MENU.BIN`.
4. Separar una futura prueba m-card/SPI de cualquier prueba USB-C; no instalar m-card ahora.
5. Diseñar una prueba física sólo de identificación/lectura con recuperación independiente preparada.
6. Obtener autorización explícita antes de cualquier comando de programación.
7. Mantener `MB_LAUNCH_BLOCKED`, el instalador fail-closed y todas las rutas físicas deshabilitadas.

## Seguridad y estado

No se modificó `main`, DARK-1 ni m-bootloader. No se escribió ni borró Flash, no se ejecutó RAM/APP1, no se activó USB Host ni se realizó trabajo VBUS/CC.

- Rama: `goose/fase3b-4a-preflight`.
- Commit de implementación validado: `ac3ae6bd553c689ce2b06f15870d18a47429461e`.
- PR #8: OPEN/Draft, sin merge.
- Decisión de recuperación autónoma USB-C: **NO-GO**.
