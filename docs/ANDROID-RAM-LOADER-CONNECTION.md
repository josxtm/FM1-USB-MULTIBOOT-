# Usar FM1 RAM Loader en Android 16

Esta compilación sirve para detectar un FM‑1 y simular la carga. **Cargar en RAM** permanece bloqueado.

## Detectar y consultar

1. Abre **FM1 RAM Loader** con la tablet en horizontal. Conecta el FM‑1 por USB‑C con un cable de datos y toca **Detectar FM1**.
2. En modo **MIDI** la aplicación muestra el VID/PID y la interfaz. El botón **Conectar** en ese modo solo actualiza el estado de la detección.
3. Si el dispositivo ya está en modo **ROM**, selecciona `4C4A:8057`, toca **Conectar** y concede el permiso USB de Android. La app realiza una única consulta SCSI INQUIRY y cierra la interfaz. Si la consulta es correcta, muestra la firma USB **WL82 / UBOOT1.00**. La Flash queda sin consultar.
4. Si el panel indica que falló `claimInterface(false)`, cierra otras aplicaciones USB y prueba **Conectar** de nuevo. Si ofrece **Reintentar con acceso exclusivo**, confirma solo si el FM‑1 está en ROM y su almacenamiento no está montado ni en uso. Esa opción puede desvincular temporalmente el controlador de Android. Si Android informa de un volumen extraíble activo, la app bloqueará el intento. La consola registra las llamadas y sus resultados.
5. Para cancelar una consulta o cerrar la sesión local, toca **Desconectar**. Si la ROM se reenumera, toca **Detectar FM1** y abre una nueva sesión con **Conectar**. Android puede requerir otra autorización.

Para comprobar la comp3 en la tablet, busca **WL82 / UBOOT1.00** y `INQUIRY + CSW válidos` en la consola. Si la consulta falla, comparte la salida de **Exportar registro** desde la enumeración hasta `close()`. El registro no incluye números de serie ni rutas USB.

La transición al modo ROM, la carga física y el regreso tras ejecutar el diagnóstico están pendientes de validación independiente. La app carece de una acción para cambiar de modo.

## Validar y simular

1. Toca **Usar diagnóstico de Fase 1**, o selecciona el mismo `fm1-usb-diagnostics-ram-experimental.bin` desde el selector de archivos. Toca **Validar archivo**.
2. Con los cinco controles aprobados, toca **Simular carga**. El progreso y la consola llevan la marca de simulación. Este recorrido funciona sin conectar el FM‑1.
3. **Exportar registro** guarda una copia local de la consola mediante el selector de Android.
