# Fase 6B · Preflight de hardware offline

`scripts/fm1_hardware_preflight.py` usa sólo la biblioteca estándar de Python. Lee archivos locales y emite JSON por stdout. No enumera USB, consulta el MIDI, ejecuta M-Flasher, carga código ni cambia modos. La comprobación M-Flasher se realiza sobre los fuentes auditados y el SHA-256 del paquete oficial de su plataforma. En Linux se comprueba también la versión de glibc. El preflight no certifica un ejecutable instalado ni los permisos del anfitrión.

## Reproducción

Se admite el ZIP original de la PR #12 o la raíz de un checkout con el candidato recompilado en `build/romdiag` y los dos logs de pruebas de la raíz. El ZIP se valida completo antes de leer miembros. Para un checkout se comprueban las ocho huellas de los miembros de compilación/ABI y se inspeccionan los logs. En ambos casos se reutiliza el validador 6A sobre el ELF y los bytes del BIN. La CI reconstruye con pi32v2 LLVM `23.1.5-2` y fija la revisión del bootloader y de M-Flasher.

```sh
python3 scripts/fm1_hardware_preflight.py FM1-6A-ROM-diagnostico-experimental.zip \
  --mflasher-source /ruta/local/m-flasher \
  --mflasher-release /ruta/local/m-flasher-0.12.1-linux-x86_64.tar.gz \
  > fm1-6b-preflight.json
```

Usar `v0.12.1` / `8fbece7348d758ac284325eed455781675ac95ff` para el checkout. En macOS usar el DMG universal y en Windows x64 el ZIP oficial correspondiente. En Linux se admiten los tar x86_64 y AArch64. [Artefacto original PR #12](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/45129290479/artifacts/14796927522) · [Paquetes y código de M-Flasher](https://github.com/jvitkauskas/m-flasher/releases/tag/v0.12.1)

| Resultado | Interpretación |
|---|---|
| `PASS`, salida 0 | Verificaciones de software completas. Sigue `physical_status: BLOCKED`. |
| `FAIL`, salida 1 | Falta un archivo obligatorio, hay un hash o formato incorrecto o falla otra verificación. |
| `NOT VERIFIED`, salida 2 | Falta evidencia opcional para determinar el resultado, o el anfitrión no está en la matriz de paquetes. |
| `BLOCKED` | Decisión física fija del informe. No hay opción capaz de cambiarla ni de ejecutar algo sobre el FM-1. |

El candidato lleva `APP1`, placa 1, formato 1 y CRC16 `0xB786`. Su segmento ELF se comprueba contra los 4.092 B de payload. El tráiler completa 4.100 B y el fin de la transferencia queda en `0x01C31004`. Área baja `[0x01C16800,0x01C18038)` con 1.080 B de BSS, 4.096 B de pila principal y 1.024 B de pila de excepciones. El informe de pila fijado por hash registra 108 B como mayor marco y 696 B de suma que emitió el análisis estático 6A. La rama terminal del trampolín impide volver al llamador. Las ranuras de residente/STG2 `[0x01C20000,0x01C30000)` y la memoria retenida a partir de `0x01C7FD80` quedan fuera de los dos segmentos. Los rangos proceden del contrato documentado y se han validado con el ELF, pendientes de comprobar en esta unidad. [ABI 6A](FASE-6A-ROM-ABI.md) · [Contrato upstream §11.8](https://github.com/jvitkauskas/m-bootloader/blob/395e6cc469ba1d6eae9922a752d1f416ff07b69a/docs/SPEC.md#118-ram-apps)

La sección `simulation` contiene resultados sintéticos de una política de una sola ejecución. Evalúa orden de comandos, longitudes y fallos y mantiene distintas la pérdida de respuesta al salto, la observación del panel y la recuperación. No simula un controlador USB completo ni sale a hardware. `physical_blockers` registra por separado autorización, anfitrión sin validar, residente, transporte y recuperación. [Auditoría](FASE-6B-MFLASHER-AUDIT.md) · [Plan físico](FASE-6B-PHYSICAL-TEST-PLAN.md)
