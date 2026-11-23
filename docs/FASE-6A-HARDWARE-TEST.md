# Fase 6A · Ensayo de pantalla y teclas pendiente de autorización

## Condiciones previas

El artefacto es experimental. Esta fase entrega la preparación, con decisión **NO-GO físico** hasta recibir autorización específica, disponer de un anfitrión de desarrollo compatible y comprobar el camino de vuelta. El entorno de compilación carece de acceso USB al FM‑1. Conservar la copia externa privada previamente verificada y anotar cómo abre recuperación y cómo arranca el firmware actual. Este ensayo no requiere solicitar ni publicar una lectura de la Flash o de identificadores confidenciales.

Antes del ensayo, cotejar `SHA256SUMS.txt`, el ELF, el mapa y `ABI-SAFETY.txt`. El operador debe verificar la versión revisada de M‑Flasher y confirmar el FM‑1 concreto. Mantener desconectado cualquier lector microSD o accesorio que pueda suministrar VBUS. Preparar una sesión fría que llegue a **DOWNLOAD MODE** desde la recuperación existente con un único FM‑1 conectado al anfitrión. Ante una conexión ambigua, un cargador residente conocido o un problema de recuperación, detener la prueba.

## Secuencia propuesta para una prueba autorizada

1. Cargar una única vez la imagen **`fm1-rom-hardware-test.bin`**, en RAM a **`0x01C30000`**, mediante el subcomando específico `run-ram` del M‑Flasher revisado. Los parámetros previstos son `run-ram fm1-rom-hardware-test.bin --addr 0x01C30000`. El selector STG2 5Z se reserva para la vía **SD CARD**. Esta secuencia excluye opciones de comandos adicionales, lectura, restauración, actualización y reinicio remoto.
2. Observar la desconexión USB y buscar `FM1 MULTIBOOT`, `HARDWARE TEST`, `DISPLAY: TEST`, `KEYS: TEST` y `FLASH: NOT WRITTEN`. La indicación `TMR0 SEC` debe avanzar. Compararla de forma aproximada con un reloj externo si se quiere ensayar el temporizador. La etiqueta 48 MHz describe el perfil configurado y no mide el oscilador.
3. Pulsar OCT−, OCT+, PLAY y REC por separado. El diagnóstico pinta en verde las teclas debounced que mantiene pulsadas y marca `SEEN` tras detectarlas. Una tecla sostenida al entrar se ignora hasta soltarla. Una pulsación corta de REC sólo prueba el botón.
4. Mantener REC dos segundos para terminar. El límite alternativo es 120 s. Con pantalla disponible, la sesión muestra `TEST FINISHED`, `TIME LIMIT REACHED` o `TMR0 ERROR` y `POWER OFF THEN ON`. Apagar y encender con el interruptor, comprobar el arranque habitual y comprobar después que continúa accesible el menú de recuperación.

Si no aparece la pantalla, se pierde el acuse, aparece un reinicio imprevisto o el temporizador se detiene, no inferir éxito por la desconexión. Detenerse, apagar, volver por la ruta conocida y registrar el último resultado observado. No reenviar automáticamente el salto. Un arranque normal posterior acredita recuperación funcional básica. La fase no certifica igualdad física de Flash mediante una observación de pantalla.
