# FM-1 — ABI y arranque

## Fuentes

La fuente principal es [m-bootloader SPEC](https://github.com/jvitkauskas/m-bootloader/blob/main/docs/SPEC.md), MIT, y los mapas enlazados publicados por Melodee. Describe pi32v2 little-endian, calling convention, contenedores, tablas de aplicación, RAM retenida y entradas de stage. [M-Flasher formats](https://github.com/jvitkauskas/m-flasher/blob/main/src/formats.rs) describe el área de boot y paquetes desde el lado host.

## Clasificación

El formato/ABI descrito por m-bootloader es **documentado por terceros**, no un contrato aprobado para este core. Entradas `0x01C02000`, `0x01C0A800`, `0x01C20000` y `0x01C28000` son válidas solo en sus contextos publicados. El `0x01C30000` de nuestro linker es **hipótesis**. Vectores, watchdog, retorno de aplicación, reservas exactas de SRAM y XIP para una nueva multiboot app son **desconocidos**.

El bootloader publicado excluye expresamente un nuevo USB stack en su boot menu; su SD stage 2 usa SPI1 y `/MBOOT/MENU.BIN` en una modificación de hardware. Esto no proporciona un ABI para ejecutar un selector desde USB MSC.

## Decisión

No adaptar binarios GPL ni instalar un ELF. El core permanece freestanding y `mb_prepare_launch()` bloqueado hasta obtener un ABI/imagen validado, preservación del bootloader y recuperación independiente.
