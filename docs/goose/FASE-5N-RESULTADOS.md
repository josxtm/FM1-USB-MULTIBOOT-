# FASE 5N — resultados de consolidación RAM-only

## Implementado

- Contrato de reloj explícito y seguro ante wraparound.
- Inicialización TMR0 separada entre diagnóstico legacy y aplicación RAM-only.
- Backend DEMO y backend `mb_storage` externo seleccionables por la misma aplicación.
- Prueba end-to-end de catálogo interno y backend USB emulado.
- Documentación de stack, startup, memoria y limitaciones ABI.
- CI actualizado con `clock-test`, aplicación RAM, validador APP1 y objetos pi32v2.

## Problema corregido

La aplicación utilizaba `tb_now()/48000` directamente y no inicializaba explícitamente TMR0. Se eliminó esa conversión fija.

Ahora:

1. Se inicializa TMR0 con `timer_start_unconfigured()`.
2. Se exige una tasa ticks/ms explícita.
3. `embedded_clock` convierte ticks usando la tasa configurada.
4. Los saltos ambiguos y reloj no operativo detienen el flujo.
5. El código no asume que 48 MHz o 60 MHz sean válidos para la unidad real.

El diagnóstico anterior conserva su comportamiento legacy separado.

## Pruebas

Pasaron localmente:

```text
make ram-app-test
make clock-test
make launch-policy-test
make ram-image-validator-test
make test
make msc-bot-test
make msc-fat32-test
make lcd-renderer-test
```

La aplicación usa la misma máquina de estados y renderer para DEMO y USB emulado. La integración FAT32/MSC existente mantiene pruebas 0/1/2/3/64 y fallos adversariales.

## Memoria y stack

El linker experimental conserva APP, LOW, BSS, stack y memoria retenida separadas. El validador comprueba segmentos, BSS, memoria retenida, símbolos indefinidos y símbolos peligrosos.

La profundidad máxima de stack no puede demostrarse solamente con el mapa. Los tamaños estáticos medidos anteriormente permanecen:

```text
mb_core              9096
mb_lcd_renderer       552
mb_key_adapter         20
scanline              480
SHA buffer            512
render framebuffer      0
```

El stack real, SRAM y buffers heredados requieren ABI y ejecución controlada; no se inventa un margen.

## Estado físico

No se conectó USB-C, lector, hub, microSD ni instrumento al FM-1. No se activó USB Host. No se escribió Flash ni se ejecutó RAM. `MB_LAUNCH_BLOCKED` permanece.

## Criterios pendientes para una primera prueba RAM

- Confirmar contrato de entry y argumento con el bootloader instalado.
- Obtener una tasa de reloj válida, o una API de calibración autorizada.
- Confirmar stack y SRAM disponibles.
- Confirmar comportamiento de retorno/parada.
- Verificar LCD y GPIO de forma independiente.
- Mantener recuperación externa y no escribir Flash.
- Autorizar explícitamente la prueba física.
