# Run-ram frente a stage-2

## Perfil A — stage-2

El SPEC público describe una llamada C normal con un argumento que expresa MHz LSB, normalmente 60, después del cierre de sesión. El stage-2 puede retornar y debe preservar/restaurar el estado relevante. Esto no equivale a recibir un descriptor ROM.

## Perfil B — run-ram

El SPEC describe un puntero a palabras/descriptor ROM, identificado por un valor >= `0x01000000`. La aplicación debe controlar interrupciones/watchdog, stack, USB, reloj y transición de salida/reinicio. El retorno normal no se puede suponer.

## Decisión 5P

El protocolo Android disponible permite construir y simular una secuencia de bloques y un registro de salto, pero no demuestra cuál perfil invoca la imagen en el dispositivo instalado `2.4-395E6CC`. El startup mínimo se etiqueta como **candidato run-ram experimental**, no como stage-2. No se reutiliza el mismo contrato ni se convierte el argumento silenciosamente.

Antes de hardware faltan caller exacto, registros, reloj heredado, vector, watchdog, mapa SRAM, BSS/.data, retorno y recuperación. M-Flasher es evidencia de protocolo/host y no sustituye la validación del caller ROM instalado.
