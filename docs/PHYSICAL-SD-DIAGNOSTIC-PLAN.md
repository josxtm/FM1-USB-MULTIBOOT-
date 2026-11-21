# Fase 3A — microSD FM1 física, integración de solo lectura

## Qué se ha añadido

El diagnóstico bare-metal en RAM ahora puede encontrar el catálogo que Android Manager ha creado en **FM1/INDEX.CSV**. Antes solo listaba la raíz de FAT32; la biblioteca quedaba en una subcarpeta invisible para el diagnóstico.

El nuevo `fat32_open_fm1_index` navega exclusivamente por los dos componentes 8.3 `FM1` y `INDEX.CSV`, con límites de sectores, clusters y 24 KiB de tamaño. No implementa rutas arbitrarias, escritura ni búsqueda recursiva.

La cuarta página del LCD se denomina `FM1 / INDEX.CSV`: informa de si el fichero se encuentra y si sus primeros bytes incluyen las cabeceras FM1CAT v1. **No valida todas las entradas, firmas o imágenes**, y no ejecuta ningún firmware. Para indexar toda la biblioteca será necesaria lectura secuencial, validación de líneas y rutas hacia `FM1/FW/`.

La prueba automatizada crea un FAT32 artificial en RAM con FM1/INDEX.CSV y verifica hallazgo, vista preliminar, falta de carpeta, cluster inválido y exceso de tamaño del índice. Estas son pruebas de software; no evidencian acceso al lector USB-C del usuario.

## Recuperación m-bootloader: verificación física del usuario

El usuario ha instalado m-bootloader con M-Flasher. Fotografía física de la pantalla confirma **FM-1 M-BOOTLOADER 2.4-395E6CC** y las siguientes cadenas de pantalla:\n\n- `APP OK` — imagen de aplicación reconocida, longitud `581564 B`.\n- `UPDATE NONE` — no aparece ninguna actualización pendiente.\n- `FLASH 856014 1024K` — ID JEDEC y capacidad nominal mostrados.\n- Menú: `BOOT APP`, `DOWNLOAD MODE`, `MIDI MODE`. La opción MIDI existe visualmente, **pero no acredita por sí sola compatibilidad de una carga APP1 ni su seguridad**.\n\nTambién ha comprobado **en su FM1 real**:

- Mantener PLAY al encender abre el menú de recuperación.
- Elegir Boot app vuelve a arrancar DARK-1 normalmente.
- Según el informe de instalación, el bootloader se grabó en los **cuatro primeros sectores** de Flash.

La Flash Puya de 1 MiB usa sectores de 4 KiB. Para el diseño y las comprobaciones de seguridad, la región de arranque es **[0x00000, 0x04000)** (16.384 bytes), y la región de aplicación estándar comienza en **0x04000**. El área de arranque también puede contener cabeceras, configuración y datos relevantes para arrancar: no es un simple espacio de programa libre.

**Regla obligatoria para cualquier instalador futuro:** ninguna operación de borrado o escritura de firmware de aplicación debe tocar, solaparse ni incluir parcialmente [0x00000, 0x04000). Si un paquete incluye esa región, rechazarlo por defecto; un cambio de bootloader debe ser una operación separada y autorizada, diseñada y verificada específicamente. Mantener también fuera de las operaciones ordinarias los datos de fábrica y zonas protegidas.

El menú de recuperación y el retorno a DARK-1 están probados físicamente. **NO** se ha probado todavía una restauración completa después de un fallo, ni se ha verificado una restauración autónoma desde la tablet Android 16. No atribuir al bootloader una garantía absoluta frente a daños del propio sector de arranque, problemas eléctricos o imágenes incorrectas.

Antes de cargar un diagnóstico en RAM sigue siendo obligatorio identificar el estado del cargador residente y comprobar las reservas RAM: instalar m-bootloader no demuestra que la dirección de carga esté libre.

Fuentes de contraste de mapa: https://sbegueria.github.io/fm1-dev-wiki/Flash-Storage.html y https://sbegueria.github.io/fm1-dev-wiki/Recovery.html

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
