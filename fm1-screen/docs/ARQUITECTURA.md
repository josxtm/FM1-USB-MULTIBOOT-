# Arquitectura Android

Proyecto independiente en Kotlin y Jetpack Compose. Compose permite reutilizar los componentes originales y sus gestos desde RemoteFM1Android. PanelFeed enlaza esas vistas con la recepción física.

| Componente | Responsabilidad |
|---|---|
| MidiTransport | Enumera USB Host, abre una sesión MIDI USB o BLE, detecta desconexión y reintenta. Android posee la interfaz MIDI. |
| MirrorSession | Sondeo documentado, selección de familia, temporización, recuperación y cola de control con ACK. |
| MirrorPackets | Ensamblado SysEx, identificación, validación y decodificación RGB565. |
| FrameBuffer | 57.600 píxeles y máscara de cobertura. Solo aplica regiones completas. |
| Faceplate | Panel adaptado, tres vistas y estados recibidos. |
| data | Perfiles y temas declarativos con preferencias privadas. |
| capture | MP4 del LCD con Android MediaCodec/MediaMuxer y exportación mediante selector de documentos. |

Un hilo serial procesa MIDI. El framebuffer publica copias inmutables al hilo de interfaz con limitación de frecuencia. Canvas presenta el LCD en relación 1:1 y ofrece filtros de escalado. La grabación dispone de su propio hilo.

La importación modifica metadatos y leyendas. La política de salida depende de identidad, protocolo y estado de sesión. El FM-1 mantiene la ejecución del firmware y la producción de audio.
