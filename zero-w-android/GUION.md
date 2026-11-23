# FM-1 portátil con Raspberry Pi Zero W y Android

Guion de desarrollo · Software primero, montaje y validación física después.

La APK controla por Wi-Fi local un programador instalado en la Raspberry Pi Zero W. Los firmwares musicales se conservan en la microSD de la Pi. El móvil puede subirlos por adelantado y ordenar después su instalación por USB. El FM-1 ejecuta la aplicación elegida desde su flash. Se conserva el bootloader existente, también cuando se parte de una zona de aplicación vacía.

**Organización.** Rama de desarrollo `zero-w-android`, creada desde el commit inicial del repositorio. Todo el trabajo nuevo vivirá bajo `zero-w-android/`: `android/` para la APK, `zero/` para el servicio e instalador, `contrato/` para la API y sus ejemplos de prueba. Cada componente tendrá compilación y versiones propias. La APK tendrá un identificador Android propio para convivir con las APK del proyecto MultiOS. Los trabajos de CI futuros se limitarán a esta rama y sus rutas. El catálogo, las claves de emparejamiento, volcados de dispositivos y paquetes musicales del usuario permanecerán en sus equipos.

## Parte 1. Software del móvil Android

**Resultado.** Una APK en castellano que funcione desde el móvil en una red local sin internet. Primero se desarrollará con respuestas simuladas, incluso sin disponer de la Zero o del sintetizador.

1. **Conexión.** Emparejar el móvil con la Zero. Distinguir en la interfaz «Zero conectada», «FM-1 detectado» y «modo de programación disponible». Mostrar por separado un fallo de Wi-Fi y una desconexión del cable USB. Comprobar el uso de la red local cuando Android conserve los datos móviles activos.
2. **Biblioteca.** Seleccionar un archivo `.fwsc` con el selector de Android, subirlo y mostrar el catálogo que devuelve la Pi. Mostrar nombre, versión si se conoce, tamaño y estado de compatibilidad. Una subida parcial nunca aparecerá como instalable. Se podrá preparar el catálogo con el FM-1 desconectado.
3. **Instalación.** Elegir paquete y solicitar un plan a la Zero. Mostrar dispositivo, firmware y modo requerido. Habilitar la confirmación cuando el plan sea válido. Si es necesario, explicar cómo seleccionar DOWNLOAD MODE con OCT−/+ y confirmar con PLAY en el FM-1. Un toque duplicado o una petición repetida debe identificar el mismo trabajo.
4. **Seguimiento.** Mostrar preparación, escritura, verificación, resultado y acción pendiente. El porcentaje procederá de los bloques procesados. Tras cerrar la app o perder Wi-Fi, recuperar el trabajo por su identificador. Diferenciar «grabación verificada» de «arranque confirmado». Permitir apagar ordenadamente la Zero cuando esté inactiva.
5. **Entrega y aceptación.** APK instalable, instrucciones de conexión y pruebas con servidor simulado. Demostrar la secuencia subir → elegir → confirmar → desconectar móvil → reconectar → recuperar resultado. Incluir archivo dañado, paquete incompatible y USB ausente.

## Parte 2. Software de la Raspberry Pi Zero W

**Resultado.** Un servicio autoiniciable sobre Raspberry Pi OS Lite de 32 bits para la Zero W original. Conservar el paquete local y dirigir toda la grabación desde la Pi permite que el móvil se desconecte durante la operación.

