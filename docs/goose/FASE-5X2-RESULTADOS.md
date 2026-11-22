# FASE 5X.2 — corrección definitiva del auditor Flash

## Alcance

Esta fase corrige defectos concretos detectados al contrastar el auditor con el resumen técnico de un respaldo real de FM-1. No se incorporó el respaldo, su JSON, identificadores ni ningún dato privado al repositorio. Las regresiones usan únicamente fixtures sintéticos.

No se modificaron `main`, DARK-1 ni m-bootloader. No se leyó ni escribió Flash física y `MB_LAUNCH_BLOCKED` permanece activo.

## Corrección de `uboot.boot`

El CRC de `uboot.boot` no se calcula sobre los bytes cifrados almacenados como si fueran payload plano.

El auditor ahora distingue:

- CRC de registros de directorio: sobre el registro descifrado, excluyendo su propio campo CRC;
- `uboot.boot`: CRC16/XMODEM sobre el contenedor plano completo;
- cabecera del contenedor: ENC `0xFFFF` reiniciado para sus primeros 16 bytes;
- payload del contenedor: ENC `0xFFFF` reiniciado para el payload según el límite publicado;
- `isd_config.ini`: CRC sobre sus bytes almacenados, sin aplicar ENC genérico a la configuración;
- archivos de aplicación: CRC sobre los datos descifrados por bloques de 32 bytes y clave/offset de unidad.

La salida pública incluye el tipo de comprobación, por ejemplo:

```text
plain_container_crc16
stored_bytes_crc16
```

No se oculta un CRC inválido: un contenedor sintético mal cifrado o corrupto continúa fallando.

La regresión sintética construye un `uboot.boot` de 16 bytes, lo cifra por separado y verifica que el auditor lo marque válido mediante el CRC del contenedor plano.

## Corrección de PRCT y regiones anidadas

`PRCT` describe una región de protección que puede contener archivos de aplicación. Por eso el auditor separa:

- `map_nesting.expected_nesting`: descriptor reconocido que contiene un archivo;
- `map_overlaps`: solapamientos verdaderamente inválidos o ambiguos.

Un `PRCT` que contiene `app.bin` ya no se reporta como corrupción. En cambio, se mantienen como error:

- solapamientos parciales entre áreas incompatibles;
- solapamientos entre archivos de igual nivel;
- regiones descriptoras que cruzan límites sin contener limpiamente el objeto;
- rangos fuera de Flash.

VM, BTIF y USR continúan siendo regiones protegidas por defecto. Identificar una región no la convierte en programable.

## Límites de tablas

La auditoría ahora:

- mantiene un límite defensivo de 128 registros de aplicación;
- se detiene ante el primer registro CRC inválido;
- rechaza atributos de aplicación desconocidos (`0x12`, `0x92`, `0x82` y `0x83` son los únicos aceptados en el perfil documentado);
- requiere que el primer registro válido sea interpretable como `app_area_head`;
- trata `app_area_head.address` como entry CPU y no como offset físico;
- trata archivos de atributo `0x82` como offsets relativos al app area;
- trata descriptores `0x12`/`0x92` como offsets físicos absolutos;
- comprueba por separado CRC de registros y CRC de datos;
- detecta tabla truncada, registro fuera de límites, atributo desconocido y overflow de rango;
- deja regiones desconocidas en estado `UNKNOWN` y protegidas.

Esto evita recorrer indefinidamente bytes de instrucciones o datos de aplicación como si fueran entradas de directorio.

## Comparación con el resumen del respaldo real

El auditor y los fixtures cubren la estructura comunicada del respaldo real:

