# Contrato RAM del m-bootloader 2.4 — clasificación

## Contratos públicos

El SPEC público distingue dos entradas:

1. **stage-2**: llamada C normal, un argumento con la frecuencia LSB en MHz (el stock usa 60), tras cerrar la sesión. Puede retornar y debe preservar/restaurar el estado relevante.
2. **run-ram**: argumento que apunta a palabras/descriptor ROM; un valor mayor o igual a `0x01000000` identifica el puntero. Tras cambiar dependencias no se espera retorno normal. La aplicación debe desactivar interrupciones/watchdog, mover el stack, bajar USB, seleccionar el reloj requerido y reiniciar.

El arranque de bootloader desde excepción usa contexto ROM y `rti/reti/rete` antes de establecer stacks. Eso no demuestra que una llamada stage-2 use el mismo contexto.

## Estado del candidato

`multiboot_ram_start.S` es experimental y actualmente implementa sólo el perfil de llamada directa: `cli`, stacks del linker, `call embedded_ram_app_entry`, parada si retorna. No se presenta como implementación del contexto de excepción ni como sustituto del `run-ram` completo.

La comprobación `rom_arg >= 0x01000000` corresponde al perfil descriptor/run-ram. No se acepta un MHz de stage-2 como si fuera descriptor. La separación queda documentada como requisito antes de cualquier ejecución; no se ha añadido una conversión silenciosa.

## Registro ABI pendiente

Antes de probar hardware hay que obtener del bootloader instalado: punto exacto de llamada, valor/semántica de cada registro, reloj LSB heredado, watchdog, vector/retorno, stack disponible, mapa SRAM y política de USB. La identidad MIDI `2.4-395E6CC` sólo fue leída en modo MIDI y no prueba equivalencia binaria con el SPEC público.

No se modificó el ensamblador para fingir compatibilidad. Esta clasificación reduce el riesgo de pasar un argumento válido para un perfil al otro.
