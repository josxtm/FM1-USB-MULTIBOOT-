# AC791N / FM-1 — mapa de memoria con niveles de evidencia

| Región/dato | Clasificación | Fuente/limitación |
|---|---|---|
| Flash SPI 1 MiB, JEDEC observado 0x856014 | Evidencia propia de identidad; tamaño externo del protocolo | No se leyó Flash físicamente en esta fase |
| `0x00000..0x03FFF` boot/config | Documentado por terceros y protegido por política | [m-bootloader SPEC §5](https://github.com/jvitkauskas/m-bootloader/blob/main/docs/SPEC.md), [Melodee BOOT-SAFETY](https://github.com/keremimo/melodee/blob/main/docs/BOOT-SAFETY.md) |
| aplicación desde `0x04000`, tablas y entrada XIP | Documentado por terceros para formatos conocidos; aplicabilidad al FM1 presente no revalidada | m-bootloader SPEC §§5, 7 |
| `0x01C02000`, `0x01C0A800`, `0x01C20000`, `0x01C28000` | Documentado por m-bootloader para boot/stage/loader; no contrato aprobado para este proyecto | SPEC §§2–4, 11.5 |
| `0x01C30000` linker pi32v2 de este proyecto | Hipótesis de enlace; assert virtual 64 KiB | No es prueba de SRAM física |
| SRAM total, stacks, vectores, XIP exacto de nuestra imagen | Desconocido | Requiere ABI/imagen y prueba autorizada |
| ST7789 240×240 | Evidencia propia del hardware/proyecto y documentación externa | No se inicializó pantalla físicamente |
| USB0 registros `CON0/CON1`, `DEVCTL`, endpoints | Documentado por terceros y código propio husb; no validado eléctricamente | Melodee `fm1_usb.h`, `src/husb.c` |

No se modifica ni se recomienda leer/escribir la región protegida. Valores externos son referencias de investigación, no autorización de acceso.
