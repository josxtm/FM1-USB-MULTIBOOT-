# FASE 5Q — resultados end-to-end USB MSC

La prueba existente `msc-fat32-test` atraviesa BOT read-only emulado, sectores FAT32, cadenas fragmentadas, FM1CAT, SHA, renderer y `MB_LAUNCH_BLOCKED` para catálogos 0/1/2/3/64 y fallos de desconexión, timeout, BPB, volumen, ciclo, truncamiento y hash.

La nueva prueba `usb-storage-test` usa el adaptador `mb_usb_msc_storage_mount()`, el mismo `fat32.c` y el mismo `mb_storage` que la ruta física, y valida carga de catálogo/verification/launch policy sin inicializar USB0.

Esto prueba composición e interfaces. No prueba VBUS/CC, enumeración eléctrica, hub, lector USB-C ni husb sobre el FM-1. Los comandos SCSI admitidos continúan siendo exclusivamente lectura; WRITE/format/erase permanecen rechazados.
