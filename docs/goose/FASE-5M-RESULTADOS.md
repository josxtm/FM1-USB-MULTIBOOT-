# FASE 5M — motor multiboot y compatibilidad

## Motor de decisión

Se añadieron:

- `src/mb_launch_policy.h`
- `src/mb_launch_policy.c`
- `tests/mb_launch_policy_test.c`

El motor separa metadatos de catálogo, integridad, ABI, memoria y método de arranque. Devuelve una clasificación:

- `INSPECTION` — se puede mostrar/inspeccionar.
- `SIMULATION` — ABI y RAM verificados para simulación software.
- `INSTALL_REQUIRED` — necesita instalación autorizada.
- `ADAPTATION_REQUIRED` — requiere adaptación/formato adicional.
- `INCOMPATIBLE` — integridad o requisitos incompatibles.
- `UNKNOWN` — faltan evidencias.

La clasificación no llama a ninguna ruta de ejecución y no sustituye `mb_prepare_launch()`, que sigue bloqueado.

## Matriz actual

| Tipo | Inspección | Simulación | RAM física | XIP | Flash/install | Estado |
|---|---:|---:|---:|---:|---:|---|
| DARK-1 | Sí, sólo metadatos | No demostrado | Desconocido | Desconocido | No autorizado | `UNKNOWN` |
| Melodee | Sí, sólo metadatos | No demostrado | Desconocido | Desconocido | No autorizado | `UNKNOWN` |
| Tessera | Sí, sólo metadatos | No demostrado | Desconocido | Desconocido | No autorizado | `UNKNOWN` |
| BIN FM1CAT | Sí | Sólo si ABI verificado | No | No | No | `INSPECTION` |
| FWS FM1CAT | Sí | Sólo si ABI verificado | No | No | No | `INSPECTION` |
| APP1 experimental propio | Sí | Sí, en simulación | No | No | No | `SIMULATION` |
| Imagen que requiere instalación | Sí | No | No | No | Requiere gate | `INSTALL_REQUIRED` |

Los nombres DARK-1, Melodee y Tessera en la aplicación 5J no implican inclusión, redistribución, compatibilidad ni ejecución de sus binarios.

## Licencias y redistribución

- m-bootloader: MIT según la evidencia pública consultada.
- m-flasher: MIT según la evidencia pública consultada.
- Melodee: GPL-3.0-only.
- Tessera: GPL-3.0-only y reutilización de componentes GPL documentada.
- No se copió código GPL ni se incluyeron binarios propietarios.

## Estrategias

### RAM

Sólo aplicable a una imagen preparada para un ABI y región RAM verificadas. Actualmente es simulación.

### XIP

Sin evidencia suficiente del mapa Flash, vectores, relocación y propiedad del bus. No soportado actualmente.

### Instalación en Flash

Diseño únicamente. Requiere backup, formato oficial, protección del bootloader, verificación posterior, rollback y autorización explícita. Está bloqueado.

### Selector residente

No se sustituye DARK-1 ni se modifica el m-bootloader. Sólo queda como arquitectura futura.

## Recuperación

La recuperación futura debe conservar:

- backup oficial independiente;
- hash de imagen;
- validación antes y después;
- protección de `[0x00000,0x04000)`;
- comportamiento definido ante corte de energía;
- rollback o restauración con m-flasher;
- retorno a firmware oficial.

## Estado de cierre

5M queda **completada como arquitectura de decisión y matriz de compatibilidad software**. No existe un método de arranque físico autorizado ni se presenta el prototipo como multiboot operativo en hardware.
