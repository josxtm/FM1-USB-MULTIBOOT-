# Registro compacto USB0

Se añadió `src/usb_diag_log.c/.h`: 32 eventos de 2 bytes, sin heap ni I/O. Cada registro contiene `code` y `detail`; al llenarse conserva los primeros eventos y marca `dropped`.

Códigos: BOOT, ATTACH, RESET, ENUM, ENDPOINTS, BOT, CAPACITY, READ, FAT32, DISCONNECT, TIMEOUT y ERROR. `scripts/decode_usb_diag_log.py` decodifica pares hexadecimales desde argumento o stdin. `make usb-diag-log-test` valida límites y nombres.

La estructura está preparada para instrumentar el diagnóstico sin pantalla. No se presenta todavía como captura física ni se agregan accesos USB automáticos.
