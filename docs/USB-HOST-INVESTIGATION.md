# Investigación USB Host y diagnóstico en RAM

## Resultado

**Prototipo de RAM compilado y verificado en software.** La ROM USB con M-Flasher de escritorio es una ruta de referencia pendiente de validar con este diagnóstico. M-Unbricker probó físicamente el enlace con otro sintetizador en ROM, alimentado por su propia batería. No hay evidencia en estas fuentes de que la placa FM-1 alimente correctamente un lector microSD pasivo. Tampoco se ha medido la unidad del usuario.

## Referencias examinadas

| Proyecto y versión examinada | Resultado |
|---|---|
| [m-bootloader `121cfbe`](https://github.com/jvitkauskas/m-bootloader/tree/121cfbea1ef6a66df8c804ee45c1338bc16b0a53) | La sección 11.8 define APP1, modelo FM-1 y formato 1. Las secciones 11.6 y 11.7 distinguen un cargador MIDI ya residente de la etapa OTA que se almacena en Flash antes de correr desde RAM. |
| [M-Unbricker `bd5ea96`](https://github.com/jvitkauskas/m-unbricker/tree/bd5ea96ca2fb925e1647d233373bac7e3881a098) | Controlador USB0 full speed en modo host con sondeo y código de LCD y teclas en una aplicación de RAM. Su lector objetivo original era el dispositivo de descarga JieLi. Se reutilizaron el host y el soporte de placa, y se implementó un flujo independiente USB MSC/SCSI de solo lectura. |
| [M-Flasher `8fbece7`](https://github.com/jvitkauskas/m-flasher/tree/8fbece7348d758ac284325eed455781675ac95ff) | Su comando de escritorio `run-ram` carga y arranca código en la ROM USB. La variante web trabaja con MIDI y necesita código ya residente o una etapa almacenada por el firmware. |
| [pi32v2-llvm 23.1.5-2](https://github.com/jvitkauskas/pi32v2-llvm/releases/tag/pi32v2-23.1.5-2) | Clang/LLD y herramientas ELF para pi32v2. Se verificó el SHA-256 del archivo Linux x64 publicado. |
| [Android FM-1 Flasher `bfede50`](https://github.com/ekim-pl/android-fm1-flasher/tree/bfede50cfc9d7a848809980f5b300a465a04c364) | Puede abrir la ROM por Android USB Host y carga sus auxiliares internos MFLR A/B de hash fijo. No ofrece una acción para elegir y ejecutar una aplicación APP1 arbitraria. |
| [DARK-1 `6c0cc34`](https://github.com/sparky480v/Dark-1/tree/6c0cc34677c4188575158c9ea5f53c95200be4a0) | En `firmware/src/ota.c`, la ruta M-UPGRADE borra cinco sectores y programa un cargador en `[0xE0000,0xE5000)`. La entrada UBOOT revisada escribe una marca en RAM retenida y reinicia. Se comprobó el código fuente, no la revisión exacta instalada en la unidad. |
| [JieLi AC79NN SDK `d179b44`](https://gitee.com/Jieli-Tech/fw-AC79_AIoT_SDK/tree/AC79NN_SDK_V1.2.1_2023-12-13) | Las capas `apps/common/usb/host` corroboran que la familia tiene modo USB Host. Los archivos de configuración de otra placa no demuestran la circuitería de la placa FM-1. |

## VBUS y rol USB-C

El USB0 incorpora PHY y controlador host. El bit VBUS de `CON0` que activa el controlador se interpreta como validación interna de sesión. Su presencia en el código no establece que existan una salida de 5 V, un conmutador, protección de corriente o resistencias CC para negociar rol de fuente en la placa. M-Unbricker sirve como evidencia sobre el enlace de datos, con dos equipos alimentados por separado. [Notas de hardware USB](https://github.com/jvitkauskas/m-unbricker/blob/bd5ea96ca2fb925e1647d233373bac7e3881a098/docs/husb-notes.md)

El prototipo deja el PHY desactivado mientras la alimentación figure como pendiente. Este estado refleja una condición externa sin comprobar. Para evaluar la seguridad quedan por caracterizar la tensión VBUS bajo carga, la negociación CC y la protección contra retornos de corriente. No se ha caracterizado ninguno de estos puntos en un FM-1 físico.

## Alcance del diagnóstico

- Un dispositivo USB full speed conectado directamente, configuración inicial de hasta 512 bytes e interfaz alternativa 0. Se informa del VID, PID, clase y subclase de dispositivo e interfaz. Para lectura se acepta clase `08`, subclase `06` y protocolo `50`, endpoints bulk IN/OUT de igual tamaño máximo. No incluye hubs, USB-C PD, UASP ni alta velocidad.
- BOT usa CBW, transferencia de datos IN y CSW. Se comprueban firma, etiqueta, residuo y longitud. Una condición de atención puede causar un único reintento de TEST UNIT READY tras REQUEST SENSE. Las otras órdenes SCSI son READ CAPACITY(10) y READ(10). El único bulk OUT es un CBW, sin datos de escritura de sectores.
- FAT32 se abre como volumen directo o primera partición primaria FAT32 MBR de tipo `0B` o `0C`. `vfat` en Android no identifica por sí solo FAT32. Se validan BPB, tamaño, FAT activa, rangos de clúster y cadenas. La pantalla muestra nombres cortos 8.3 de hasta 40 archivos raíz y una vista de hasta 80 bytes. El API de vista previa permite hasta 1.024 bytes. No hay soporte de FAT16, exFAT, GPT, nombres largos ni lectura de subdirectorios.
- La búsqueda, las transferencias y el recorrido FAT tienen límites. Errores de conexión, descriptor, transferencias, SCSI y FAT se muestran en el LCD. REC apaga el host y devuelve los GPIO de teclas. El interruptor físico vuelve a arrancar DARK-1. No se incluyó código para programar Flash interna, modificar calibración o grabar sectores de la tarjeta.

## Mapa y validación del artefacto

| Elemento | Diseño |
|---|---|
| Arquitectura | JieLi pi32v2, CPU R3, ELF32 little-endian |
| Código y entrada | `0x01C30000`, entrada en el primer byte |
| Límite del binario plano | `0x4FD80` bytes, incluido tráiler APP1 |
| BSS | Desde `0x01C16800`, sin bytes en el archivo, antes de `0x01C1E000` |
| Pilas | `0x01C1F800` y `0x01C20000` |
| Formato de RAM | Plano APP1, CRC16-XMODEM, placa 1, formato 1 |
| Tamaño verificado | 8.956 bytes incluyendo el tráiler, BSS 2.496 bytes |
| Integridad | `SHA256SUMS.txt`, verificación del CRC y comparación exacta de los bytes con el segmento ELF |

El enlazador descarta ubicaciones de Flash y produce dos segmentos cargables estrictamente en RAM. El código recibe el argumento de entrada de ROM y deja el puerto pasivo hasta el apagado físico. Se conserva el mapa de la aplicación de referencia y se valida el supuesto de reloj ROM de 48 MHz solo en software. El diagnóstico confirma que el contador avanza antes de activar periféricos. La frecuencia del temporizador, el rol CC y la respuesta del LCD siguen pendientes en placa.

## Pruebas y validación pendiente

`make test` simula control y bulk a nivel de transporte. Pasaron identificación, descriptor inválido, interfaz incompatible, tamaño de bloque, timeout, etiqueta CSW incorrecta, recuperación BOT, FAT32 con MBR y volumen directo, límites y lectura de un archivo fragmentado. El simulador verificó 27 CBW de solo lectura y ningún payload bulk OUT de datos. Las mismas pruebas pasaron con AddressSanitizer y UndefinedBehaviorSanitizer. No se simuló el controlador MMIO.

La validación en una unidad real debe cubrir la carga exclusiva en RAM, el rol USB-C y VBUS bajo carga, la enumeración y lectura en un lector real, el LCD, las teclas y la recuperación ante desconexión. Siguen pendientes el retorno a DARK-1 y la comprobación de integridad de la Flash respecto a la copia verificada. Estos son criterios de aceptación, con la ejecución física aplazada hasta la revisión de seguridad.
