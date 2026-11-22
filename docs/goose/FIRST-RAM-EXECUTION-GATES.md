# Gates para la primera ejecución RAM

Todos son obligatorios y acumulativos. El estado actual es **NO AUTORIZADO**.

1. Preservar evidencia: confirmar rama, PR #8 Draft, `main` intacta y copia del `core` sin tocar.
2. Obtener y registrar identidad del dispositivo con la consulta fija; no inferir ABI sólo de la versión.
3. Revisión del SPEC/bootloader instalado por el propietario: perfil stage-2 o run-ram, argumento, reloj, stack, BSS, `.data`, vector, watchdog, USB y retorno.
4. Confirmar mapa SRAM y región retenida mediante documentación o medición autorizada; revisar ELF/flat/APP1, SHA y CRC independientemente.
5. Confirmar que la imagen mínima no tiene MMIO/USB/LCD/Flash ni símbolos de lanzamiento y que su único resultado observable sea el marcador documentado.
6. Preparar recuperación antes de cargar: cable/host de descarga, procedimiento de entrada a download, identidad y comando de recuperación documentados.
7. Autorización explícita y separada para la primera ejecución. Debe ser RAM-only, sin Flash erase/write, sin USB Host, sin SCSI write, sin VBUS/CC y con límite temporal.
8. Primer ensayo sólo con el candidato mínimo. Cancelar ante pantalla negra, pérdida de enlace, watchdog, corriente anómala, identidad inesperada o ausencia de retorno esperado.
9. Sólo después de revisar evidencia se puede considerar Cover Flow; nunca se habilita `mb_prepare_launch()`, que sigue devolviendo `MB_LAUNCH_BLOCKED`.

La recuperación documentada no equivale a recuperación probada. La región protegida, DARK-1 y m-bootloader no se alteran en esta fase.
