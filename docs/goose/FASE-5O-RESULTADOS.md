# FASE 5O — resultados de preflight RAM

## Alcance

Se redujeron incertidumbres por análisis de fuentes y por validación host, sin tocar hardware. Se conservaron Cover Flow, HAL, backends DEMO/USB y el bloqueo permanente de lanzamiento.

## Resuelto

* TMR0 ya no divide por cero ni calcula esperas con overflow: tasa cero es fallo, el producto usa intermedio amplio, los microsegundos se redondean y los deltas se limitan a media vuelta.
* La evidencia pública del m-bootloader fija un perfil documentado de 24 MHz de cristal / 60 MHz LSB y la fórmula de TMR0. Se clasificó correctamente como evidencia de fuente, no como medición del FM-1 instalado.
* Se separaron conceptualmente stage-2 (argumento MHz) y run-ram (puntero ROM); el ensamblador experimental no se declaró compatible con ambos.
* Se añadió el candidato mínimo independiente `src/ram_minimal.c` y gates `ram-minimal-test`/`ram-minimal-pi32v2-object`.
* Se documentaron carga, memoria, recuperación y gates de primera ejecución.

## Pendiente real

No está resuelto el contrato exacto de `2.4-395E6CC`: caller, registros, reloj heredado, watchdog, stacks, BSS/.data, vector, mapa SRAM y retorno. Tampoco hay validación física de CC/VBUS, USB Host, LCD o timer. La toolchain pi32v2 puede verificar sintaxis/link, pero no SRAM instalada.

## Verificación prevista

`make test`, suites C99, sanitizers, `make ram-app-test clock-test timer-test ram-minimal-test ram-image-validator-test`, simulador Cover Flow, link pi32v2 y workflow Android deben permanecer verdes. La imagen completa sigue siendo estructuralmente validable por `validate_ram_image.py`; ese resultado no constituye autorización de ejecución.

## Prerrequisitos físicos exactos

Sólo tras autorización: identificar modo de descarga, confirmar ABI del caller y mapa SRAM con documentación/medición del propietario, preparar recuperación y probar primero el mínimo RAM-only. No Flash, no DARK-1/m-bootloader, no USB Host, no VBUS/CC, no SCSI writes y no firmware launch.

Estado final: `MB_LAUNCH_BLOCKED`; sin ejecución física.
