# ADR 0001 · Núcleo Kotlin común y adaptadores de plataforma

**Estado** · Aceptado para la compilación 13. La viabilidad de ARMv6 requiere un prototipo medido.

| Opción | Reutilización | Restricciones y decisión |
|---|---|---|
| Kotlin Multiplatform | Conserva protocolo, validación, sesión y modelos Kotlin. Comparte código con JVM y destinos Apple cuando se incorporen. | Adoptado para el núcleo. El primer destino es JVM. Kotlin/Native carece de un destino Linux ARMv6 oficial. |
| Compose Multiplatform | Puede conservar componentes y geometría en escritorio y Apple después de aislar dependencias de Android. | Evaluación posterior. Compose Desktop publica soporte para Linux de 64 bits. Android conserva Jetpack Compose. |
| C++ | Permite una implementación pequeña con acceso a ALSA y libusb en Linux ARMv6. | Ruta alternativa si la JVM no cumple el presupuesto del Zero W. Exige otra implementación de los decodificadores y validación cruzada con la JVM. |
| Qt/QML | Ofrece ventanas, entrada táctil y controles visuales en escritorio. | Evaluar en sistemas de 64 bits si se necesita integración nativa. El Zero W requeriría demostrar el soporte y presupuesto de Qt concretos del sistema. |
| SDL2 | Permite presentar el RGB565 con una interfaz ligera sobre Linux. | Candidato para un prototipo dedicado ARMv6 con DRM/KMS o salida compatible, si el controlador de la pantalla elegida lo permite. La interfaz deberá implementar sus propios controles. |

Mantener Android Kotlin preserva la GUI adaptada bajo GPL, las pruebas y el ciclo de vida USB y BLE que ya existen. El cambio reutiliza el plugin Kotlin instalado y mantiene Android libre de dependencias JNI nuevas. El contrato de sesión intercambia bytes, tiempo y eventos. Cada adaptador tendrá una política de identidad y capacidad equivalente a la de Android.

Al añadir cada destino, compilar el núcleo en su toolchain, ejecutar los vectores de prueba compartidos y verificar conexión y pantalla con un FM-1. Si el prototipo ARMv6 requiere C++, mantener los mismos formatos JSON y casos de protocolo, sin mover el núcleo Android a JNI.

Fuentes oficiales consultadas el 28 de noviembre de 2026. [Destinos Kotlin/Native](https://kotlinlang.org/docs/native-target-support.html) · [Compatibilidad Compose Multiplatform](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html) · [Plataformas Qt 6](https://doc.qt.io/qt-6/supported-platforms.html) · [SDL2 en Raspberry Pi](https://wiki.libsdl.org/SDL2/README/raspberrypi).
