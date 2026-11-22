# Arquitectura de arranque multifirmware

La arquitectura queda dividida en contratos:

1. **Selector**: Cover Flow, navegación y PLAY de inspección.
2. **Inspector**: formato, tamaño, CRC/SHA, regiones y requisitos.
3. **Planificador**: `mb_launch_policy`, clasificación y razón.
4. **Gestor de instalación**: futuro; sólo transacciones virtuales en 5R.
5. **Recuperación**: independiente del firmware seleccionado.
6. **Ejecutor**: interfaz futura, deliberadamente ausente/bloqueada.

## Alternativas

* RAM directa: viable sólo para una imagen cuyo ABI, entry, SRAM, BSS, stack y reloj estén demostrados. APP1 actual es candidato estructural, no prueba física.
* XIP: no demostrada; requiere mapa Flash, vectores, reloj y compatibilidad de código/datos.
* Instalación: la alternativa más plausible si el bootloader sólo arranca el área de aplicación, pero exige mapa, transacción y recovery probados.
* Cargador residente: requiere modificar/instalar una aplicación residente, todavía prohibido y sin autorización.

La opción de investigación más prudente es conservar firmwares en microSD y generar una instalación virtual/verificada sólo cuando exista evidencia del área de aplicación. No se confunde esto con multiboot directo.
