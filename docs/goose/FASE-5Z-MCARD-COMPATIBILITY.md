# Fase 5Z · m-card y acceso SPI1

m-card `ec63784` es una placa intercalada en J12, dentro del FM‑1, entre la placa base y el LCD. El diseño requiere abrir el sintetizador. Toma 3,3 V de J12 y comparte PC9 (CLK), PC10 (datos hacia la tarjeta) y PC8 (datos desde la tarjeta y D/C del panel). Invierte PC7 para que panel y tarjeta queden seleccionados en estados opuestos. El PDF y el README documentan el diseño. El README marca esta PCB como prototipo. [m-card y señales J12](https://github.com/jvitkauskas/m-card/tree/ec63784fe94006296ad25bdeb5d32bf7afa834d1)

El menú del v2.4 hace sondeo SPI1 sólo al entrar en recuperación. Una tarjeta debe responder antes de mostrar **SD CARD**. Para compartir el bus, el selector sigue la transición publicada. Envía NOP al ST7789 con la tarjeta deseleccionada, deja PC8 como entrada y cambia a DI/DO separadas. Inicializa SDHC/SDXC a 400 kHz, obtiene bloques mediante CSD v2 y lee a 4 MHz con CRC16 de datos. Al terminar la sesión devuelve PC8 a salida y la configuración de SPI1 al LCD. El controlador sólo puede emitir CMD0, CMD8, CMD9, CMD17, ACMD41, CMD55 y CMD58. FAT32 exige bloques de 512 B. No hay soporte para SD de direccionamiento por byte en esta entrega. [SPI1 en v2.4](https://github.com/jvitkauskas/m-bootloader/blob/395e6cc469ba1d6eae9922a752d1f416ff07b69a/src/sdboot.c) · [Controlador de referencia y relojes](https://github.com/jvitkauskas/m-apps/blob/748bf0036b784ac38aa4c765c882fd7955e51332/kit/card.c)

| Criterio | USB‑C MSC | m-card/SPI1 |
|---|---|---|
| Intervención | Requiere demostrar primero una ruta de arranque y que la placa pueda actuar como host con alimentación segura. | Requiere intercalador interno documentado y autorización. |
| Recuperación v2.4 | Sin entrada MSC en la revisión instalada. | `SD CARD` documentada para SPI1 y STG2. |
| Alimentación | CC y 5 V sobre esta placa siguen sin caracterizar. | Diseño de 3,3 V compartido con J12. La PCB no está confirmada en esta unidad. |
| Velocidad y fiabilidad | Controlador full-speed según código de referencia. La tasa de lectura efectiva es desconocida. | Datos a 4 MHz nominales en 5Z con CRC por bloque. Tiempo y margen eléctrico por medir en el FM‑1. |
| Selector | **NO-GO** con el equipo actual. | **GO de software**, carga confirmada en la simulación exacta de v2.4. **NO-GO físico** en la unidad actual hasta confirmar hardware compatible y autorizar el ensayo. |

Las pruebas comunitarias citan montajes o prototipos FM‑1 y montajes en SMK‑37. De ellas se reutilizan decisiones técnicas. La variante, el rendimiento y los fallos de un SMK‑37 no se extrapolan al FM‑1 del propietario. [Estado de m-apps](https://github.com/jvitkauskas/m-apps/blob/748bf0036b784ac38aa4c765c882fd7955e51332/README.md)
