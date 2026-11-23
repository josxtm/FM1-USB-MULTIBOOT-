# FM-1 portátil con Raspberry Pi Zero W y Android

Guion de desarrollo · Software primero, montaje y validación física después.

Desde el navegador del móvil se abre una página web alojada en la Raspberry Pi Zero W, conectándose a su red Wi-Fi local. Los firmwares musicales se conservan en la microSD de la Pi. El móvil prepara la tarjeta con FM1 Manager To USB y después ordena la instalación desde el navegador. El FM-1 ejecuta la aplicación elegida desde su flash. Se conserva el bootloader existente, también cuando se parte de una zona de aplicación vacía.

**Flujo principal con una sola microSD.** Preparar una vez una microSD arrancable con Raspberry Pi OS Lite (Trixie, 32 bits) y el servicio. Reservar una partición FAT32 de intercambio identificable como FM1DATA, con la carpeta FM1/FIRMWARES/. El diseño de imagen deberá asignarle espacio propio y ensayarse en la Zero W y en el lector del móvil. Grabar una imagen borra el contenido anterior: usar una tarjeta dedicada o respaldada. Si Android ofrece formatear una partición Linux, cancelar.

1. Con internet, abrir la APK existente FM1 Manager To USB. Adaptar su selector de carpeta y menús para guardar los paquetes musicales completos en FM1DATA/FM1/FIRMWARES/. Mantener la adaptación en esta línea de desarrollo. Verificar lectura y escritura desde Android con la tarjeta ya preparada, incluida la visibilidad de la partición de intercambio.
2. Expulsar la tarjeta en Android, colocarla en la Zero apagada y encenderla. Al arrancar, montar la partición por identificador, descubrir los paquetes y validar contenido y compatibilidad antes de ofrecer su instalación. Los archivos incompletos quedan fuera del catálogo instalable.
3. Conectar el móvil a la Wi-Fi de la Zero y abrir su web. Elegir un paquete que ya figura en la tarjeta. La subida por web queda como vía opcional.
4. Con el FM-1 conectado por USB, seleccionar DOWNLOAD MODE con OCT−/+ y confirmar con PLAY. Ordenar la instalación desde la web. Tras verificar la escritura, comprobar el reinicio y el arranque de la aplicación musical. MIDI MODE del bootloader sirve para recuperación o selección manual. Distinguir ese modo del USB-MIDI que ofrezca la aplicación musical.
5. Antes de volver a sacar la tarjeta, apagar la Zero de forma ordenada.

### Capacidad de la tarjeta