| Estructura | Resultado esperado |
|---|---|
| capacidad | `0x100000` |
| filesystem size | `0xFF000` |
| alineación | 4096 bytes |
| boot table | CRC de entradas válido |
| `uboot.boot` | CRC del contenedor plano, no bytes cifrados |
| `isd_config.ini` | longitud 96 y CRC independiente |
| app table | siete entradas estructurales en el resumen real |
| `app.bin` | CRC sobre datos descifrados |
| `cfg_tool.bin` | CRC sobre datos descifrados |
| `PRCT` | descriptor de protección potencialmente contenedor |
| VM/BTIF/USR | regiones protegidas, no automáticamente sustituibles |
| `key_mac` | último sector protegido incluso si contiene `0xFF` |

No se afirma que el fixture sea una copia del respaldo real ni que sus offsets autoricen una instalación.

## Privacidad y `.gitignore`

Se añadieron patrones específicos para respaldos privados y JSON de metadatos:

```text
**/fm1-backup*.bin
**/fm1-backup*.json
**/*fm1*backup*.bin
**/*fm1*backup*.json
**/flash-backup*.bin
**/flash-backup*.json
```

No se excluyen los BIN experimentales legítimos existentes del proyecto. No se usa un patrón global `*.bin`, porque los artefactos APP1/RAM y fixtures públicos deben seguir siendo versionables.

El respaldo real nunca se copió al árbol, commit, log o artifact.

## Pruebas

Se añadieron regresiones para:

- CRC correcto de contenedor `uboot.boot`;
- CRC incorrecto de contenedor;
- anidamiento PRCT/app.bin esperado;
- detección de solapamiento inválido;
- atributos desconocidos;
- límites fuera de Flash;
- tabla truncada;
- tabla de siete registros sintética;
- `isd_config.ini` de 96 bytes;
- CRC de `app.bin` y `cfg_tool.bin`;
- ausencia de datos privados en informes.

Comandos ejecutados:

```text
make flash-backup-audit-test
make boot-recovery-test
make virtual-installer-test
make firmware-tools-test
make test
python3 -m unittest discover -s tests -p '*test.py'
```

Resultado local: **19 tests Python OK**, además de las pruebas C existentes.

## Protección del instalador

`FlashRegionPolicy()` continúa fail-closed. El auditor produce evidencia estructural, pero no habilita ninguna región. La política distingue regiones observadas, protegidas, desconocidas, anidadas y autorizadas sólo dentro de perfiles sintéticos explícitos.

No existe writer físico ni ruta de comandos de programación. `MB_LAUNCH_BLOCKED` no cambia.

## CI y estado

Los workflows pasaron sobre el commit de implementación `c5863fdd0a18a0feb4e5bfe3df9d14a674bf98c8`:

- Embedded multiboot core — `45073133469` — success.
- Cover Flow simulator — `45073136680` — success, sólo regresión histórica.
- FM-1 RAM diagnostics — `45073140103` — success.
- Android RAM Loader — `45073143230` — success.

Los workflows no contienen el respaldo real y no ejecutan Flash, USB Host, RAM ni firmware en el dispositivo.

## Condiciones pendientes para FASE 5Y

1. Mantener los respaldos reales fuera de GitHub y auditar únicamente copias privadas autorizadas.
2. Confirmar el parser contra una segunda revisión documental o herramienta offline independiente.
3. Confirmar el significado exacto de cualquier región no reconocida antes de diseñar una política virtual más amplia.
4. Mantener bootloader, configuración, VM, BTIF, USR, key/MAC y gaps protegidos.
5. Obtener recuperación independiente antes de cualquier instalación.
6. Resolver USB-C Host y su seguridad eléctrica antes de enumerar un lector.
7. Mantener separado APP1/RAM de `flash.bin`/Flash/XIP.
8. No modificar DARK-1, m-bootloader ni `main`.

## Estado de entrega

- Rama: `goose/fase3b-4a-preflight`.
- Commit de implementación validado: `c5863fdd0a18a0feb4e5bfe3df9d14a674bf98c8`.
- PR #8: OPEN/Draft, sin merge.
- Flash física: sin acceso de lectura/escritura en esta fase.
- Decisión de instalación permanente: **NO-GO** hasta cerrar recovery, USB-C y ABI.
