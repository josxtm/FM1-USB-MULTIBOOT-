# FASE 5L — integración USB MSC y catálogo externo

## Arquitectura conservada

La integración usa las capas existentes, sin duplicar controladores:

```text
USB0/husb → storage.c → msc_bot.c → block_device → fat32.c
         → embedded_fat32_storage.c → FM1CAT → mb_core → renderer
```

`msc_bot.c` mantiene la política read-only y la construcción/validación CBW/CSW. `storage.c` conserva el transporte USB, enumeración, endpoints, recuperación BOT y traducción de errores. `fat32.c` permanece independiente de USB.

## Evidencia de software

La prueba `tests/msc_fat32_integration.c` ya emula el transporte BOT sobre una imagen FAT32 real y cubre catálogos:

```text
0, 1, 2, 3 y 64 entradas
```

También cubre:

- FAT32 MBR y superfloppy.
- Directorios `FM1/FW`.
- Cadenas fragmentadas.
- INDEX.CSV.
- Lectura de firmware.
- SHA y tamaño.
- EOF correcto.
- Desconexión.
- Timeout.
- BPB/volumen dañado.
- Ciclo FAT.
- Truncamiento.
- Hash incorrecto.
- Lanzamiento bloqueado.

La ruta usa lectura simulada; no se sustituye por un archivo incrustado cuando se prueba la cadena BOT→FAT32.

## Dispositivo directo y base multipuerto

El sistema queda preparado conceptualmente para:

- lector microSD USB-C directo;
- lector detrás de una base USB-C alimentada externamente.

No se asume que la segunda configuración funcione: el host actual no implementa gestión de hub USB. Un hub requiere enumeración de hub, TT/puertos, direccionamiento y gestión de alimentación antes del lector MSC. Las interfaces compuestas también se rechazan si no se encuentra una interfaz MSC compatible clase 08/subclase 06/protocolo 50.

## Política de seguridad

Admitidos sólo:

- TEST UNIT READY.
- REQUEST SENSE.
- INQUIRY.
- READ CAPACITY(10).
- READ(10).

Rechazados:

- WRITE(10).
- Format.
- Erase.
- Comandos SCSI desconocidos.

No se activó `husb_init()`, enumeración, VBUS, CC ni un dispositivo físico.

## Estado de cierre

5L queda **completada en entorno de pruebas** para lector MSC directo emulado y catálogo FAT32. El soporte físico USB-C y cualquier hub siguen pendientes de validación eléctrica y de protocolo en hardware autorizado.
