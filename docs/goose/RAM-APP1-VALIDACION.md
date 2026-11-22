# Validación segura de APP1 y preparación RAM

## Política

Esta fase valida artefactos en software y prepara interfaces desacopladas del transporte. **No ejecuta APP1 en un FM-1 real** y no habilita los comandos `0x25` ni `0x30`. No hay botón Android para enviarlos.

## Reglas de aceptación actuales

| Comprobación | Regla |
|---|---|
| Formato | ELF32 little-endian, máquina pi32v2 (241), dos segmentos cargables |
| Entrada | `0x01C30000` |
| Ventana | `[0x01C30000, 0x01C7FD80)`; sin overflow y tamaño par |
| BSS | Comienza en `0x01C16800` y termina antes de `0x01C1E000` |
| APP1 | Tráiler `APP1`, placa 1, formato 1, CRC16-XMODEM |
| Identidad de artefacto | Android acepta solamente el SHA-256 fijado del diagnóstico experimental |
| Transporte | Bloques de hasta 512 bytes en la simulación; nunca transporte físico de carga |

El script reproducible es `scripts/validate.py`; `make` también compara el binario plano con el segmento ELF y genera `SHA256SUMS.txt`. El validador no puede demostrar que esas direcciones estén libres en el bootloader instalado, ni validar relojes, periféricos, pila real o ABI de entrada.

## Casos adversariales requeridos

- ELF con máquina, entrada, número de segmentos o flags incorrectos.
- Segmento fuera de ventana, BSS solapada, tamaño cero, desbordamiento y APP1 impar.
- CRC modificado, tráiler cambiado, placa/formato erróneos y hash no aprobado.
- Paquete truncado, reordenado, duplicado, timeout y desconexión durante una simulación.
- Nunca convertir un resultado de validación en autorización de `0x25`/`0x30`.

## Cobertura FASE 4A

La APK conserva el rechazo previo a cualquier carga y las pruebas de `ApprovedApp` cubren tráiler, placa, formato, CRC, hash, tamaño, rango exclusivo y copia inmutable. Las simulaciones de transporte MIDI ahora cubren fragmentación, timeout y desconexión sin habilitar `0x25`/`0x30`.

## Estado

- **Verificado en software:** límites, formato, CRC, hash fijado y simulaciones existentes.
- **Verificado en hardware:** ninguno para APP1/RAM.
- **Pendiente:** reservas SRAM del m-bootloader 2.4 instalado, ABI de transición, reloj, LCD/USB, acuse de salto, retorno a DARK-1 y recuperación.

## Procedimiento seguro para una futura CI

```sh
make test
make                       # requiere toolchain pi32v2 local
cd android
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Si falta JDK/SDK o el toolchain, CI debe marcar el job como no ejecutado, conservar el log y no publicar el artefacto como validado. Una futura prueba física solo podrá empezar tras revisión de seguridad independiente, copia íntegra de Flash y caracterización eléctrica; no debe tocar `[0x00000,0x04000)` ni programar Flash.
