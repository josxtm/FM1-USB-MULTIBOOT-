# Fase 3A — microSD FM1 física, integración de solo lectura

## Qué se ha añadido

El diagnóstico bare-metal en RAM ahora puede encontrar el catálogo que Android Manager ha creado en **FM1/INDEX.CSV**. Antes solo listaba la raíz de FAT32; la biblioteca quedaba en una subcarpeta invisible para el diagnóstico.

El nuevo `fat32_open_fm1_index` navega exclusivamente por los dos componentes 8.3 `FM1` y `INDEX.CSV`, con límites de sectores, clusters y 24 KiB de tamaño. No implementa rutas arbitrarias, escritura ni búsqueda recursiva.

La cuarta página del LCD se denomina `FM1 / INDEX.CSV`: informa de si el fichero se encuentra y si sus primeros bytes incluyen las cabeceras FM1CAT v1. **No valida todas las entradas, firmas o imágenes**, y no ejecuta ningún firmware. Para indexar toda la biblioteca será necesaria lectura secuencial, validación de líneas y rutas hacia `FM1/FW/`.

La prueba automatizada crea un FAT32 artificial en RAM con FM1/INDEX.CSV y verifica hallazgo, vista preliminar, falta de carpeta, cluster inválido y exceso de tamaño del índice. Estas son pruebas de software; no evidencian acceso al lector USB-C del usuario.

## Bloqueos previos a probar en la unidad real

1. La comp5 Android **no carga APP1 físicamente**. Solo identifica `WL82 / UBOOT1.00` y simula comandos. La prueba de USB Host del FM1 requiere un mecanismo RAM físicamente validado, que NO se habilita aquí.
2. No se ha confirmado la circuitería de 5 V **VBUS** ni el rol USB-C host del FM1. El código USB host del chip no prueba que la placa pueda alimentar un lector pasivo. No conectar alimentación externa improvisada.
3. Los firmwares de terceros pueden estar en formato de Flash XIP. Que un archivo esté dentro de `FW/` no garantiza ejecución externa ni coexistencia con un arranque persistente.
4. Conservar una copia íntegra de la Flash fuera del FM1 y un procedimiento de recuperación validado antes de programar nada. La sustitución futura de DARK-1 no se realiza en esta fase.

## Secuencia recomendada de evaluación física

- Con la tarjeta retirada del FM1, confirmar desde Android que contiene `FM1/INDEX.CSV` y `FM1/FW/` con dos imágenes y SHA-256 releyendo.
- Revisar la topología eléctrica USB-C de la PCB; medir VBUS en el lector mediante instrumento apropiado sin provocar cortocircuito ni retorno de alimentación; confirmar roles CC, limitación de corriente y protección.
- Solo tras validar la seguridad de carga en RAM, ejecutar el diagnóstico temporal, comprobar pantalla, controles, ausencia de errores eléctricos y detección de lector.
- Verificar lectura FAT32 de solo lectura y cuarta página con `FM1CAT V1 DETECTADO`.
- Desconectar lector, apagar y volver a encender, verificar estado de la Flash y recuperación.
- En fallos de enumeración, documentar VID/PID, clase USB y errores, sin escribir ni forzar Flash.

## Límites de esta entrega

No se ha accedido físicamente a tu FM1 desde GitHub. No se ha instalado una nueva APK ni se ha cargado ningún firmware; las modificaciones solo existen en la rama de desarrollo y sus pruebas CI. No fusionar PR hasta revisión.
