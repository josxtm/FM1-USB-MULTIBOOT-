# Protocolos gráficos

| Familia | Recepción y control | Evidencia |
|---|---|---|
| fm1-base | SysEx 8→7, longitud LE24, checksum, Hello, Rect con caché, Fill, Input y ACK. Recupera huecos mediante refresco. Puede recibir estados y enviar pulsación, detentes y MASTER. | [device/mirror.c](https://github.com/charlesvestal/fm1-base/blob/e4863fab25fcb5cf2be6d625992a017e1217d781/device/mirror.c) |
| Big Screen V1 | Solicitud FS y franjas de filas RGB565 RLE. La cobertura crece a medida que se reciben regiones. | [Big Screen](https://github.com/pfkellogg/fm1-mirror/tree/43cae0d2afe9db7b95a209be74fe390ad2539e22) |
| Big Screen V2 | HELLO, INFO, RECT y SYNC de BS. Caché de colores y runs. Publica en SYNC o tras la espera límite. | [Especificación](https://github.com/konsumer/foma/blob/885100f027356014b42a1d9c7d9902eb3ac41801/bigscreen/README.md) |
| Spyglass | Función de notas y acordes. Su visor comparte las familias anteriores. | [Receptor Android](https://github.com/pfkellogg/fm1-mirror-spyglass-android/tree/a5ee5336a192d3ff1c2b71b83692e07601b935b6) |
| RemoteFM1 USB Bulk | El flujo estudiado envía el LCD host → FM-1 y eventos de panel FM-1 → host. La recepción de LCD por Bulk queda en investigación. | [RemoteFM1](https://github.com/Quixotic7/RemoteFM1/tree/26338e52a28c0ceba400384bc1d160ea03570fae) |
| Skiff Studio Cast | Transmite eventos y temporización para reproducción por una copia WASM del firmware. Las variantes Big Screen usan píxeles publicados. | [Skiff cast.c](https://github.com/illuminus5/skiff/blob/392c1dfab4d8d2700e0d24f1ef61cc071d2529ab/firmware/src/cast.c) |

La conexión escucha primero. Una activación expresa habilita únicamente sondeos conocidos en el puerto reconocido. Auto prueba V2, V1 y fm1-base y selecciona la primera respuesta verificada. Los tiempos reales MIDI se pueden intercalar en SysEx. El máximo por mensaje es 4 KiB y los decodificadores comprueban límites antes de aplicar píxeles.

Diagnóstico informa bloques/s y para V2 también SYNC/s. Los FPS dibujados cuentan imágenes que Canvas llega a dibujar. El tiempo local mide desde el callback MIDI hasta publicar el framebuffer. La latencia de transporte y extremo a extremo requiere medición externa.
