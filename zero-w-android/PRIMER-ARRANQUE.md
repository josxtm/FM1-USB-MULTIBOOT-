# Primer arranque de la edición de diagnóstico

1. Comprueba el SHA-256 del archivo FM1-Zero-W.img.xz con la suma que lo acompaña. Selecciona la imagen personalizada en Raspberry Pi Imager y una microSD dedicada de 32 GB con capacidad real de al menos 31.500.000.256 bytes. La grabación reemplaza el contenido anterior.
2. En la partición FM1DATA, dentro de FM1, crea WIFI.TXT con una contraseña propia de 8 a 63 caracteres ASCII en una sola línea. La Zero la usa para configurar la red en el primer arranque. Conserva esa contraseña para el móvil. Evita compartir copias de la tarjeta ya personalizada.
3. En Android, instala FM1 Manager · Zero W, elige Preparar tarjeta SD y selecciona la carpeta FM1 de FM1DATA. Descarga y copia la selección. Expulsa la tarjeta e introdúcela en la Zero apagada.
4. Enciende la Zero y conecta el móvil a la red FM1-Zero. Abre http://10.42.0.1:8080. La página muestra el catálogo local y su SHA-256.
5. Conecta el FM-1 al puerto USB OTG. En el menú del sintetizador selecciona DOWNLOAD MODE y pulsa PLAY. Actualiza la página. WL82 UBOOT1.00 indica que Linux ha identificado el dispositivo de programación. Esta edición solo muestra información y no dispone de escritura ni de apagado desde la web.

El arranque, la visibilidad de FM1DATA desde Android y la enumeración del sintetizador requieren validación física en la Zero W original.
