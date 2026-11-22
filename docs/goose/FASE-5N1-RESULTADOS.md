# FASE 5N.1 — corrección de arranque RAM y validación completa

## Causa del bloqueo

La versión anterior ejecutaba:

```c
if (!timer_start_unconfigured() || !timer_calibrated()) return;
```

`timer_start_unconfigured()` limpiaba deliberadamente `ticks_ms` a cero. Como no existía una fuente de calibración posterior, la aplicación abandonaba antes de inicializar LCD, renderer o catálogo.

## Corrección

Se separaron explícitamente cuatro estados:

1. Inicialización TMR0.
2. Obtención/configuración de ticks por milisegundo.
3. Habilitación de navegación.
4. Diagnóstico de reloj no calibrado o inválido.

La aplicación ya no usa `tb_now()/48000`. Usa `embedded_clock` con una tasa explícita proporcionada por la HAL. Si la tasa no existe, la entrada física segura se detiene; no se inventa una frecuencia del FM-1.

Los tests host proporcionan una tasa controlada de 48000 ticks/ms únicamente como simulación. Esto no valida la frecuencia física.

## Prueba de entrada completa

`tests/embedded_ram_app_test.c` ejecuta realmente `embedded_ram_app_entry()` con hooks host inertes:

```text
startup
 → timer init/calibrated
 → LCD HAL simulada
 → backend DEMO
 → catálogo
 → renderer
 → NEXT
 → PLAY/inspección
 → REC/cancelación
 → parada controlada
```

Después repite el mismo entry con backend `MB_APP_USB_MSC` y un `mb_storage` externo emulado.

La prueba falla si:

- la aplicación sale prematuramente;
- no inicializa la HAL LCD;
- no genera frames;
- no procesa eventos;
- no vuelve a BROWSE tras REC;
- el backend USB no carga el catálogo;
- se habilita un lanzamiento.

Resultado:

```text
RAM-only Cover Flow app: full startup DEMO/USB, buttons, inspection, cancel, verify, blocked launch: OK
```

## Selector DEMO/MSC

`mb_ram_app_init()` es ahora el único punto de selección de backend. DEMO sigue siendo el modo inicial. MSC recibe un `struct mb_storage` externo y no activa USB0. La HAL real continuará siendo `husb → storage → MSC/BOT → FAT32` cuando exista una prueba física autorizada.

## Stack y memoria

`struct mb_core`, renderer, keys y clock de la aplicación se mantienen como almacenamiento estático controlado. Esto evita colocar el core de aproximadamente 9 KB como objeto automático dentro de `embedded_ram_app_entry`.

El enlace pi32v2 final tras mover el estado persistente de la aplicación a `.data.app` midió:

```text
.image / flat BIN     15368 bytes
APP1                   15376 bytes
.bss                  25140 bytes
static total          40508 bytes
```

La decisión evita colocar el `mb_core` en BSS/stack y deja el BSS por debajo del límite LOW; aumenta la imagen inicializada porque el estado debe quedar disponible en RAM al entrar. El mapa/linker continúan imponiendo los límites APP/LOW y el validador comprueba BSS, segmentos y memoria retenida.

La profundidad máxima de stack sigue sin estar demostrada: el mapa no sustituye un análisis ABI/dinámico ni una prueba en el FM-1.

## Pruebas adicionales

Pasaron:

- `make ram-app-test`;
- `make clock-test`;
- `make launch-policy-test`;
- `make ram-image-validator-test`;
- pruebas renderer y keys;
- integración FAT32/MSC emulada;
- sanitizers existentes;
- compilación estricta C99.

## ABI y hardware

Continúan pendientes entry real, argumento, reloj LSB, TMR0 heredado, watchdog, interrupciones, SRAM, stack, LCD, GPIO, USB Host, VBUS/CC y recuperación.

No se ejecutó el entry en el FM-1. La prueba completa es host-only y usa HAL simuladas.

## Estado final

5N.1 queda completada en software: la ruta de entrada ya no falla prematuramente cuando la HAL proporciona una calibración válida, y permanece bloqueada de forma segura cuando falta evidencia del reloj físico.

La ejecución RAM real continúa sin autorización y `MB_LAUNCH_BLOCKED` permanece.
