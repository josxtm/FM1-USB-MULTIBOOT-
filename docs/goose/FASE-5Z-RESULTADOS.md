# Fase 5Z · Primer selector STG2 nativo

**Resultado de software.** Compilado un STG2 pi32v2 que lee `FM1/INDEX.CSV` por SPI1, muestra la lista textual y verifica el archivo elegido sin autorizar su ejecución. El simulador del cargador de `2.4-395E6CC` aceptó los bytes exactos del binario sobre FAT32 fragmentado. El firmware musical sigue arrancando desde Flash en el diseño futuro.

| Medida de compilación | Resultado |
|---|---|
| Imagen | `fm1-multiboot-selector-stg2.bin`, 11.300 B de 32.768 B |
| Entrada / CRC16 | `0x01C28000` / `0x1FDD` |
| SRAM .bss y pilas | 35.384 B BSS, 8.192 B pila principal y 1.024 B pila de excepciones, total 44.600 B de trabajo |
| Rango de trabajo | `[0x01C16800,0x01C21638)` y 11.776 B de carga redondeada a sectores a partir de `0x01C28000` |
| Pila estática | Mayor marco 908 B. Suma de marcos emitidos 3.068 B, estimación estática sin medición en placa. |
| SHA‑256 del BIN | `0e9ef963d5a0c614d09a7bd3dc3ba808b25c611d572e9544f37bd9586c283002` |

El menú de recuperación `2.4-395E6CC` ya está documentado físicamente en la unidad. Sus pruebas de carga, la cesión SPI1, la lectura/validación, la interfaz y el retorno están comprobados sólo en software o por auditoría de ABI. Falta ejecutar el selector con un lector SPI y la pantalla/teclas del FM‑1 concreto.

**Primera prueba física. NO-GO con la configuración actual.** USB‑C carece de entrada MSC en la versión instalada y no están confirmados el rol y VBUS de la placa. SPI1 está preparado en software, pero falta confirmar una interfaz de microSD SPI segura y autorizada. Si existe una unidad de ensayo con esa interfaz verificada, el primer ensayo independiente debe limitarse a **SD CARD → lista de prueba → SHA‑256 → REC → menú de recuperación → BOOT APP**. Exige copia externa recuperable verificada y autorización específica. Si falta el menú, falla la lectura o el retorno es anómalo, se detiene el ensayo. En esta fase no se conecta un lector ni se instala un STG2 en la unidad. El bootloader, DARK‑1 y Flash permanecen intactos.

La rama continúa la PR #10 (`e1c98de`). La selección de archivos usa el mismo catálogo que `virtual_slotfile`; los escenarios de interrupción y respaldo siguen siendo de host. No se ha cambiado la política de instalación física.

[Contrato STG2](FASE-5Z-BOOT-CONTRACT.md) · [Hardware USB‑C](FASE-5Z-USB-C-HARDWARE.md) · [Matriz m-card](FASE-5Z-MCARD-COMPATIBILITY.md) · [Selector](FASE-5Z-SELECTOR.md) · [Pruebas](FASE-5Z-TESTS.md)
