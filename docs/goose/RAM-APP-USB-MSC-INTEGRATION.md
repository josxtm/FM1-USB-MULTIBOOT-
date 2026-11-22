# FASE 5Q — aplicación RAM y USB MSC

La cadena integrada conserva una sola implementación por capa:

`USB0/husb.c → storage.c → msc_bot.c → block_device → fat32.c → embedded_fat32_storage.c → mb_core → embedded_lcd_renderer.c`.

`embedded_usb_msc_storage.c` es únicamente el adaptador de composición. Su función `open()` es explícita y no se invoca automáticamente por enlazar la aplicación. `mount()` permite al host/simulador inyectar un block device read-only usando exactamente el mismo FAT32/mb_storage.

El backend DEMO sigue siendo el catálogo incorporado de tres nombres, sin binarios de terceros. El backend USB recibe `mb_storage`; un error no cae silenciosamente a DEMO. El modo USB físico sigue siendo una opción futura porque `usb_enumerate()` sólo puede probarse sobre hardware autorizado.

El target `ram-app-pi32v2-full-image` enlaza la aplicación, Cover Flow, teclas, LCD, timer, core, adapter USB, FAT32, storage, BOT, husb, diagnóstico USB y runtime en un único ELF experimental.
