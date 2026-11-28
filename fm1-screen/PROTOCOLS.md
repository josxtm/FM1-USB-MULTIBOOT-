# Protocolos y direcciones de comunicación

| Familia o proyecto | Dirección verificada en código | Implementación |
|---|---|---|
| fm1-base v1/v2 | FM-1 → visor por SysEx para Hello, Rect, Fill y estados cuando están habilitados. Visor → FM-1 para consulta, refresco y control compatible. ACK y recuperación por secuencia. | Receptor con checksum, empaquetado de 8 a 7 bits, parches y control v2 autorizado. [Código MPL-2.0](https://github.com/charlesvestal/fm1-base/blob/e4863fab25fcb5cf2be6d625992a017e1217d781/device/mirror.c) |
| Big Screen V1 | Visor → consulta FS. FM-1 → franjas RGB565 con RLE. | Receptor con cobertura progresiva. [Visor GPL-3.0 y emisor MIT](https://github.com/pfkellogg/fm1-mirror/tree/43cae0d2afe9db7b95a209be74fe390ad2539e22) |
| Big Screen V2 | Visor → consulta BS. FM-1 → INFO, RECT y SYNC. | Receptor con caché de colores, límites de región y publicación en SYNC o plazo de espera. [Especificación MIT](https://github.com/konsumer/foma/blob/885100f027356014b42a1d9c7d9902eb3ac41801/bigscreen/README.md) |
| Spyglass | El visor emplea las familias anteriores y añade funciones de notas. | Las familias de imagen están implementadas. [Receptor GPL-3.0](https://github.com/pfkellogg/fm1-mirror-spyglass-android/tree/a5ee5336a192d3ff1c2b71b83692e07601b935b6) |
| RemoteFM1 USB Bulk | Pantalla host → FM-1 y eventos de panel FM-1 → host en la implementación examinada. | Pendiente una fuente que documente LCD FM-1 → host por Bulk. [Fuentes GPL-3.0 y licencias por componente](https://github.com/Quixotic7/RemoteFM1/tree/26338e52a28c0ceba400384bc1d160ea03570fae) |
| SKIFF Cast | FM-1 → navegador con eventos y tiempos para una instancia WASM. | Pendiente una fuente LCD en SKIFF estándar. La variante Big Screen emite píxeles. [Fuente GPL-3.0](https://github.com/illuminus5/skiff/blob/392c1dfab4d8d2700e0d24f1ef61cc071d2529ab/firmware/src/cast.c) |
| FM1 Open | USB MIDI y pantalla local en el firmware base. V14 con Big Screen añade emisión. | Sigue la familia Big Screen V1 en dicha variante. [Firmware y licencia](https://github.com/m-vave/fm1_open/tree/4c7496daac04a95ff10d1afdcc8f9ead2dbabc41) · [Variante](https://github.com/m-vave/fm1_open/pull/24) |

Android abre USB MIDI y, de forma excluyente, BLE MIDI mediante `MidiManager`. `UsbManager` aporta instrumentación de dispositivos e interfaces. Una vez elegida la conexión, la sesión empieza escuchando. Los sondeos se habilitan solo con **Solicitar duplicación** y una identidad conocida. El descubrimiento automático reconoce la familia por una respuesta válida. Los mensajes SysEx quedan limitados a 4 KiB. El núcleo reensambla fragmentos, admite MIDI en tiempo real intercalado y valida longitud, checksum y límites de LCD antes de aplicar datos.

El catálogo comunitario permite localizar proyectos y sus ediciones. El soporte de cada edición requiere examinar su emisor. [The Firmware Hub](https://thefirmwarehub.com/) · [Atribuciones y revisiones](docs/LICENCIAS-Y-ATRIBUCIONES.md).

La política USB de la compilación 017 reconoce el descriptor Felucca cuando coincide con `1209:0001` y limita el sondeo a Big Screen V1 e identificación de solo lectura. [Mensajes, caducidad y diagnóstico de Atlatl](docs/FASE-3-COMUNICACIONES.md).
