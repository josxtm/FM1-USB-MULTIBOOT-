# FASE 5K — ABI, memoria y recuperación RAM

## Validador

Se añadió `scripts/validate_ram_image.py`. Es un validador estructural independiente y no ejecuta ELF ni imagen APP1.

Comprueba:

- ELF32 little-endian.
- Ausencia de símbolos indefinidos en el ELF enlazado.
- Ausencia de símbolos explícitos de programación/borrado/ejecución prohibida.

También informa SHA-256 del segmento plano para identificación reproducible.

- Machine `JieLi pi32v2` (`241`).
- Tipo executable.
- Entry esperado, por defecto `0x01C30000`.
- Tabla de program headers.
- Segmentos `PT_LOAD` y límites de archivo.
- Correspondencia exacta entre segmento ejecutable y binario plano.
- BSS dentro de la ventana documentada.
- Exclusión de la región retenida desde `0x01C7FD80`.
- Tamaño y alineación APP1.
- Trailer `APP1`, placa FM1 y formato 1.
- CRC16 XMODEM.
- SHA-256 informativo del binario plano.

El resultado `VALID SOFTWARE ONLY` no autoriza ejecución física.

## Estado del contrato

El SPEC público de m-bootloader documenta formatos y algunos contextos de RAM, pero no se ha verificado contra el estado interno del m-bootloader `2.4-395E6CC` mediante una ejecución experimental.

Continúan sin prueba física:

- Entry real aceptado por el bootloader instalado.
- Valor y significado del argumento de entrada.
- Stack heredado y stack libre.
- Vectores e interrupciones.
- Estado de watchdog.
- Clocks heredados.
- Rango completo de SRAM.
- Retorno de una aplicación.
- Efecto de un fallo de aplicación.
- Colisiones con buffers o memoria retenida.

La dirección `0x01C30000` sigue siendo una hipótesis de enlace basada en documentación y artefactos, no una garantía para el FM-1.

## Recuperación

Una futura prueba RAM sólo podría considerarse después de:

1. Identidad de bootloader verificada.
2. Imagen validada por el script y revisada manualmente.
3. Backup independiente cuando sea aplicable.
4. Procedimiento de recuperación probado sin depender de la aplicación.
5. Ausencia de escrituras Flash.
6. Plan de alimentación y retorno.
7. Autorización específica del propietario.

No se envía ninguna imagen al FM-1 en esta fase.

## Estado de cierre

5K queda **completada en software como contrato y validador estructural**, con las incertidumbres físicas explícitamente registradas. No existe autorización de ejecución RAM.
