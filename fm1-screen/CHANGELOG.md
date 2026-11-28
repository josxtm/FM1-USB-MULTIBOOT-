# Cambios de FM1 SCREEN

## v1.0 · compilación 16

- FULL CONTROL en teléfono horizontal reserva una franja propia para los indicadores de navegación. La prueba Android comprueba que su zona táctil quede fuera de los pads.

## v1.0 · compilación 15

- El interruptor de demostración aparece en la primera fila del panel Vista. La prueba en teléfono lo activa en vertical y verifica la orientación física del emulador antes de capturar las vistas horizontales.
- La misma APK pasó las pruebas de teléfono y tablet tras ajustar el emulador a su resolución observada.
- La compilación 14 publicó la APK Android y superó las pruebas JVM y Lint. La prueba de teléfono se detuvo al buscar DEMO en horizontal. Se conserva el artefacto 14 como antecedente.

## v1.0 · compilación 14

- Entrega exclusivamente Android. La biblioteca interna usa Kotlin/JVM y conserva los módulos de comunicación, protocolos, estado y GUI existentes. Se retira el plugin Kotlin Multiplatform y la planificación de otros destinos de la documentación activa.
- La prueba Android 16 para teléfono desplaza el panel de ajustes hasta el interruptor de demostración. La primera ejecución de la compilación 13 pasó en tablet y se detuvo en móvil antes de activar DEMO. La compilación 14 conserva la funcionalidad y los perfiles de la 13 con un versionCode nuevo.

## v1.0 · compilación 13

- Sesión, geometría, temas y framebuffer RGB565 en un núcleo Kotlin común con destino JVM. Android conserva los adaptadores MIDI y Compose.
- Etiquetas centradas dentro de los 27 pads con ajuste a espacios pequeños. Estado provisional, inferido, desconocido y confirmado por el FM-1.
- Colores independientes para pads activos, texto de pads y botones activos, con lectura de temas schema 1 anteriores.
- Reintentos con secuencia, descarte de parche repetido, revocación de control remoto ante error y bloqueo de SAVE.
- Perfiles de referencia para POLLY y LOOM-8. La matriz identifica ediciones con emisor y detalla que LOOM-8 0.19 lo deshabilita.
- Diagnóstico de tráfico, bloques, descartes, renderizado y tiempo de decodificación. Pruebas en JVM y flujo de GitHub Actions con instalaciones Android 16.
- Preparación de icono adaptativo. El recurso aprobado está pendiente.
- La distribución conserva el applicationId `com.josxtm.fm1screen`. El certificado se documenta junto al artefacto.

## v1.0 · compilación 12

Base de la fase 2, commit `971467317fafe5e7620f88502c7a2da3fd948e23`, PR #25. Tres vistas, edición de colores, USB/BLE MIDI, perfiles y receptores gráficos. Las 15 pruebas JVM y Android Lint pasaron en la auditoría inicial. La fase detectó acoplamiento del reloj de sesión a Android, instantáneas ARGB, contraste y tamaño de etiquetas mejorables y falta de validación automatizada en móvil.
