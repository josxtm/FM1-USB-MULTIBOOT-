# PRESETS — decoder y evidencia

Se añadió `src/embedded_presets.c/.h`, un decoder de cuadratura independiente del pinout. La HAL entrega muestras A/B de dos bits; el decoder usa tabla de transiciones, rechaza saltos inválidos, requiere cuatro transiciones por detente por defecto y produce `MB_EVENT_NEXT`/`MB_EVENT_PREVIOUS` sin bloquear.

La prueba host cubre giro horario, antihorario, detente e inválidos. El campo `encoder_scan` es opcional en la HAL de `embedded_ram_app`; si no existe, la ruta de teclas anterior sigue funcionando.

No se afirmó un pinout físico: la conexión exacta de PRESETS/encoder 6 del FM-1 instalado no está documentada con evidencia suficiente en el repositorio. `keys.c` conserva únicamente el escáner físico conocido de controles existentes. La integración eléctrica queda pendiente de una fuente pública verificable o una prueba autorizada.
