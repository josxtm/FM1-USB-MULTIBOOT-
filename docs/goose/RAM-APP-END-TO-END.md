# RAM-only — prueba end-to-end

## Flujo validado

```text
startup lógico
 → inicialización del backend
 → FM1CAT
 → Cover Flow
 → botones
 → inspección
 → cancelación
 → verificación
 → lanzamiento bloqueado
```

## Casos DEMO y USB emulado

`tests/embedded_ram_app_test.c` valida:

- DEMO con tres entradas.
- USB MSC mediante `mb_storage` emulado con una entrada.
- Navegación NEXT.
- PLAY/INSPECT.
- REC/CANCEL.
- SHA correcto.
- Backend ausente.
- Renderer host seguro.
- `MB_LAUNCH_BLOCKED`.

La integración FAT32/MSC existente mantiene los catálogos 0/1/2/3/64 y los fallos de desconexión, timeout, corrupción FAT, truncamiento y hash incorrecto.

## Reloj

`tests/embedded_clock_test.c` valida:

- Tasa explícita de 48000 ticks/ms.
- Tasa explícita de 60000 ticks/ms.
- Wraparound de contador de 32 bits.
- Tasa cero.
- Salto ambiguo superior a medio rango.

## Fallos y límites

- Almacenamiento ausente: `MB_NO_STORAGE`.
- Catálogo corrupto: `MB_ERROR` mediante el parser existente.
- SHA erróneo: `MB_INCOMPATIBLE`.
- Timer sin calibración: la app RAM se detiene antes de usar botones.
- Renderer nulo: el core no llama callbacks ausentes.
- Memoria excesiva: límites FM1CAT/parser y aserciones del linker rechazan el caso.

No se emiten saltos a firmware, comandos SCSI de escritura ni instrucciones de programación Flash.

## Estado

La cadena está validada en host y emulación. No es evidencia de funcionamiento del TMR0, LCD, GPIO, USB Host, VBUS, CC, SRAM o ABI en el FM-1.
