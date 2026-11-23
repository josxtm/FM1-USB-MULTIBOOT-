# Fase 6D · Paquete reproducible SPI1/FAT32 para FM‑1

La herramienta `scripts/fm1_sd_package.py` prepara un ZIP de archivos para el FM‑1 en un ordenador. El selector se toma del build pi32v2 5Z, con tráiler STG2, 11.300 B, CRC16 `0x1FDD` y SHA‑256 `0e9ef963d5a0c614d09a7bd3dc3ba808b25c611d572e9544f37bd9586c283002`. El bootloader objetivo es `2.4-395E6CC`. El ZIP es reproducible y contiene:

```text
MBOOT/MENU.BIN
FM1/INDEX.CSV
FM1/FW/
PACKAGE.JSON
```

`PACKAGE.JSON` se conserva en el ordenador. El catálogo de la tarjeta sigue siendo FM1CAT v1, con nombres DOS 8.3, ASCII, máximo 64 entradas y `COMPAT=UNVERIFIED`. Se valida la longitud, SHA-256, nombre e ID, perfil de placa y contenido. El selector verifica SHA-256 al consultar una entrada y mantiene el bloqueo de lanzamiento. [Formato FM1CAT](FM1CAT-v1.md)

## Construcción y verificación de archivos locales

```sh
make TOOLCHAIN=toolchain stg2-selector
python3 scripts/fm1_sd_package.py build \
  --selector build/stg2/fm1-multiboot-selector-stg2.bin --output FM1-SPI1-EMPTY.zip
python3 scripts/fm1_sd_package.py verify FM1-SPI1-EMPTY.zip
```

La herramienta necesita Python 3 con su biblioteca estándar. La compilación del selector puede sustituirse por el BIN 5Z publicado que coincida con el hash. La salida del comando incluye el SHA-256 del ZIP. La comprobación relee bytes del ZIP y compara cada archivo con la fuente fijada o con metadatos locales revisados. Rechaza miembros extra, rutas inesperadas, enlaces, CRC, formatos y hashes incorrectos. El modo de creación usa un nuevo archivo regular y no acepta sobrescribir uno existente.

| Modo | Uso y límites |
|---|---|
| `EMPTY` | Selector y catálogo vacío, sin firmware musical. Permite observar el menú y salir cuando se autorice el ensayo SPI1. |
| `SYNTHETIC` con `--demo` | Dos fixtures de SLOT de 5Y marcados como sintéticos. Artefacto para pruebas en ordenador. Este ZIP se distingue por su nombre `HOST-ONLY`. |
| `PRIVATE_REVIEWED` con `--review` y `--source` | Copia de un SLOT v1 de procedencia revisada localmente, con hash independiente, placa FM‑1, JEDEC `856014`, cabecera y datos con CRC32 y mapa del contenedor sin sectores del bootloader ni key/MAC. El propietario mantiene los archivos en privado. El informe distingue la revisión de integridad de la compatibilidad de arranque, que permanece `UNVERIFIED`. |

Para el modo privado, `--source` apunta a un directorio de trabajo con `<ID>.BIN`. `--review` apunta a un JSON independiente con `schema: 1`, `target: {"board":"FM-1","jedec":"856014","bootloader":"2.4-395E6CC"}` y `entries`. Cada entrada debe tener únicamente `id`, `label`, `version`, `size` entero, `sha256` esperado, `format: "SLOT-v1"` y `source` con la referencia documental de procedencia. Durante `verify` se exige el mismo archivo de revisión. El verificador no genera esa evidencia a partir de bytes desconocidos y no valida la autenticidad de una declaración del operador. En esta fase no se ha revisado ni publicado ningún firmware musical real. APP1, STG2 usado como firmware musical, volcados raw, FWSC y formatos opacos no tienen perfil admitido en la preparación 6D. No renombrar un paquete para simular un SLOT. [Restricciones del simulador 5Y](goose/FASE-5Y-RESULTADOS.md)

## Preparar una tarjeta en un ordenador para una sesión posterior

1. Trabajar con una microSD dedicada, conservar una copia de sus datos y verificar su estado con las herramientas del sistema. Confirmar que la primera partición utiliza FAT32 con sectores lógicos de 512 B. `vfat` o el nombre de volumen por sí solos no determinan el subtipo. Comprobar el medio en modo de verificación sin cambios antes de copiar. Un volumen dañado o con otro sistema de archivos no es apto.
2. Verificar el ZIP y extraerlo primero en un directorio local. Para el primer ensayo reservado de selector, usar `EMPTY`. Copiar las carpetas `MBOOT` y `FM1` al nivel raíz de la tarjeta. Si ya existen, comprobar su contenido y conservar una copia antes de efectuar cambios. No se usa una imagen de disco para copiar el ZIP.
3. Releer y comparar el SHA-256 de la copia de `MENU.BIN` y de `INDEX.CSV` con los valores que imprimió el verificador. Si hay entradas privadas aprobadas para inspección, comprobar también los archivos de `FW`. Expulsar la tarjeta desde el sistema y reservarla para la sesión SPI1 autorizada. El selector permanece bloqueado para instalaciones.
4. La m‑card y la revisión del FM‑1 requieren las comprobaciones eléctricas y mecánicas de la Fase 6B. Este procedimiento de preparación de archivos no utiliza el FM‑1 ni valida el lector, el panel o los controles físicos. [Materiales y comprobaciones](FASE-6B-MICROSD-HARDWARE.md)

## Ensayo reproducible de software

```sh
make TOOLCHAIN=toolchain MBOOT24=/ruta/al/checkout-fijado sd-package-test
```

Se verifican dos creaciones idénticas del ZIP. Los archivos verificados del paquete pasan por un generador de imágenes FAT32 con fragmentación deliberada, tanto superfloppy como primera partición MBR. Son archivos dispersos temporales de host. El harness exacto de `395e6cc` lee sus sectores y compara los 11.300 B de selector en RAM simulada. Confirma cadena no contigua, ausencia de MENU.BIN y rechazo de su CRC corrupto. El harness del selector usa la misma imagen de sectores a través de la simulación SPI1, monta el catálogo, navega, verifica SHA-256 y observa errores de CRC, timeout, desconexión y ausencia de firmware. La biblioteca vacía permite recarga o salida. La ejecución de instrucciones pi32v2 en el FM‑1 sigue pendiente. Las imágenes de disco de prueba no se incluyen en el paquete de CI.
