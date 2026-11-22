# USB0 → MSC → FAT32

La ruta física prevista es:

```text
husb USB0 (EP0/Bulk IN/Bulk OUT)
  → storage.c (enumeración MSC 08/06/50 y pipes)
  → msc_bot (CBW/CSW/SCSI read-only)
  → block_device de 512 bytes
  → fat32.c
  → embedded_fat32_storage.c
  → FM1CAT/core/Cover Flow
```

`tests/usb_storage_test.c` prueba enumeración, descriptors, endpoints, TUR, REQUEST SENSE, READ CAPACITY, READ(10), CSW, timeout, disconnect, MBR/superfloppy y FAT32. `tests/msc_fat32_integration.c` ejecuta la cadena completa simulada BOT → bloques → FAT32 → `mb_fat32_storage` → FM1CAT/core → renderer y launch bloqueado para 0/1/2/3/64 entradas. No declara hardware.

El acceso a registros permanece encapsulado en `husb.c`; el objeto pi32v2 de `msc_bot.c` es freestanding y no enlaza host libc. No se envían comandos de escritura.
