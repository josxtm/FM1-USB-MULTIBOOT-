# FASE 5X.1 — integración del respaldo real y auditoría avanzada de Flash

## Alcance y privacidad

El respaldo real del FM-1 no está incluido en el repositorio, no se solicitó incorporarlo y no se añadió a GitHub Actions. Esta fase usa únicamente los resultados minimizados proporcionados por la directiva y fixtures sintéticos construidos desde la especificación pública.

No se publican ni se imprimen:

- respaldo raw;
- JSON original;
- chip key, efuse o UID;
- MAC Bluetooth;
- calibración;
- bytes privados de `isd_config.ini`;
- firmware propietario.

El auditor marca explícitamente `sensitive_values_emitted: false` y sólo exporta estructura, nombres de entradas, offsets, tamaños, CRC y categorías de protección.

## Evidencia real minimizada

La siguiente información proviene del análisis offline externo del respaldo real, no de una lectura ejecutada por este repositorio:

| Parámetro | Resultado comunicado |
|---|---:|
| Capacidad | `1,048,576` bytes |
| SHA-256 frente a metadatos originales | coincidente |
| Cabecera Flash | CRC16 válido |
| Versión filesystem | `0x10` |
| Tamaño filesystem | `0xFF000` |
| Alineación | `4096` bytes |
| Directorio superior | 4 registros válidos |
| `isd_config.ini` | 96 bytes |
| Tabla de aplicación | 7 registros válidos |
| `app.bin` | 581.564 bytes, CRC válido |
| `cfg_tool.bin` | 383 bytes, CRC válido |

Directorio superior observado:

```text
uboot.boot
isd_config.ini
app_dir_head
key_mac
```

La clave de unidad sólo se usó transitoriamente durante el análisis offline externo para descifrar estructuras y verificar CRC. No se incorporó al proyecto.

## Auditor ampliado

`scripts/audit_flash_backup.py` se amplió para:

1. exigir exactamente 1 MiB para un análisis completo;
2. calcular SHA-256 y CRC32;
3. descifrar la cabecera con ENC `0xFFFF`;
4. validar CRC16/XMODEM;
5. recorrer el directorio superior hasta un límite de 128 registros;
6. validar límites y CRC de entradas;
7. comprobar `isd_config.ini`, incluyendo longitud de 96 bytes y CRC del prefijo de clave sin mostrarlo;
8. derivar internamente el chip key sólo para el análisis efímero, sin incluirlo en salida;
9. descifrar registros de app con ENC por bloques de 32 bytes y clave dependiente del offset;
10. tratar `app_area_head` con su dirección CPU y no como offset físico;
11. tratar `app.bin` y `cfg_tool.bin` como offsets relativos al app area;
12. tratar `PRCT`, `VM`, `BTIF`, `USR` y otros descriptores como offsets físicos absolutos;
13. validar CRC16 de archivos con datos cifrados/descifrados según el atributo;
14. generar mapa, categoría y nivel de evidencia;
15. detectar registros fuera de rango, truncados, CRC inválido y solapamientos;
16. dejar regiones ambiguas como `UNKNOWN` y aplicar política protegida.

No se fija un número de registros de aplicación: se usa un límite de seguridad de 128 y se detiene ante el primer registro inválido o tabla truncada.

## Mapa observado y protección

El mapa minimizado de la unidad analizada es:

| Región | Inicio | Fin exclusivo | Estado |
|---|---:|---:|---|
| Bootloader/configuración | `0x00000` | `0x04000` | protegida |
| Aplicación/PRCT | `0x04000` | `0x93000` | observada; no universal |
| VM | `0x93000` | `0xE9000` | protegida por defecto |
| BTIF | `0xE9000` | `0xEA000` | protegida por defecto |
| USR | `0xEA000` | `0xFC000` | protegida por defecto |
| Sin descriptor confirmado | `0xFC000` | `0xFF000` | desconocida/protegida |
| key_mac | `0xFF000` | `0x100000` | reserva fija/protegida |

Dentro de la aplicación observada:

- `app.bin`: `0x04120–0x920DC`;
- `cfg_tool.bin`: `0x920DC–0x9225B`.

Estos rangos son datos observados de una unidad y no particiones universales. `PRCT`, `VM`, `BTIF` y `USR` se derivan de tablas válidas; no se asigna libertad de escritura por identificar una región como aplicación.

El último sector puede aparecer relleno de `0xFF`; eso no se interpreta como espacio libre ni como pérdida de MAC/clave.

## Fixtures sintéticos

Se añadió un fixture procedural que genera en memoria:

