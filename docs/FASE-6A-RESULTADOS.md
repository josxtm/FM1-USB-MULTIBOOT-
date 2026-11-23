# Fase 6A · Diagnóstico ROM nativo FM‑1

Se generó `fm1-rom-hardware-test.bin`, candidato pi32v2 específico para ROM `run-ram`. Muestra un diagnóstico de ST7789, OCT−, OCT+, PLAY, REC y un contador de TMR0. El fin de la sesión libera los periféricos y espera un ciclo del interruptor. La comprobación del código de entrada no presupone retorno a la ROM. El selector STG2 5Z sigue usando su propio ABI y su BIN y ELF conservan las huellas originales.

| Medida | Resultado |
|---|---|
| APP1 | 4.100 B, placa 1, versión 1, CRC16 `0xB786` |
| Entry ELF32/pi32v2 | `0x01C30000` |
| BSS | 1.080 B desde `0x01C16800` |
| Pilas privadas | 4.096 B principal y 1.024 B de excepciones, fin exclusivo `0x01C18038` |
| Pila estimada | Mayor marco estático 108 B. Suma de marcos emitidos 696 B. Sin medición física de pico. |
| SHA‑256 BIN | `a0012d9f32b414ee38aa8b76e0f412b64a77c4163001037a44b77ac8eda91cbb` |

Las pruebas en host ejercitan la salida de 240 × 240, los botones simulados, el debounce, la transición de contador, fallos de reloj y watchdog, desconexión USB pasiva y fallos de TMR0 o SPI. El receptor de RAM simulado procesa nueve bloques y registra el salto en el caso correcto. En timeout, el registro omite el salto. Se conservan 34 pruebas Python de las fases anteriores, la regresión 5Y de respaldo y recuperación, la del auditor Flash y la carga del STG2 en el harness de m‑bootloader exacto. La captura adjunta procede del render C en host. El binario no se ha ejecutado sobre un FM‑1.

**NO-GO físico pendiente de autorización.** Está preparado el candidato, su auditoría y un procedimiento propuesto. Falta un anfitrión de desarrollo conectado, una sesión ROM fría controlada, autorización y comprobación del ciclo de salida en el FM‑1. El diagnóstico no toca Flash, bootloader ni DARK‑1. La futura selección de firmware musical conserva la vía de instalación controlada en Flash y la ejecución XIP. El diagnóstico temporal no es ese instalador.

[Contrato ROM](FASE-6A-ROM-ABI.md) · [Procedimiento de ensayo](FASE-6A-HARDWARE-TEST.md) · [Auditoría](FASE-6A-SAFETY.md) · [Decisión microSD](FASE-6A-MICROSD-DECISION.md)
