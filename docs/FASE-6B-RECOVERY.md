# Fase 6B · Parada, recuperación y hoja de ensayo

**Firmware de referencia actual comunicado por el propietario:** SKIFF. No solicitar comprobaciones de DARK-1, que ya no está instalado.

## Parada

Parar ante identidad o dirección ambigua, hash o versión incorrectos, alimentación inestable, cambios de la ruta USB, acceso no exclusivo, residente desconocido, error de protocolo, longitud corta, timeout, pantalla sin respuesta, reinicios, tecla bloqueada o temporizador anómalo. Un salto sin acuse sólo se espera por el apagado del PHY. Cuando la pantalla no confirma ejecución, el estado se registra como no confirmado. Nunca se vuelve a emitir el salto.

Para una futura sesión autorizada, detener el transporte y liberar el handle. Si el programa responde, mantener REC dos segundos o esperar su límite. En ambos casos el candidato queda a la espera del interruptor. Si no responde, cortar con el interruptor. Desconectar el cable de datos para confirmar un apagado real. No desmontar el FM‑1. No ejecutar un reset USB desde el host, un segundo APP1, un reinstalador ni una restauración automática.

Con el aparato apagado y sin accesorios, arrancar sin pulsar teclas y comprobar SKIFF, pantalla, teclas y audio. Luego, desde apagado, entrar con PLAY al menú de m-bootloader, comprobar versión, estado de aplicación y ausencia de actualizaciones pendientes. Volver a arrancar normalmente. Cualquier divergencia deja la prueba fallida y el FM-1 fuera de nuevos ensayos. Un arranque oscuro o que no enumera no se resuelve probando imágenes al azar. REC retenido al encender es la ruta documentada de stage 0 si el menú falla. Su uso como diagnóstico independiente sólo se contempla con autorización adicional. El respaldo externo y cualquier posible recuperación Flash se evalúan en una operación separada, con el propietario presente. [ROM y recuperación §4.2–4.3](https://github.com/jvitkauskas/m-bootloader/blob/395e6cc469ba1d6eae9922a752d1f416ff07b69a/docs/SPEC.md#42-entering-rom-usb-download-mode)

El operador debe disponer previamente de una copia privada extraída como archivo, cotejada con su registro y abierta en modo offline. Auditar el tamaño, mapa y hash con el auditor Flash existente de 5Y. No crear otro backup por un flujo genérico del flasher durante el ensayo ni adjuntar la ROM, eFuses, chip key, MAC, número de serie o identificadores de rutas USB a ningún informe público. Confirmar que hay un mecanismo de recuperación verificable para ese archivo y abortar si esa comprobación está pendiente. Sólo se devuelve a la hoja el estado de la evidencia, sin el contenido.

## Hoja del futuro ensayo físico

Copiar la tabla y rellenarla durante la sesión. Hasta entonces, mantener `NOT VERIFIED` en observaciones físicas. Conservar el informe JSON offline por separado.

| Campo | Valor / observación del operador |
|---|---|
| Fecha, zona horaria, autorización de ensayo | |
| Anfitrión, sistema, arquitectura y transporte aprobado | |
| Versión de M-Flasher auditada / versión del transporte utilizado | |
| Referencia SHA-256 del APP1 y comprobación previa | |
| Modelo, versión del menú y Flash mostrada | |
| Estado anterior de SKIFF, pantalla, teclado, audio | NOT VERIFIED |
| Copia externa privada disponible y recuperable | NOT VERIFIED |
| Entrada ROM, identificación única y exclusividad | NOT VERIFIED |
| Carga RAM, bloques completos y estado de respuesta al salto | NOT VERIFIED |
| Pantalla, texto inicial y estabilidad | NOT VERIFIED |
| OCT− | NOT VERIFIED |
| OCT+ | NOT VERIFIED |
| PLAY | NOT VERIFIED |
| REC | NOT VERIFIED |
| TMR0 frente al reloj de referencia | NOT VERIFIED |
| Salida y apagado real | NOT VERIFIED |
| Arranque normal posterior y funcionamiento | NOT VERIFIED |
| Menú de recuperación posterior y versión | NOT VERIFIED |
| Anomalías y orden temporal | |
| Resultado final y motivo | NOT VERIFIED |
