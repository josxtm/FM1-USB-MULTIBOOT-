# Fase 5 · Configuración de Android

El propietario confirmó la duplicación real con la APK 018 y SKIFF Atlatl v1.2.5. La 019 cambia el acceso a configuración y su diseño. Las comunicaciones USB y BLE, el protocolo, los plazos de sesión y el código que procesa y dibuja el LCD se mantienen con la implementación de la 018.

## Acceso y gestos

En la 018, el tirador de configuración tenía una zona táctil de 88 × 44 dp como mínimo. La colocación variaba según la vista y el factor de escala. La transición de página podía componer a la vez dos tiradores con sus propios manejadores. Un gesto que comenzase fuera del tirador podía quedar asignado al fondo, donde un desplazamiento horizontal de más de 60 dp cambia de página. La auditoría identifica esas rutas posibles. Los pasos exactos del incidente original dependen del punto de contacto y de la transición en curso.

La 019 monta un único engranaje fuera de la transición de FaceplateView. Su área táctil mide 48 × 48 dp y su icono mide 24 dp. El botón usa un clic accesible con estado pulsado, y el gesto del fondo permanece en una capa hermana. La columna del panel recibe el toque aunque se pulse un espacio vacío. La X, el botón Atrás y un toque fuera permiten cerrar. La barra de estado queda como información. La configuración se superpone y las vistas conservan el espacio asignado al LCD.

## Organización y colores

| Categoría | Acceso principal | Controles secundarios |
|---|---|---|
| Conexión | Transporte, dispositivo, duplicación, solo visualización, resincronización | Motivo de bloqueo cuando corresponde |
| Firmware | Selección automática o manual, protocolo e intercambio de perfiles | Evidencia, limitaciones y fuente del perfil |
| Pantalla | Tres vistas, demostración, escala y pantalla completa | Captura, grabación y energía |
| Rendimiento | ULTRA LOW LATENCY, BALANCED y ECO | Valor persistente del sistema de presentación de 018 |
| Apariencia | Selector con muestras, restauración, importación y exportación | Cuadrícula de 15 roles, editor HSV/HEX y gestión de temas |
| Diagnóstico | Estado MIDI, RX/TX, actualización gráfica, latencia interna y sesión | Interfaces, tiempos, percentiles, cola, rendimiento, registros y consola |
| Información | Versión, compilación, repositorio y licencias | Créditos |

En pantallas grandes el ancho se acota a 280–336 dp y se aproxima a un máximo del 30 % donde la anchura disponible lo permite. En móviles se adapta a los límites del área útil. Las siete categorías permanecen arriba. El contenido de la categoría puede desplazarse en pantallas cortas y al expandir contenido técnico. La apertura y el cierre combinan desplazamiento y fundido de 150 ms. La transición de categoría cambia la opacidad en dos etapas.

Se derivan las superficies, los controles, los iconos y los menús de los colores activos del instrumento. Las variantes de texto e iconos tiñen el color elegido cuando se requiere contraste. Las pruebas comprueban 4,5:1 para texto e iconos después de cuantizar a sRGB. Los colores elegidos para el instrumento se conservan.

## Validación reproducible

El workflow descarga el artefacto original de la 018 y comprueba su huella. Cada emulador de Android 16 prueba la 018 y la 019 con la misma resolución y densidad. Compara los límites del LCD, guarda capturas de las tres vistas y de la configuración y mide el coste de los fotogramas de apertura y cierre con `dumpsys gfxinfo`. El dato es tiempo de fotograma de la interfaz en emulador con DEMO. La latencia física extremo a extremo requiere el FM-1.

La prueba de tablet completa 100 ciclos y comprueba apertura y cierre en las tres vistas. En teléfono también se comprueba la rotación. Las acciones rápidas, arrastres que empiezan en el engranaje, swipes de fondo y toques dentro del panel verifican la separación de gestos. La suite comprueba cambios de paleta con la configuración abierta y la persistencia del tema y del modo de rendimiento. El ensayo Atlatl sintético de diez minutos vuelve a ejecutarse para la entrega.
