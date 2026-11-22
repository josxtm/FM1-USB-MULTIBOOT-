# FASE 4C — Coherencia APP1 y desconexión MIDI

**Rama:** `goose/fase3b-4a-preflight`  
**PR:** #8 (Draft)  
**Build:** `FM1_RAM_Loader_v1.0_comp10.apk` / `versionCode 10`

## APP1

La auditoría comparó el asset Android con el artefacto generado por CI en el workflow de diagnóstico.

| Artefacto | Tamaño | SHA-256 |
|---|---:|---|
| Asset anterior | 8.956 B | `810bb3673ef2fc75964724e07d42bb751d1e3e5e49056cdaccad40c3764ea246` |
| Generado por CI | 9.788 B | `364af5cb7ce487fbbb24fd1c910c14885bacb5c58d9292f59926416a3be3ff4d` |

La discrepancia era real: el asset versionado correspondía a una revisión anterior, mientras que el código actual incluye el diagnóstico `FM1/INDEX.CSV` y otras modificaciones de `src/`. El artefacto nuevo fue validado por CI como ELF32 pi32v2, entrada `0x01C30000`, dos segmentos, BSS `0x01C16800` de 2660 bytes, tráiler APP1 v1, placa 1 y CRC16 `0x17BF`.

Se actualizó `AppSlot.SHA256`, el asset Android y las pruebas de tamaño/CRC. Se añadió `scripts/check_app1_consistency.py`, ejecutado por CI, que compara bytes y SHA-256 del binario generado con el asset incluido. Una futura discrepancia detendrá el workflow.

No se ejecutó el binario en hardware.

## Desconexión MIDI

`MbootMidiActivity` registra `MidiManager.DeviceCallback`. Si se retira el `MidiDeviceInfo` activo, se llama a `MbootQueryController.disconnected()`, se invalida el token, se cancela el timer y se cierran `MidiInputPort`, `MidiOutputPort` y `MidiDevice`. Una respuesta antigua no puede completar una reconexión porque su token ya no está activo.

Los tests simulados cubren respuesta fragmentada, timeout, cancelación, desconexión, reemplazo de consulta y respuestas obsoletas. El único mensaje físico sigue siendo la consulta fija `0x12`.

## Estado

- Software: APP1 coherente y comprobado por CI; flujo MIDI y desconexión modelados y probados.
- Hardware previamente documentado: m-bootloader 2.4-395E6CC, menú de recuperación y retorno a DARK-1.
- Pendiente físico: consulta MIDI real, mapa RAM/ABI, VBUS/CC, lector microSD y recuperación integral.

No se habilitaron `0x25`, `0x30`, lectura Flash ambigua, borrado, programación, ejecución APP1 ni USB Host físico.
