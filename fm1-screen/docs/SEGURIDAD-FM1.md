# Seguridad del instrumento

La app arranca en escucha pasiva y en solo visualización. Excluye puertos asociados a actualización, arranque, rescate o descarga. Los puertos de identidad desconocida se pueden abrir en escucha.

El envío se limita a solicitudes de pantalla documentadas y operaciones acotadas de panel fm1-base. El panel exige detección de la versión, duplicación activa y autorización local para salir de solo visualización. La cola espera ACK, limita reintentos y libera el panel al suspender o cerrar. El firmware fm1-base también expira los mandos al terminar la sesión. SAVE se reserva a la botonera física.

El APK carece de cargadores, binarios de firmware, permiso de Internet y operaciones de programación de Flash. UsbManager inspecciona metadatos. MidiManager conserva la propiedad de la interfaz. El diagnóstico recorta prefijos MIDI y limita el tamaño persistido.