| Función | Implementación y comprobación |
|---|---|
| Arranque y red | Instalador para Raspberry Pi OS Lite, servicio systemd y punto de acceso Wi-Fi con contraseña. Preparar las dependencias en casa. La operación posterior funciona sin cobertura, router ni nube. |
| API local | Definir con Android las operaciones de estado, catálogo, subida, planificación, confirmación y seguimiento. Autenticar el móvil emparejado. Vincular cada confirmación al hash del paquete, al plan y a la sesión del dispositivo. |
| Almacenamiento | Recibir en temporal, comprobar espacio, formato, modelo, tamaño e integridad, calcular SHA-256 y publicar mediante renombrado atómico. Persistir catálogo y registro del trabajo. Un hash identifica el fichero, mientras que la compatibilidad se verifica aparte. |
| USB y modos | En MIDI MODE, comprobar detección y lecturas admitidas. En DOWNLOAD MODE, identificar la unidad y evaluar la vía Linux de programación por sectores. El perfil inicial previsto es FM-1 con m-bootloader 2.4-395E6CC y flash 856014 de 1 MiB. Confirmar el perfil mediante lecturas reales. |
| Escritura limitada | Comprobar qué regiones puede cambiar cada combinación equipo/paquete. Validar el plan completo antes del primer borrado, proteger cargador, configuración y sectores reservados, grabar los sectores de aplicación necesarios y verificar por relectura. Bloquear las instalaciones que exijan cambiar el bootloader, incluso si son oficiales. La interfaz solo ofrecerá operaciones de aplicación. |
| Fallos | Ejecutar un solo trabajo de escritura por dispositivo. Una pérdida de Wi-Fi permite continuar en la Pi. Ante un corte USB o de alimentación, registrar estado interrumpido, identificar otra vez la unidad y comprobar qué se grabó antes de presentar un nuevo plan. |

**Secuencia de desarrollo.**

1. Acordar la API con ejemplos compartidos y conectar la APK a una Zero simulada.
2. Implementar catálogo y servicio con una flash simulada. Probar aplicaciones vacías y existentes, paquetes fuera de rango, desconexiones y doble confirmación. Comprobar byte a byte que los sectores protegidos se conservan.
3. Integrar APK y servicio, generar APK e instalador y documentar el arranque offline. Esta entrega cierra el desarrollo inicial en simulación.
4. Pasar al anexo físico. Primero identificar en ambos modos y hacer dos lecturas concordantes de respaldo. Conservar una copia fuera de la Pi. Probar el estado vacío en simulación conserva la aplicación real durante esta fase.
5. Con una combinación acreditada de paquete y unidad, presentar el plan para la primera instalación física. Verificar sectores y cargador, volver al menú y probar BOOT APP y reproducción de una nota. Hasta completar esta prueba, etiquetar el funcionamiento físico como pendiente.

**Referencias de implementación.** FM-1 Flip aporta el flujo de selección, validación y relectura visto en el vídeo. El proyecto Linux de fuleo documenta una actualización MIDI de V14 a V15. MvaveFM1Unbricker aporta experiencia de identificación y respaldo en Linux/Raspberry Pi, pero su restauración parcial tiene límites y la restauración completa puede escribir el cargador. Revisar los efectos concretos y las licencias antes de reutilizar código. [Vídeo original](https://youtu.be/n6WhMKjcWSU) · [FM-1 Flip](https://github.com/chrisdiana/fm1-flip) · [fm1-linux-update](https://github.com/fuleo/fm1-linux-update) · [Herramienta Linux de respaldo](https://github.com/Quixotic7/MvaveFM1Unbricker) · [m-bootloader](https://github.com/jvitkauskas/m-bootloader)

## Anexo. Hardware para la fase posterior

| Equipo | Función |
|---|---|
| Móvil Android | Ejecutar la APK y enviar órdenes por Wi-Fi. |
| Raspberry Pi Zero W v1.1 y carcasa sencilla | Alojar el programador. Dejar accesibles microSD, USB y PWR IN. |
| MicroSD y lector para prepararla | Instalar el sistema, servicio, paquetes y registros. |
| Powerbank de 5 V con salida de 2 A y cable micro-USB | Dar alimentación a la Zero por PWR IN. |
| Adaptador micro-USB OTG a USB-A hembra y cable USB-A a USB-C de datos | Conectar el puerto USB de datos de la Zero al FM-1. |
| M-VAVE FM-1 con batería cargada | Seleccionar el modo del bootloader y arrancar el firmware instalado. |

**Montaje.** Powerbank a PWR IN. Puerto USB de la Zero al USB-C del sintetizador. Móvil a la Wi-Fi de la Zero. La carcasa aloja únicamente la placa. [Ficha de la Zero W](https://www.raspberrypi.com/products/raspberry-pi-zero-w/) · [Sistema operativo](https://www.raspberrypi.com/software/operating-systems/)
