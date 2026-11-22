# Fase 5Z · Validación reproducible

```sh
make stg2-host-test
python3 -m unittest discover -s tests -p '*test.py'
make TOOLCHAIN=toolchain stg2-selector
make TOOLCHAIN=toolchain MBOOT24=/ruta/al/checkout-395e6cc stg2-v24-test
make virtual-slotfile-test virtual-slotfile-demo
```

| Prueba | Evidencia |
|---|---|
| Imagen y ABI | Cuatro pruebas nuevas Python para el vector CRC v2.4, límites, corrupción/APP1 y dirección/arquitectura equivocadas. Auditoría de ELF real y dos segmentos, redondeo de lectura, presupuesto de pila, símbolos, bytecode de entrada y restauración de pila a partir del desensamblado. |
| Carga en v2.4 | `stg2_v24_bridge` incluye el `sdboot_test.c` del checkout exacto y coloca el STG2 compilado en una imagen FAT32 fragmentada. `load()` carga los mismos bytes en la ventana esperada en MBR y superfloppy. Se rechazan CRC incorrecto y cadena cortada. Se ejecuta el cargador C en host; las instrucciones pi32v2 del payload no se ejecutan en esa prueba. |
| Lectura y UI | `stg2_selector_test` usa SPI simulado y FAT32 real como formato, 12 entradas y dos páginas. Montaje, navegación, PLAY/detalle/verify, salida REC, CRC16 de datos, SHA‑256 correcto e incorrecto, índice demasiado grande, FAT dañado, ausencia, desconexión y fallo a mitad de un bloque. El mock afirma la lista de comandos de solo lectura. |
| Cesión de bus | `stg2_spi_mmio_test` ejecuta la rutina C de plataforma contra registros simulados. Comprueba NOP, cambio de PC7/PC8, frecuencia, devolución a LCD y salida por SPI sin respuesta. No accede a USB0 o Flash. |
| Ausencia de VBUS | La regresión de 5Y mantiene USB‑C fuera de la ruta preboot. La auditoría del ELF impide enlazar `husb`, reset o escritura Flash en STG2. Ninguna prueba virtual mide tensión o verifica la placa. |
| Regresión | Se conservan las 30 pruebas Python anteriores y el suite C99 existente de catálogo, USB/BOT, FAT32, selector textual, política de lanzamiento y `virtual_slotfile`. La suite Python total pasa 34 pruebas. |
| APP1 histórico | El escáner GPIO omite el acceso al PHY sólo en el STG2 FM‑1. La compilación heredada conserva la imagen APP1 de 10.436 B, idéntica al asset Android. |

GitHub Actions recompila el selector con `pi32v2-llvm 23.1.5-2` verificado por hash y usa el repositorio upstream fijado al commit de v2.4 para ejecutar el puente. El artefacto experimental se publica por separado con BIN, ELF, mapa, desensamblado, SHA‑256, informes ABI/pila, captura PPM y logs. Los ensayos históricos que generan otros artefactos de regresión no son una vía de arranque para este selector.