- Flash de 1 MiB;
- cabecera cifrada y CRC válida;
- directorio superior de cuatro entradas;
- configuración sintética de 96 bytes con CRC;
- tabla de aplicación de siete entradas;
- `app_area_head`;
- `app.bin` y `cfg_tool.bin` cifrados por bloques;
- descriptores `PRCT`, `VM`, `BTIF` y `USR`;
- key/MAC final preservado sin bytes reales.

Los tests también cubren:

- SHA esperado y discrepante;
- cabecera y tabla corruptas;
- configuración inválida;
- descifrado sintético;
- CRC de archivos;
- registros fuera de límites;
- tablas truncadas;
- solapamientos;
- regiones desconocidas;
- sector `key_mac` vacío/no interpretado;
- backup incompleto;
- ausencia de exposición de claves, MAC o configuración.

La fixture no contiene bytes del respaldo real.

## Relación con el instalador

`FlashRegionPolicy()` continúa fail-closed. El auditor sólo entrega evidencia estructural; no convierte ninguna región observada en región programable.

La política distingue:

- `OBSERVED_STRUCTURE`;
- `PROTECTED`;
- `UNKNOWN`;
- región potencialmente sustituible según tabla;
- región autorizada únicamente en un perfil virtual explícito.

La identificación de `app.bin` no autoriza instalarlo. VM, BTIF, USR y regiones sin descriptor permanecen protegidas. No existe writer físico ni integración de comandos de programación.

## Validación

Comandos ejecutados:

```text
make flash-backup-audit-test
make boot-recovery-test
make virtual-installer-test
make firmware-tools-test
make test
python3 -m unittest discover -s tests -p '*test.py'
```

Resultado: **18 tests Python OK** después de añadir el fixture estructural, además de la suite C existente.

Workflows de GitHub Actions sobre el commit de implementación `870b680c81ac30385f7a38938059c5cdc67a12d2`:

- Embedded multiboot core — `45066724532` — success.
- Cover Flow simulator — `45066728549` — success, sólo regresión.
- FM-1 RAM diagnostics — `45066732256` — success.
- Android RAM Loader — `45066735861` — success.

Ningún workflow contiene el respaldo real ni ejecuta Flash, RAM o USB Host en el dispositivo.

## Errores corregidos

- La auditoría inicial no interpretaba tabla de aplicación ni configuración de unidad: ahora usa descifrado efímero y salida sanitizada.
- La tabla de aplicación no se trata como una lista fija: se limita por formato y bounds.
- `app_area_head` ya no se confunde con un offset físico.
- Los descriptores absolutos no reciben erróneamente la base del app area.
- Los registros fuera de rango y solapamientos se reportan.
- El instalador no obtiene autorización por reconocer `app.bin`.
- La política Flash continúa sin autorización por defecto.

## Limitaciones restantes

- El respaldo real no se incorpora ni se vuelve a abrir en CI.
- No se demuestra que la tabla observada sea válida para otro firmware o unidad.
- No se prueba restauración, instalación o recuperación.
- No se ha verificado el acceso USB-C Host.
- No se ha demostrado microSD preboot por USB-C.
- No se ha confirmado el caller stage-2/run-ram en el FM-1 instalado.
- No se ha demostrado un selector residente o retorno autónomo.
- `MB_LAUNCH_BLOCKED` permanece activo.

## Requisitos para FASE 5Y

1. Mantener el respaldo real fuera del repositorio y repetir el auditor sólo en un entorno privado autorizado.
2. Comparar todos los offsets reportados con una segunda fuente o procedimiento read-only aprobado.
3. Confirmar app table, chip key y configuración sin exportar secretos.
4. Preparar un informe de protección por región para el instalador virtual, sin writer.
5. Obtener contrato verificable de recovery/stage-2 antes de considerar una imagen temporal.
6. Resolver el procedimiento eléctrico de USB-C antes de cualquier enumeración.
7. Mantener selector textual, PRESETS, PLAY, FM1CAT, MSC/BOT/FAT32 y pi32v2 sin reintroducir Cover Flow.
8. No enviar comandos de borrado, escritura o carga RAM.

## Estado de seguridad y entrega

- Rama: `goose/fase3b-4a-preflight`.
- PR #8: OPEN/Draft, sin merge.
- `main`: intacto.
- DARK-1 y m-bootloader: intactos.
- Respaldo real: no incluido en GitHub, logs, artefactos ni tests.
- Flash física: sin escritura, borrado o lectura ejecutada por esta fase.
- Decisión de instalación permanente y retorno autónomo: **NO-GO** hasta resolver recovery, USB-C y ABI.