Elegimos Raspberry Pi OS Lite (Trixie, 32 bits) por las pruebas físicas publicadas por FM-1 Companion con Raspberry Pi Zero W y FM-1. El autor documenta copia, restauración verificada y actualización de SLOOP 2.7.1 a 2.8. La instalación por DOWNLOAD MODE con nuestro cargador y paquetes requiere pruebas propias. La imagen base oficial ocupa 2.656 MB una vez descomprimida. Evaluar 4 GB como capacidad candidata y usar 8 GB para el primer montaje, con espacio para las dependencias, los firmwares, las copias y la partición FAT32 FM1DATA. Publicar el mínimo tras medir la imagen completa y probar el ciclo móvil–Zero–FM-1. [Pruebas físicas de FM-1 Companion](https://github.com/xoxoaudiolabs/FM-1-Companion) · [Descargas oficiales](https://www.raspberrypi.com/software/operating-systems/)

### Preparación de la microSD y botón «Preparar tarjeta SD»

La imagen prevista para el proyecto incluirá Raspberry Pi OS Lite (Trixie, 32 bits), sus particiones de arranque y sistema, y un volumen FAT32 de intercambio llamado FM1DATA. Definir ese reparto al generar la imagen, limitar la expansión del sistema al espacio asignado y probar el primer arranque. Grabar la imagen una sola vez en una tarjeta dedicada o previamente respaldada. La preparación inicial reemplaza el contenido de la tarjeta. Evitar una secuencia que dependa de reducir a mano el sistema después de arrancar. La imagen propuesta está pendiente de construir y validar.

La imagen creará una carpeta FM1 en FM1DATA y dejará dentro un identificador de formato para reconocer la ubicación correcta. En FM1 Manager To USB, el botón «Preparar tarjeta SD» abrirá el selector de carpetas de Android. El usuario elegirá FM1 y concederá acceso a esa carpeta. La APK comprobará el identificador y la escritura disponible, creará FIRMWARES si falta y guardará el permiso para futuras descargas. Si el identificador falta, se eligió otra carpeta, Android no muestra el volumen o falta permiso, explicar el motivo antes de escribir. La APK solo gestionará archivos dentro de la carpeta concedida; no requiere acceso al disco completo ni operaciones de particionado.

Al repetir la preparación, conservar los paquetes y archivos existentes. Verificar con la tarjeta definitiva en el Nothing Phone y el lector: seleccionar FM1, preparar dos veces, guardar un paquete, expulsar, arrancar la Zero y comparar el hash del paquete. Registrar que el sistema arranca y que los archivos de prueba ajenos a la carpeta permanecen intactos. La FAT de arranque cumple una función distinta de FM1DATA. [Almacenamiento compartido en Android](https://developer.android.com/training/data-storage/shared/documents-files) · [Configuración de Raspberry Pi OS](https://www.raspberrypi.com/documentation/computers/configuration.html)

**Organización.** Rama de desarrollo `zero-w-android`, creada desde el commit inicial del repositorio. Todo el trabajo nuevo vivirá bajo `zero-w-android/`: `web/` para la página que sirve la Zero, `zero/` para el servicio e instalador, `contrato/` para la API y sus ejemplos de prueba. El instalador de la Zero incluirá la web y sus recursos para poder usarla sin internet. Los trabajos de CI futuros se limitarán a esta rama y sus rutas. El catálogo, las claves de emparejamiento, volcados de dispositivos y paquetes musicales del usuario permanecerán en sus equipos.

## Parte 1. Móvil · APK para preparar la tarjeta y web para controlar la Zero

**Resultado.** Una página web en castellano que funcione desde el móvil en una red local sin internet. Primero se desarrollará con respuestas simuladas, incluso sin disponer de la Zero o del sintetizador.

1. **Conexión.** Conectar el móvil a la Wi-Fi de la Zero y abrir su dirección local en el navegador. Distinguir en la interfaz «Zero conectada», «FM-1 detectado» y «modo de programación disponible». Mostrar por separado un fallo de Wi-Fi y una desconexión del cable USB. Comprobar el uso de la red local cuando Android conserve los datos móviles activos.
2. **Biblioteca.** Listar primero los firmwares descubiertos en la partición de intercambio. Como vía opcional, seleccionar un archivo `.fwsc` con el selector de Android, subirlo y mostrar el catálogo que devuelve la Pi. Mostrar nombre, versión si se conoce, tamaño y estado de compatibilidad. Una subida parcial nunca aparecerá como instalable. Se podrá preparar el catálogo con el FM-1 desconectado.
3. **Instalación.** Elegir paquete y solicitar un plan a la Zero. Mostrar dispositivo, firmware y modo requerido. Habilitar la confirmación cuando el plan sea válido. Si es necesario, explicar cómo seleccionar DOWNLOAD MODE con OCT−/+ y confirmar con PLAY en el FM-1. Un toque duplicado o una petición repetida debe identificar el mismo trabajo.
4. **Seguimiento.** Mostrar preparación, escritura, verificación, resultado y acción pendiente. El porcentaje procederá de los bloques procesados. Tras cerrar la pestaña o perder Wi-Fi, recuperar el trabajo por su identificador. Diferenciar «grabación verificada» de «arranque confirmado». Permitir apagar ordenadamente la Zero cuando esté inactiva.
5. **Entrega y aceptación.** Interfaz web adaptable al móvil, instrucciones de conexión y pruebas con servidor simulado. Demostrar la secuencia subir → elegir → confirmar → desconectar móvil → reconectar → recuperar resultado. Incluir archivo dañado, paquete incompatible y USB ausente.

## Parte 2. Software de la Raspberry Pi Zero W

**Resultado.** Un servidor web local autoiniciable sobre Raspberry Pi OS Lite (Trixie, 32 bits) para la Zero W original. Conservar el paquete local y dirigir toda la grabación desde la Pi permite que el móvil se desconecte durante la operación.

| Función | Implementación y comprobación |
|---|---|
| Arranque y red | Servicio iniciado al arrancar Linux y punto de acceso Wi-Fi con contraseña. Preparar las dependencias en casa. La operación posterior funciona sin cobertura, router ni nube. |
| API local | Implementar para la web las operaciones de estado, catálogo, subida, planificación, confirmación y seguimiento. Autenticar la sesión del navegador. Vincular cada confirmación al hash del paquete, al plan y a la sesión del dispositivo. |
| Almacenamiento | Recibir en temporal, comprobar espacio, formato, modelo, tamaño e integridad, calcular SHA-256 y publicar mediante renombrado atómico. Persistir catálogo y registro del trabajo. Un hash identifica el fichero, mientras que la compatibilidad se verifica aparte. |
| USB y modos | En MIDI MODE, comprobar detección y lecturas admitidas. En DOWNLOAD MODE, identificar la unidad y evaluar la vía Linux de programación por sectores. El perfil inicial previsto es FM-1 con m-bootloader 2.4-395E6CC y flash 856014 de 1 MiB. Confirmar el perfil mediante lecturas reales. |
| Escritura limitada | Comprobar qué regiones puede cambiar cada combinación equipo/paquete. Validar el plan completo antes del primer borrado, proteger cargador, configuración y sectores reservados, grabar los sectores de aplicación necesarios y verificar por relectura. Bloquear las instalaciones que exijan cambiar el bootloader, incluso si son oficiales. La interfaz solo ofrecerá operaciones de aplicación. |
| Fallos | Ejecutar un solo trabajo de escritura por dispositivo. Una pérdida de Wi-Fi permite continuar en la Pi. Ante un corte USB o de alimentación, registrar estado interrumpido, identificar otra vez la unidad y comprobar qué se grabó antes de presentar un nuevo plan. |

**Secuencia de desarrollo.**

1. Acordar la API con ejemplos compartidos y conectar la página web a una Zero simulada.
2. Implementar catálogo y servicio con una flash simulada. Probar aplicaciones vacías y existentes, paquetes fuera de rango, desconexiones y doble confirmación. Comprobar byte a byte que los sectores protegidos se conservan.
3. Integrar web y servicio en un único instalador para la Zero y documentar el arranque offline. Esta entrega cierra el desarrollo inicial en simulación.
4. Pasar al anexo físico. Primero identificar en ambos modos y hacer dos lecturas concordantes de respaldo. Conservar una copia fuera de la Pi. Probar el estado vacío en simulación conserva la aplicación real durante esta fase.
5. Con una combinación acreditada de paquete y unidad, presentar el plan para la primera instalación física. Verificar sectores y cargador, volver al menú y probar BOOT APP y reproducción de una nota. Hasta completar esta prueba, etiquetar el funcionamiento físico como pendiente.

**Referencias de implementación.** FM-1 Flip aporta el flujo de selección, validación y relectura visto en el vídeo. El proyecto Linux de fuleo documenta una actualización MIDI de V14 a V15. MvaveFM1Unbricker aporta experiencia de identificación y respaldo en Linux/Raspberry Pi, pero su restauración parcial tiene límites y la restauración completa puede escribir el cargador. Revisar los efectos concretos y las licencias antes de reutilizar código. [Vídeo original](https://youtu.be/n6WhMKjcWSU) · [FM-1 Flip](https://github.com/chrisdiana/fm1-flip) · [fm1-linux-update](https://github.com/fuleo/fm1-linux-update) · [Herramienta Linux de respaldo](https://github.com/Quixotic7/MvaveFM1Unbricker) · [m-bootloader](https://github.com/jvitkauskas/m-bootloader)

## Anexo. Hardware para la fase posterior

| Equipo | Función |
|---|---|
| Móvil Android | Abrir la web local en el navegador y enviar órdenes por Wi-Fi. |
| Raspberry Pi Zero W v1.1 y carcasa sencilla | Alojar el programador. Dejar accesibles microSD, USB y PWR IN. |
| MicroSD y lector para prepararla | Instalar el sistema, servicio, paquetes y registros. |
| Powerbank de 5 V con salida de 2 A y cable micro-USB | Dar alimentación a la Zero por PWR IN. |
| Adaptador micro-USB OTG a USB-A hembra y cable USB-A a USB-C de datos | Conectar el puerto USB de datos de la Zero al FM-1. |
| M-VAVE FM-1 con batería cargada | Seleccionar el modo del bootloader y arrancar el firmware instalado. |

**Montaje.** Powerbank a PWR IN. Puerto USB de la Zero al USB-C del sintetizador. Móvil a la Wi-Fi de la Zero. La carcasa aloja únicamente la placa. [Ficha de la Zero W](https://www.raspberrypi.com/products/raspberry-pi-zero-w/) · [Raspberry Pi OS Lite](https://www.raspberrypi.com/software/operating-systems/)
