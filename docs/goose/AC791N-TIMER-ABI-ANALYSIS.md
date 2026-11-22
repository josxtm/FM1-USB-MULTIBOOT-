# FASE 5O — análisis de temporización AC791N/pi32v2

## Resultado

No existe una frecuencia física medida en el FM-1 instalado. Por tanto, `timer_start_unconfigured()` configura TMR0 pero deja la tasa en cero y la entrada RAM se detiene si no recibe una tasa explícita. Esto no es una calibración implícita.

## Evidencia separada

* `build/research/mboot/docs/SPEC.md` documenta para el m-bootloader público un oscilador de 24 MHz, sistema 360 MHz, HSB 180 MHz y LSB 60 MHz. Documenta TMR0 en `0x10400`, un conteo por ciclo LSB, control `0x4000` para limpiar y `1` para arrancar, y periodo `(LSB_Hz/1_000_000)*us`.
* `build/research/mboot/src/platform.c` contiene los estados de reloj de 24, 60 y 48 MHz y `hal_timer_clock()`. Es evidencia de una implementación pública, no una lectura del FM-1 2.4-395E6CC.
* Melodee documenta TIMER4 como contador libre a 24 MHz bajo UBOOT y TIMER5 como tick de 10 kHz. Es otra familia/capa de software y no se sustituye por TMR0 sin demostrar compatibilidad.
* No se encontró una API segura que entregue al programa RAM la frecuencia heredada. El argumento de stage-2 documentado es MHz LSB; el de `run-ram` es un puntero a descriptor ROM. No son intercambiables.

## Correcciones de software

`tb_ms_since()` devuelve cero ante tasa no configurada. `delay_us()` devuelve sin tocar MMIO ante tasa cero, usa intermedio de 64 bits, redondeo hacia arriba, límite de delta a menos de media vuelta y guardia contra overflow. La conversión no convierte una hipótesis en una medición.

## Decisión

60 MHz LSB es una **fuente técnicamente justificable para un perfil m-bootloader público**, no una frecuencia aprobada para el hardware instalado. Sólo podría usarse después de confirmar el contexto de entrada y que el bootloader instalado conserva ese contrato; alternativamente el caller debe pasar explícitamente MHz. La validación física queda bloqueada.
