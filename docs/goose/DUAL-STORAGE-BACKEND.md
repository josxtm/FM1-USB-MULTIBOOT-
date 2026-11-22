# RAM-only — backends DEMO y USB MSC

## Interfaz común

`mb_ram_app_init()` selecciona un backend sin cambiar la máquina de estados ni el renderer:

```text
MB_APP_DEMO    → catálogo interno → mb_core
MB_APP_USB_MSC → mb_storage externo → mb_core
```

El backend USB recibe un `struct mb_storage` ya adaptado por la cadena:

```text
USB0/husb → storage.c → msc_bot.c → block_device → fat32.c
         → embedded_fat32_storage.c → mb_storage
```

El transporte físico no se invoca desde las pruebas host.

## DEMO

Incluye tres entradas demostrativas y sus datos SHA locales. Se usa para Cover Flow, navegación y pruebas de inspección sin ningún dispositivo.

## USB MSC

El test de aplicación construye un backend externo emulado con `FM1/INDEX.CSV` y `FM1/FW/USB_1.BIN`. Usa exactamente la misma función `mb_ram_app_init()`, `mb_load_catalog()`, `mb_verify_selected()` y renderer.

La ruta conserva EOF, missing, error y ausencia de storage. Un backend nulo resulta en `MB_NO_STORAGE`; no se sustituye silenciosamente por el catálogo DEMO dentro del modo USB.

El fallback DEMO existe como selección explícita de backend, no como encubrimiento de una desconexión durante una prueba de transporte.

## Limitaciones

- USB Host real sigue desactivado.
- VBUS/CC no se verifican.
- El soporte de hubs no está implementado.
- Un lector compuesto o un hub requiere enumeración adicional.
- MSC sólo admite comandos SCSI de lectura.
- No hay escritura de tarjeta, Flash ni firmware.
