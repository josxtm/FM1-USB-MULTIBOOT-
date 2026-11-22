# FASE 4H — Identificación física MIDI y preparación segura de RAM

**Rama:** `goose/fase3b-4a-preflight`  
**PR:** #8 (Draft)  
**Referencia:** FM1_RAM_Loader_v1.0_comp14  
**Estado de software:** comp15 en validación

## Evidencia física nueva

El usuario conectó una tablet Android 16 a un M-VAVE FM-1 real en MIDI MODE y completó la consulta fija de identidad `0x12`. El informe técnico confirmó:

- `IDENTITY=m-bootloader 2.4-395e6cc`
- `PROTOCOL_KIND=1`
- `FLASH_CAPACITY_BYTES=1048576`
- `FLASH_JEDEC=0x856014`
- `PROTOCOL_COMPATIBLE=true`
- `RAM_ANNOUNCED=true`
- `PHYSICAL_WRITE=BLOCKED`
- `RAM_EXECUTION=BLOCKED`
- Resultado de sesión: `RESPONSE`

Hubo dos detecciones iniciales con cero candidatos y una identificación posterior correcta. Esto valida físicamente la ruta Android USB-MIDI → consulta `0x12` → respuesta estructurada del m-bootloader. No valida ejecución RAM, carga APP1, programación Flash ni recuperación completa.

El informe original no se incorpora al repositorio: esta documentación conserva solo los campos técnicos no sensibles autorizados.

## Cambios de detección

- `MidiManager.DeviceCallback` actualiza la enumeración en conexión y desconexión.
- Se muestra el número de candidatos FM-1 disponibles, incluyendo el estado de cero candidatos.
- Se añadió botón de actualización manual para dispositivos que aparecen después de abrir la actividad.
- La política de descubrimiento se aisló en `MbootMidiDiscovery` y no abre dispositivos ni envía comandos.
- El controlador de consultas conserva tokens, timeout, cancelación, desconexión y rechazo de respuestas antiguas.
- Los registros usan UTC ISO-8601 y excluyen UID, seriales, claves y credenciales.

## Preparación RAM — clasificación de evidencia

| Dato | Clasificación | Procedencia | Consecuencia |
|---|---|---|---|
| m-bootloader 2.4-395e6cc responde a `0x12` | Verificado en hardware | Sesión Android real documentada arriba | Solo identidad/protocolo |
| Capacidad 1 MiB y JEDEC `0x856014` | Verificado en hardware | Respuesta real `0x12` | No autoriza lectura ni escritura |
| `RAM_ANNOUNCED=true` | Verificado en hardware | Campo de identidad real | No demuestra ventana, ABI ni ejecución |
| ELF32, máquina pi32v2, entry `0x01C30000` | Verificado por análisis de binarios | `scripts/validate.py`, linker y APP1 generado | Evidencia software, no autorización física |
| APP1 trailer/CRC16/SHA-256 | Verificado por análisis y CI | `scripts/app_trailer.py`, `check_app1_consistency.py` | Integridad del artefacto |
| Ventana APP1 y BSS/stacks documentados | Verificado por análisis de linker | `src/ram.ld`, `docs/goose/RAM-EXECUTION-FEASIBILITY.md` | Requiere confirmación de mapa real |
| Transferencia fragmentada | Verificado en simulación | `AppTransferSimulationTest` | No equivale a transporte físico |
| Arranque, clocks, periféricos y retorno al bootloader | Hipótesis pendiente | Sin gate físico autorizado | No ejecutar |
| Ejecución de firmware completo desde RAM | Hipótesis pendiente | Sin ABI/loader/retorno demostrados | No ejecutar |
| Selector multifirmware desde microSD | Verificado en software | SAF, FM1CAT v1 y Cover Flow | No es menú embebido del FM-1 |

## Matriz del siguiente gate RAM

Antes de proponer cualquier prueba controlada faltan, como mínimo: documentación independiente de mapa RAM y ABI AC791N/pi32v2; confirmación de pila, BSS, clocks y periféricos; protocolo documentado de carga y retorno; imagen de recuperación comprobada; revisión eléctrica y de transporte; plan de abortar sin tocar `[0x00000,0x04000)`; y autorización explícita del usuario para un procedimiento físico reversible.

## Validación CI comp15

- Android/Kotlin, Lint, pruebas MIDI y APK comp15: ✅ [run 44965435916](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44965435916)
- Diagnóstico RAM/pi32v2, USB/FAT32 y coherencia APP1: ✅ [run 44965438484](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44965438484)
- Simulador Cover Flow: ✅ [run 44965441621](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44965441621)
- `make test` local USB/FAT32: ✅

Hasta completar esos gates, `0x25`, `0x30`, Flash y ejecución APP1 permanecen deshabilitados. RAM solo se planifica y simula en software.
