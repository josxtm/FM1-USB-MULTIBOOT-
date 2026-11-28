# Viabilidad del Raspberry Pi Zero W original

El Zero W original tiene un BCM2835 de un núcleo ARM1176 ARMv6 y 512 MB. El Zero 2 W tiene un Cortex-A53 ARM64. El perfil de CPU, RAM y controladores de Pi 4 y Pi 5 se evaluará en sus propias pruebas. [Raspberry Pi Zero W](https://www.raspberrypi.com/products/raspberry-pi-zero-w/) · [Raspberry Pi Zero 2 W](https://www.raspberrypi.com/products/raspberry-pi-zero-2-w/).

1. Arrancar un sistema Linux de 32 bits compatible con ARMv6 y verificar USB Host, pantalla táctil y desconexión. Fijar la versión exacta de OS, kernel y controlador.
2. Medir ALSA MIDI con una sesión USB real. Para ARMv6, comparar un proceso C++/SDL2 con una JVM ARMv6 disponible para la imagen elegida. Usar vectores de RGB565 comunes para validar ambos.
3. Presentar el LCD 240 × 240 por DRM/KMS, o por la salida que admita el controlador. Una imagen nativa ocupa 112,5 KiB. Dos búferes ocupan 225 KiB, antes de superficies de vídeo, bibliotecas y SO.
4. Medir en el Zero W original RSS máximo, carga de CPU, FPS, latencia real, uso de swap, tensión USB y estabilidad durante diez minutos. Probar también desconexiones y reinicios de aplicación por systemd.
5. Preparar arranque directo sin escritorio, recuperación por systemd, funcionamiento offline, alimentación portátil y apagado ordenado. Confirmar que el adaptador OTG trabaja en modo Host y mantiene alimentación estable.

Pendientes el prototipo, los resultados de consumo y el empaquetado IMG.XZ. El punto de partida es un panel Big Screen y un único transporte USB. [SDL2 para Raspberry Pi](https://wiki.libsdl.org/SDL2/README/raspberrypi) · [Sistemas operativos de Raspberry Pi](https://www.raspberrypi.com/software/operating-systems/).
