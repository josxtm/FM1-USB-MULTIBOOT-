# FM1 MULTIBOOT OS — Arquitectura v0.1

> Estado: propuesta de diseño, sin instalación física. El simulador no carga ningún firmware.

## Objetivo
Encendido del FM1 → Cover Flow en TFT 240 × 240 → OCT−/OCT+ → PLAY → verificar imagen → ejecutar por método compatible → sintetizador operativo. La tablet Android prepara la microSD; el FM1 debe funcionar autónomamente. DARK-1 no será dependencia del sistema propio.

## Evidencia actual y límites
- Chip JieLi AC791N/pi32v2, Flash Puya JEDEC 0x856014 de 1 MiB.
- PR #1: enumeración USB MSC y FAT32 comprobadas con lector simulado; aún sin ensayo físico.
- PR #2: Android RAM Loader; la identificación USB WL82 / UBOOT1.00 con INQUIRY y CSW se confirmó en el FM1 real. No acredita por sí sola una ROM limpia, pues un cargador residente podría responder igual.
- La ejecución real del diagnóstico en RAM no está autorizada ni validada.
- La microSD de 32 GB se monta como vfat en Android; vfat por sí solo no distingue FAT16 de FAT32.
- El lector USB-C no tiene alimentación auxiliar. Un arranque normal del FM1 con él conectado no demuestra que el lector tenga VBUS ni que se hayan leído datos.

## Capas separadas
1. Boot ROM inmutable del AC791N (identificar sus capacidades, sin alterarla).
2. Vía de recuperación verificada: respaldos exportados y restauración demostrada.
3. Gestor de arranque persistente FM1 MULTIBOOT OS, sin depender de DARK-1.
4. Controlador USB Host, MSC/BOT/SCSI y lector FAT32 de solo lectura.
5. Catálogo versionado e inspección de compatibilidad de imágenes.
6. Renderizador Cover Flow, aislado de USB y escritura de Flash.
7. Gestor de ejecución que elige RAM, Flash o bloquea imágenes incompatibles.
8. FM1 Manager Android para descargar/importar/verificar/exportar a microSD.

La interfaz no debe escribir Flash ni decidir por sí misma que una imagen es ejecutable.

## Modelo de estados
BOOT → DETECT_SD → LOAD_INDEX → BROWSE → INSPECT → VERIFY → LAUNCH.
Errores NO_CARD, NO_VBUS, USB_ERROR, FAT_ERROR, BAD_IMAGE, INCOMPATIBLE y RECOVERY deben resolverse sin bucles de arranque ni escritura automática.

En este commit se implementa SOLO BROWSE e INSPECT en un simulador web con catálogo ficticio.

## Cover Flow 240 × 240
- Tres portadas visibles, foco central, laterales reducidos y perspectiva simulada.
- Estética oscura industrial con acento turquesa.
- Control real objetivo: OCT− anterior; OCT+ siguiente; PLAY seleccionar; REC cancelar.
- En Android también teclas de dirección, Intro, Escape y deslizamiento.
- Preconvertir carátulas desde Android a sprites RGB565 u otro formato eficiente validado.
- No asumir que cabe un framebuffer completo RGB565: 240 × 240 × 2 = 115.200 bytes.
- Medir RAM, tiempos de dibujo y retardo de los controles en dispositivo antes de definir animaciones definitivas.
- Ofrecer render estático si la plataforma no sostiene animaciones fluidas.

## USB-C y microSD externa
El soporte USB Host del silicio NO demuestra que la placa FM1 suministre 5 V VBUS ni negocie correctamente el rol fuente USB-C.

Primero comprobar pistas, protección eléctrica, resistencias CC y VBUS. Después, si la medición es segura, enumerar un lector físico clase 08/06/50 y leer capacidad, MBR, FAT32 y ficheros. No conectar alimentación externa a la entrada USB-C del FM1 sin validar la topología eléctrica y protección contra retorno de corriente.

Soporte inicial deseado: un único lector BOT USB full-speed, FAT32 en MBR, sector 512 bytes, nombres cortos 8.3, solo lectura. No asumir soporte exFAT, GPT, hubs o USB PD.

## Catálogo de microSD propuesto
Directorio raíz FM1, con FW, COVER e INDEX.CSV. Ejemplo conceptual:

    FM1/
      FW/
        DARK1.BIN
        FACTORY.BIN
      COVER/
        DARK1.RGB
        FACTORY.RGB
      INDEX.CSV

Ejemplos, NO imágenes de firmware verificadas ni autorizadas para instalar.

Cada entrada contendrá ID ASCII estable, nombre, versión, autor y fuente, fichero, tamaño, hash SHA-256, compatibilidad de placa y Flash, método de ejecución (UNKNOWN/RAM/FLASH), carátula y versión del formato de catálogo. Limitar longitudes, entradas y rutas; prohibir rutas absolutas o ascenso entre directorios.

## Ejecución de firmware: restricciones
- Directamente desde microSD: no demostrado para firmwares FM1 convencionales.
- Desde RAM: solo para programas preparados y relocatables que quepan y respeten el mapa de memoria.
- Desde Flash: imágenes convencionales pueden requerir XIP y direcciones fijas; cualquier escritura necesita análisis de colisiones, protección de arranque, desgaste, corte de alimentación y recuperación.

No asumir que un gestor persistente puede convivir en Flash con cualquier firmware de terceros. Evaluar cada imagen con una matriz de compatibilidad antes de habilitar PLAY real.

## Seguridad y criterios de aceptación
Antes de escritura física:
- Exportar el respaldo íntegro fuera de FM-1 Flasher y verificar hashes.
- Validar procedimiento de restauración desde Android y áreas protegidas.
- Verificar que la ventana RAM no está ocupada por cargador residente.
- Medir condiciones VBUS y rol USB-C, sin backfeeding.
- Probar primero UI con catálogo simulado; después en RAM y, solo finalmente, persistente.
- No fusionar PR #1, PR #2 ni esta rama hasta validar.

## Secuencia futura
A. Simulador y especificación (esta rama).
B. Android Manager: catálogo y exportación a microSD sin flasheo.
C. Carga RAM segura y diagnóstico del lector físico.
D. Portar render y controles al FM1.
E. Diseñar recuperación y compatibilidad multiboot por firmware.
F. Instalar sistema propio solo tras validación eléctrica, funcional y recuperación.