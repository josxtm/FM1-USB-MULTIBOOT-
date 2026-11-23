# Fase 6A · Auditoría de seguridad

El objetivo es una imagen de RAM para LCD, GPIO de teclas y temporizador. El enlace incluye un inventario explícito de objetos sin controlador SPI0/SFC, actualizador, almacenamiento, comandos BOT, lector de eFuses ni acceso a configuración, calibración, claves o datos de fábrica. El trampolín no toca la memoria retenida, no encadena un binario musical y no registra callbacks. `MB_LAUNCH_BLOCKED` y `FlashRegionPolicy` permanecen en sus ubicaciones y con sus políticas anteriores.

| Efecto admitido | Límite |
|---|---|
| CPU e SRAM | `cli`, BSS propia y dos pilas privadas. Entrada y bucle de fin comprobados mediante el desensamblado. No se ejecuta `rti` ni se instala un vector. |
| Reloj | Sólo los dos campos documentados para el perfil ROM. La lectura de confirmación debe coincidir antes de usar periféricos. |
| P33 | Únicamente dos tramas fijas dirigidas al watchdog volátil `0x80`, una máscara `0xEF` y una lectura de confirmación. Son distintas de las secuencias de eFuse. Si el puente no responde se corta la trama y se deaserta su selección. |
| USB | El único símbolo USB enlazado es `usb_disable`. Escribe `0` en el puente y `0x0C` en el puerto de control para dejar D+/D− pasivos. Sin USB Host, VBUS, control físico de CC ni energización externa. |
| Periféricos del ensayo | TMR0, SPI1 al LCD y los GPIO de teclas del FM‑1. El fin desactiva TMR0 y suelta el bus y las teclas. No se solicita un reinicio ni se modifica la solicitud de arranque retenida. |

`validate_rom_diag.py` comprueba los segmentos del ELF, la correspondencia con el payload, los símbolos prohibidos, las pilas y la secuencia exacta de entrada. El mock de MMIO prueba la preparación real, limita los registros y los bytes P33 admitidos, verifica que los fallos cortan fases posteriores y prueba la publicación de la pantalla mediante el driver ST7789. El dry-run modela los bloques de RAM con un receptor en memoria y detiene el salto tras un timeout. Estas evidencias auditan el código y el enlace. La ejecución, la frecuencia efectiva y el estado de Flash sobre el dispositivo siguen sin medir.

El código de 5Z, los auditadores Flash, el simulador 5Y, bootloader y DARK‑1 quedan fuera del nuevo diagnóstico. CI fija las huellas del BIN y ELF 5Z y también comprueba el APP1 histórico de la app ya existente. No se usa un respaldo real en los fixtures.
