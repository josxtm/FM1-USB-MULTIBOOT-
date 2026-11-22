# Fase 5Z · Contrato de arranque STG2

## Comprobado contra el bootloader instalado

La referencia es el tag `v2.4`, commit `395e6cc469ba1d6eae9922a752d1f416ff07b69a`. `boot_main()` detecta PLAY de arranque antes de invocar la aplicación. `menu()` llama a `sd_probe(60)` y ofrece **SD CARD** si la tarjeta SPI1 responde. `sd_stage2(60)` abre SPI, encuentra la tarjeta, lee FAT32 y carga el nombre corto `/MBOOT/MENU.BIN`. El menú abre la ruta sin depender de DARK‑1. En la unidad del propietario se ha comprobado anteriormente la entrada al menú y el regreso por **BOOT APP**. [Fuente de arranque](https://github.com/jvitkauskas/m-bootloader/blob/395e6cc469ba1d6eae9922a752d1f416ff07b69a/src/boot.c) · [Cargador SD](https://github.com/jvitkauskas/m-bootloader/blob/395e6cc469ba1d6eae9922a752d1f416ff07b69a/src/sdboot.c) · [Evidencia previa de la unidad](../PHYSICAL-SD-DIAGNOSTIC-PLAN.md)

| Campo | Contrato y comprobación |
|---|---|
| Carga | `0x01C28000`; la primera instrucción es la entrada C por trampolín. El ELF es de diagnóstico y el archivo STG2 contiene código plano. El cargador sólo reconoce el tráiler y el límite de longitud. La dirección se valida contra ELF y mapa. |
| Contenedor | 16–32768 bytes en total. Ocho bytes finales `STG2`, CRC16‑XMODEM little endian con semilla 0 sobre el payload y dos bytes cero según SPEC. La variante APP1 usa otro contrato. |
| Lectura | Sectores de 512 B, superfloppy o primera partición MBR. El cargador redondea al sector completo. El área de trabajo separada del payload evita que ese exceso pise BSS. |
| ABI | `r0` recibe la frecuencia LSB en MHz; el FM‑1 v2.4 llama con 60. El trampolín no modifica `r0`, guarda `rets` y `r4–r15`, conmuta `sp` y `ssp` a pilas privadas y restaura los dos antes de retornar mediante `pc`. El código C rechaza otro valor de LSB sin tocar periféricos. |
| Reloj e IRQ | SPEC indica sistema 360 MHz, HSB 180 MHz, LSB 60 MHz, watchdog apagado e interrupciones deshabilitadas. Se calibra TMR0 con el reloj entregado, se conserva el valor de sus registros y el retorno se hace con IRQ deshabilitadas. El binario no cambia PLL ni watchdog. |
| RAM y retorno | La zona de bootloader por debajo de `0x01C0A800` y la zona retenida desde `0x01C7FD80` quedan fuera de los segmentos del selector. El trampolín usa la pila del llamante sólo para salvar registros según ABI. El código libera teclas y devuelve SPI1 al panel. El v2.4 restaura su configuración de Flash y pantalla al recuperar control. |

[Especificación v2.4 §11.5](https://github.com/jvitkauskas/m-bootloader/blob/395e6cc469ba1d6eae9922a752d1f416ff07b69a/docs/SPEC.md#115-sd-card-stage-2)

## Comparación con m-apps

Se revisó `748bf0036b784ac38aa4c765c882fd7955e51332`. `apps/menu/menu.c` carga `LAUNCH.BIN`, `apps/picker` cataloga `BOOT.APP`, `kit/start.S` salva pilas y registros, `kit/card.c` conmuta SPI1 entre SD y LCD. `apps/slots` añade verificación de imagen, respaldo y un escritor físico. Su submódulo de bootloader fija `bdfda22`, distinto del instalado. La Fase 5Z adapta la entrada con retorno y la parte de lectura SPI necesaria. Usa nuestro FAT32 y FM1CAT. El código de Flash y las bibliotecas de escritura del upstream quedan fuera del enlace. La carga del **binario exacto** se prueba con el código del bootloader `395e6cc`, no con el submódulo de m-apps. [menu](https://github.com/jvitkauskas/m-apps/blob/748bf0036b784ac38aa4c765c882fd7955e51332/apps/menu/menu.c) · [picker](https://github.com/jvitkauskas/m-apps/tree/748bf0036b784ac38aa4c765c882fd7955e51332/apps/picker) · [slots](https://github.com/jvitkauskas/m-apps/tree/748bf0036b784ac38aa4c765c882fd7955e51332/apps/slots) · [start.S](https://github.com/jvitkauskas/m-apps/blob/748bf0036b784ac38aa4c765c882fd7955e51332/kit/start.S)
