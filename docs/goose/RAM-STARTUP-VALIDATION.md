# RAM-only — validación de startup

## Secuencia implementada

`src/multiboot_ram_start.S` realiza una secuencia mínima:

1. Deshabilita interrupciones según la sintaxis pi32v2 existente.
2. Carga stack primario y secundario definidos por linker.
3. Llama `embedded_ram_app_entry`.
4. Si retorna, entra en parada local (`goto .`).

`embedded_ram_app_entry`:

1. Valida un argumento mínimo documentado como hipótesis.
2. Limpia `.bss` usando `mb_bss_start`/`mb_bss_end`.
3. Inicializa TMR0 mediante `timer_start_unconfigured()`.
4. Requiere una tasa de ticks/ms explícita antes de usar debounce o timeouts.
5. Inicializa LCD y crea el core con backend DEMO.
6. Entra en el bucle de eventos sólo con reloj calibrado.

## Corrección del temporizador

La aplicación ya no convierte `tb_now()` dividiendo por `48000`.

El timer expone:

- `timer_start_unconfigured()` — configura TMR0 sin inventar frecuencia.
- `timer_set_ticks_per_ms()` — configuración explícita futura.
- `timer_calibrated()`.
- `timer_ticks_per_ms()`.

El diagnóstico heredado conserva `timer_start()` con su valor histórico de 48000 ticks/ms. Ese valor queda separado del preflight RAM y no se presenta como frecuencia física garantizada.

`embedded_clock.c` implementa la lógica pura de tiempo con:

- tasa explícita;
- aritmética unsigned para wraparound;
- detección de saltos ambiguos mayores de medio rango;
- rechazo de tasa cero;
- conversión con resto acumulado.

## Hipótesis pendientes

No está demostrado que el bootloader entregue TMR0 configurado ni qué reloj LSB hereda una aplicación. Por seguridad, la entrada RAM-only se detiene si no existe calibración explícita. Esto evita ejecutar navegación con tiempos incorrectos o asumir 48/60 MHz.

No se ha ejecutado el startup en el FM-1.
