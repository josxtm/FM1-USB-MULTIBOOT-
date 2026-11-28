# Hoja de ruta multiplataforma

| Plataforma | Situación | Adaptador o distribución prevista |
|---|---|---|
| Android móvil y tablet | App ARM64 compatible y pruebas de instalación y vistas en emuladores Android 16 x86-64. Validación del FM-1 pendiente. | APK universal de la app, Android MIDI USB/BLE y diagnóstico USB Host. |
| Windows x86-64 y ARM64 | Diseño | Evaluar Windows MIDI Services y BLE MIDI por versión del sistema. EXE/MSI. |
| macOS Intel y Apple Silicon | Diseño | Core MIDI USB y BLE, prueba de conexión y permisos. DMG firmado. |
| Linux x86-64 y ARM64 | Diseño | ALSA RawMIDI o secuenciador. Evaluar libusb únicamente para un endpoint con LCD documentado. DEB/AppImage. |
| Raspberry Pi Zero W ARMv6 | Estudio de viabilidad | Medir proceso dedicado con ALSA y SDL2, o con una JVM que funcione en el sistema de 32 bits elegido. |
| Raspberry Pi Zero 2 W, Pi 4 y Pi 5 ARM64 | Diseño | Reutilizar adaptador Linux, verificar framebuffer y modo quiosco en cada dispositivo. IMG.XZ tras las pruebas. |
| iPhone e iPad ARM64 | Diseño | Core MIDI y aplicación firmada. Requiere validar USB MIDI de clase y BLE con los accesorios elegidos. Acceso a endpoints propietarios pendiente de evidencia. |

El siguiente adaptador podrá pasar al núcleo un reloj monotónico, salida MIDI validada y eventos de conexión. Para cada sistema se debe comprobar la entrega de fragmentos, desconexión y recuperación, tamaño de transferencias y comportamiento sin Internet. Los perfiles y temas conservarán el esquema 1. WebSocket queda como opción de un futuro puente con autenticación y modo pasivo predeterminado.

Fuentes. [Windows MIDI Services](https://microsoft.github.io/MIDI/) · [Apple Core MIDI](https://developer.apple.com/documentation/coremidi) · [Imágenes Raspberry Pi OS](https://www.raspberrypi.com/software/operating-systems/) · [Estudio ARMv6](RPI-ARMV6-FEASIBILITY.md).
