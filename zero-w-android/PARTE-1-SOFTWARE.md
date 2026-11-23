# Parte 1 · Software del programador portátil FM-1

**Resultado esperado.** El móvil prepara una biblioteca de firmware en una microSD. Después, desde el navegador del móvil, el usuario elige un paquete y la Raspberry Pi Zero W lo instala en la flash del sintetizador. El sistema debe funcionar sin internet con los paquetes ya descargados, incluso si el FM-1 solo conserva el bootloader. Este guion especifica el desarrollo y sus pruebas.

## 1. Preparación inicial de la microSD

**Software:** Raspberry Pi Imager en un ordenador de preparación, o un grabador compatible con la imagen del proyecto. Se usa una vez por tarjeta. El equipo de preparación queda fuera del uso portátil.

**Función:** escribir una imagen del proyecto que incluya Raspberry Pi OS Lite Trixie de 32 bits, la configuración Wi-Fi, la web y el servicio de programación. Generar la imagen es un entregable del desarrollo. En la tarjeta de 32 GB se reservan 8 GiB para arranque y sistema. El espacio restante, unos 23,4 GB, forma la partición FAT32 `FM1DATA`. La imagen creará la carpeta `FM1` y un identificador que la APK pueda reconocer. Grabar la imagen sustituye el contenido previo de la tarjeta. [Raspberry Pi Imager](https://www.raspberrypi.com/software/)

## 2. Móvil Android · Preparar el catálogo en casa

**Software:** adaptar la APK existente **FM1 Manager To USB** en la rama de la Zero. Aprovechar su selector de carpeta y el flujo de descargas existente.

- **Preparar tarjeta SD.** El usuario selecciona `FM1` en el volumen `FM1DATA`. La APK comprueba el identificador, conserva el permiso de carpeta y crea `FIRMWARES` si falta. Repetir la acción conserva las descargas anteriores.
- **Descargar firmware.** Con internet, guardar los paquetes musicales completos `.fwsc` en `FM1DATA/FM1/FIRMWARES/`. Comprobar espacio, finalización e integridad de la descarga. Una interrupción deja un archivo temporal que el catálogo de la Zero ignora.
- **Resultado visible.** Mostrar el nombre del paquete, el destino y el resultado. Al terminar, el usuario expulsa la tarjeta desde Android y la inserta en la Zero apagada.

## 3. Móvil Android · Manejar el programador en la rave

**Software:** el navegador que ya tiene el teléfono. La página y sus recursos se sirven desde la Zero. Para abrirla, conectar el móvil al punto de acceso de la Zero e introducir su dirección local, que figurará en las instrucciones de la imagen.

| Pantalla de la web | Acción |
|---|---|
| Conexión | Mostrar por separado conexión con la Zero, presencia del FM-1 y modo USB detectado. |
| Biblioteca | Listar los paquetes encontrados en la tarjeta con nombre, versión conocida y compatibilidad comprobada o pendiente. |
| Instalar | Mostrar paquete y dispositivo, solicitar el plan a la Zero y confirmar una sola instalación. Indicar cuándo seleccionar DOWNLOAD MODE y pulsar PLAY. |
| Progreso y resultado | Recuperar el trabajo al volver a abrir la página, mostrar la fase real y diferenciar grabación verificada de arranque confirmado. Ofrecer apagado ordenado de la Zero cuando esté inactiva. |

## 4. Raspberry Pi Zero W · Servir la web y realizar la grabación

| Componente | Origen | Función |
|---|---|---|
| Raspberry Pi OS Lite Trixie, 32 bits | Sistema existente | Arrancar la Zero original, acceder a la tarjeta y gestionar Wi-Fi y USB. FM-1 Companion documenta pruebas con esta familia de sistema, Zero W y FM-1. |
| Punto de acceso y arranque automático | Configuración del sistema a preparar | Crear la Wi-Fi privada e iniciar el servicio local en cada encendido. Incluir de antemano las dependencias para trabajar sin cobertura. |
| Servidor web y catálogo | Software del proyecto | Servir la interfaz al navegador, montar FM1DATA por identificador, descubrir paquetes, validar formato y mantener el historial de trabajos. |
| Programador USB | Componente Linux a integrar y validar | Identificar equipo y chip, validar paquete y mapa de escritura, comparar y escribir solo sectores permitidos de la aplicación, releerlos y comprobar las regiones protegidas. Un paquete que requiera sustituir el cargador debe aparecer como incompatible. |
| Seguimiento y recuperación | Parte del servicio | Mantener un único trabajo activo, registrar su resultado y continuar si se pierde la Wi-Fi del móvil. Un corte USB o de alimentación requiere identificar y leer de nuevo el equipo antes de proponer otro intento. |

[Pruebas de FM-1 Companion](https://github.com/xoxoaudiolabs/FM-1-Companion) · [Referencia de programación por sectores](https://github.com/chrisdiana/fm1-flip) · [Herramientas Linux para el FM-1](https://github.com/Quixotic7/MvaveFM1Unbricker)

## 5. M-VAVE FM-1 · Recibir y arrancar el firmware musical

**Software residente:** m-bootloader `2.4-395E6CC` ya instalado. El usuario selecciona **DOWNLOAD MODE** con OCT−/+ y confirma con PLAY. La Zero envía por USB la aplicación musical elegida. Tras verificar la grabación, comprobar el arranque normal de la aplicación o elegir **BOOT APP** si se está en el menú. **MIDI MODE** está disponible para diagnóstico o recuperación. El bootloader y su configuración se preservan tanto con aplicación previa como con la zona de aplicación vacía. [Documentación del cargador](https://github.com/jvitkauskas/m-bootloader)

## 6. Orden de entrega y criterios de aceptación

1. **APK adaptada.** Preparar dos veces la misma carpeta y descargar dos paquetes. Verificar que las descargas anteriores y los archivos ajenos se conservan.
2. **Web y servicio con simulación.** Abrir el catálogo desde el navegador, elegir un paquete, simular una instalación y recuperar el resultado tras perder la Wi-Fi. Probar aplicación vacía, aplicación existente, paquete corrupto y paquete que afectaría al cargador.
3. **Imagen de la Zero.** Empaquetar el sistema y el servicio para la tarjeta de 32 GB. La prueba de integración consiste en arrancar sin internet, abrir la web y encontrar el mismo paquete con el mismo SHA-256 que preparó el móvil.
4. **Paso posterior al banco físico.** Primero identificar la unidad en lectura y obtener dos lecturas concordantes de respaldo. Después, con un paquete compatible y autorización para la prueba física, verificar escritura, integridad del cargador y arranque musical. El guion no presupone que esa instalación ya esté validada.
