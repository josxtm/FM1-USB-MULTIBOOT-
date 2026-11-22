# FASE 5J — aplicación Cover Flow RAM-only

## Resultado

Se añadió una composición de aplicación RAM-only freestanding en:

- `src/embedded_ram_app.c`
- `src/embedded_ram_app.h`
- `src/multiboot_ram_start.S`
- `src/multiboot_ram.ld`
- `tests/embedded_ram_app_test.c`

La aplicación integra el `mb_core`, un catálogo interno de tres entradas, el backend `embedded_lcd_renderer`, el adaptador lógico de teclas y los drivers existentes `lcd.c`/`keys.c`. El entry point experimental queda separado del diagnóstico USB existente y no se ejecuta en el FM-1.

## Catálogo de demostración

Las entradas son únicamente metadatos y datos de demostración locales:

- DARK-1
- MELODEE
- TESSERA

No se incluyen binarios de terceros ni se afirma compatibilidad o relocabilidad. Los datos son suficientes para probar carga, SHA, inspección y navegación; `mb_prepare_launch()` sigue devolviendo `MB_LAUNCH_BLOCKED`.

## Startup y enlace

Se prepararon:

- `mb_app_start` en una sección `.text.start` separada.
- Stack primario y secundario documentados en el linker experimental.
- Limpieza de `.bss` desde la aplicación mediante símbolos del linker.
- Región APP hipotética en `0x01C30000`.
- Región LOW separada para `.bss`.
- Aserciones contra solapamiento de stack y memoria retenida.
- Imagen APP1 experimental mediante `scripts/app_trailer.py`.

El contrato de llamada, reloj, estado heredado, vectorización, watchdog, SRAM y retorno del m-bootloader 2.4 no se consideran verificados por este enlace.

## Pruebas

`make ram-app-test` valida:

- startup lógico simulado;
- tres entradas internas;
- navegación NEXT;
- PLAY como inspección;
- REC como cancelación;
- verificación SHA;
- render callback;
- debounce de teclas;
- lanzamiento bloqueado.

Las pruebas host proporcionan HAL inertes y no acceden a registros físicos.

## Medición CI

El toolchain pi32v2 23.1.5-2 enlazó la aplicación experimental y generó:

```text
.image / flat BIN = 5080 bytes
APP1                 = 5088 bytes
.bss                 = 25136 bytes
static link total    = 30216 bytes
entry                = 0x01C30000
```

El mapa, ELF, BIN, trailer APP1 y reporte de secciones se publicaron como artefactos del workflow. Estas cifras son del linker; no son una medición de SRAM ni una autorización de ejecución.

## Estado de cierre

5J queda **completada en software host y pi32v2**. La ejecución en hardware no está autorizada.
