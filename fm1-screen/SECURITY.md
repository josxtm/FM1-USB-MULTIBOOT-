# Seguridad del FM-1

La app abre en **Solo visualización**. El transporte permite un único puerto activo. La recepción pasiva admite mensajes sin emitir solicitudes. **Solicitar duplicación** habilita comandos de lectura documentados hacia identidades reconocidas. Los modos de carga o actualización se excluyen de la conexión activa.

La emisión de gestos requiere identificación de fm1-base v2, consentimiento del usuario en la sesión y conexión vigente. El encoder y el modelo de sesión bloquean la pulsación SAVE. El envío se serializa, espera ACK, tiene cola acotada y reintentos limitados con la misma secuencia. Un rechazo o timeout del panel devuelve la app a **Solo visualización**. Se intenta liberar el panel al cerrar una sesión compatible. Si se interrumpe físicamente el cable, la aplicación pierde la capacidad de entregar ese mensaje al instrumento.

El protocolo saliente se limita al catálogo de este módulo. Los perfiles JSON carecen de capacidad de envío. El APK funciona offline y carece de programación de Flash, descarga de firmware, modificación del bootloader, cambios de particiones y SAVE persistente. El diagnóstico permanece local hasta que se elige un destino de exportación.
