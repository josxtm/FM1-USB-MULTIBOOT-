# Fase 3B — m-bootloader 2.4: primera conexión Android USB-MIDI

## Evidencia de origen

La unidad física presenta menú **FM-1 M-BOOTLOADER 2.4-395E6CC**, APP OK, 581564 B, UPDATE NONE, FLASH 856014 1024K. PLAY durante encendido abre menú y BOOT APP devuelve a DARK-1. No se ha verificado una restauración completa de Flash.

Fuente del protocolo: [m-bootloader SPEC §11.6 y §11.8](https://github.com/jvitkauskas/m-bootloader/blob/main/docs/SPEC.md); implementación de referencia [midi_update.c](https://github.com/jvitkauskas/m-bootloader/blob/main/src/midi_update.c) (MIT).

## Implementado en comp6

- Pantalla Android separada para el dispositivo USB-MIDI **FM-1 Update** en **MIDI MODE**; no se envían mensajes a DARK-1 en su modo MIDI normal.
- Consulta estrictamente fija `0x12`: marco F0/F7, empaquetado 8→7 en flujo de bits little-endian (no el esquema de bits altos por grupos), bytes sin empaquetar `00 59 CMD LEN24LE BODY CHECKSUM`.
- Espera y parseo con límites, checksum del cuerpo, literal `MBOOT`, protocolo v1, tipo bootloader, capacidad de 1 MiB y JEDEC `0x856014`; informa versión de la respuesta y flag de soporte RAM. No se guarda ni muestra la clave del chip ni su UID.
- Consulta cancelable, timeout, cierre de puertos. La conexión MIDI nunca envía `0x21`, `0x22`, `0x23`, `0x24`, `0x25` ni `0x30`.
- Tests unitarios de trama, parseo, rechazo de comando inesperado, fragmentación y suma de control.
- Protocolo ROM anterior, biblioteca de microSD y simulador continúan presentes.

## Riesgos especialmente importantes

**La especificación advierte que 0x23 (lectura Flash) difiere por un solo bit de 0x21 (borrado Flash) de algunos firmwares.** El checksum M-UPGRADE NO cubre el opcode. Por tanto no implementamos aún lectura física desde la tablet ni asumimos que una simple comprobación de checksum impediría una operación destructiva. La identidad del dispositivo debe validarse en la sesión antes de considerar lecturas.

Los comandos de carga RAM (0x25) y de programación Flash siguen bloqueados. La comp6 es **únicamente una herramienta de identificación física de solo lectura** y no constituye todavía el cargador completo que describe la directiva a largo plazo.

Se conserva el diagnóstico antiguo APP1 únicamente para simulación; el SHA de Fase 1 no se reutilizará para un binario nuevo. La versión de Android se incrementa a 1.0-comp6; el procedimiento para respaldar 1 MiB desde MIDI está pendiente de diseño y pruebas.

## Primera prueba física propuesta

1. En FM1 apagado, mantener PLAY y encender.
2. Elegir `MIDI MODE`.
3. Conectar por USB-C de datos a la tablet Android.
4. Abrir `M-BOOTLOADER 2.4 · Identificar por USB-MIDI` en comp6.
5. Pulsar `Detectar y consultar m-bootloader`.
6. Esperar texto con versión y JEDEC; si falla, exportar logs técnicos sin UID ni chip key.
7. Salir y usar BOOT APP para volver a DARK-1; no tocar DOWNLOAD MODE ni instalar nada.

**No se ha realizado todavía esta prueba en el FM1 físico.** Android USB-MIDI y el comportamiento de respuesta real están pendientes.

## Pendientes de Fase 3B→4A

- Confirmar consulta 0x12 en dispositivo real y regresión con DARK-1.
- Auditar protocolo de backup y riesgos 0x23/0x21, implementar transporte de lectura únicamente después de una segunda decisión de seguridad.
- Generar APP1 mínimo independiente, revisar su ABI y map de RAM desde modo MIDI (60 MHz LSB).
- Implementar 0x25/0x30 y aprobar riesgo antes de habilitar carga RAM real.
- Sincronizar el APP1 FAT32 actualizado de PR #6 con la futura APK.
- Comprobar voltaje VBUS y USB-C CC en placa antes de utilizar lector microSD pasivo.
- Comprobar un procedimiento de restauración física desde Android (no se harán pruebas destructivas en esta fase).

Prohibido fusionar PR o escribir en Flash.
