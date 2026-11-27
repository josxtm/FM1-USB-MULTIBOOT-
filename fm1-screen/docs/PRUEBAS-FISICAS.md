# Validación en FM-1 físico

1. Arranca normalmente en DARK-1 y anota la versión visible. Comprueba el audio por la salida del FM-1.
2. Conecta por USB-C de datos y entra en **Conexión**. Conserva **Solo visualización**. Exporta **Diagnóstico** con el puerto e interfaces.
3. Si el puerto es reconocido, activa **Solicitar duplicación** con **Auto**. Espera diez segundos, navega en el FM-1 y exporta el registro. La falta de LCD es un resultado válido en un firmware sin emisor identificado.
4. Reconecta el cable y comprueba que el puerto se recupera y el sonido sigue en el FM-1.
5. Repite con SKIFF y FRASIL cuando ya estén instalados. En SKIFF anota si el binario es una variante Big Screen. En FRASIL 3.4.1 selecciona fm1-base y compara las tres vistas con el LCD.

En FRASIL, después de validar la pantalla, cambia a control táctil. Pulsa una tecla, mantenla, prueba un acorde y gira K1. Comprueba la liberación al cambiar de vista y desconectar. Repite el enlace con BLE solo si el firmware ofrece BLE MIDI.

Registra 30 s de actividad gráfica y 10 min de estabilidad por transporte. Para la latencia extremo a extremo, filma conjuntamente el FM-1 y la tablet con una cámara de alta velocidad y cuenta cuadros entre cambios equivalentes. Documenta la versión, FPS y audio de cada prueba. Usa el modo de arranque normal.
