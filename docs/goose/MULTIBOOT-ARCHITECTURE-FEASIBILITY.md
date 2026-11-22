# Estudio de viabilidad multiboot — FASE 5D

## Evidencia y decisiones

| Arquitectura | Evidencia actual | Memoria/dependencias | Riesgo y estado |
|---|---|---|---|
| A. Aplicación desde RAM | APP1 pi32v2 compilado y enlazado; identidad anuncia RAM, pero no existe carga/ejecución física | Imagen completa + stack + ABI + periféricos; libres desconocidos | Sólo candidato para aplicaciones adaptadas; ejecución no demostrada |
| B. XIP | No hay mapa AC791N validado que pruebe XIP desde Flash/medio externo | Requiere controlador, mapeo, caché, vectores y formato XIP | MicroSD/USB por bloques no equivale a XIP; hipótesis abierta |
| C. Cargador residente sustituyendo DARK-1 | El propietario permite estudiarlo; no existe mapa Flash/ABI/recuperación confirmado | Menú, FAT/medio, imágenes, RAM de trabajo y recuperación | Candidato conceptual, pero escribir Flash y preservar bootloader no están autorizados |
| D. Híbrida Flash+RAM+externo | Combina las hipótesis anteriores | Requiere cargador mínimo, imagen compatible y retorno | Alternativa de diseño, no evidencia de hardware |

La arquitectura candidata de software es **C como objetivo de investigación**, porque no exige conservar DARK-1 y permite un menú residente; la alternativa es **D**, si el bootloader permite cargar aplicaciones adaptadas. Esta no es una autorización de instalación ni una conclusión de viabilidad física. La decisión queda condicionada a mapa Flash, ABI, SRAM, vectores, formato de imagen y recuperación independiente.

## Restricciones demostradas

El bootloader observado es `2.4-395e6cc`; la región `[0x00000,0x04000)` se conserva como protegida. El ELF enlazado en `0x01C30000` es una hipótesis de linker. El core no ejecuta código cargado, no escribe Flash y no activa USB Host. Se desconoce si DARK-1 es reubicable, si depende de Flash/XIP, qué periféricos inicializa y cómo retorna.

## Estado del cargador residente

El diseño C sigue siendo el candidato principal de investigación y requeriría sustituir sólo el área de aplicación, preservar el m-bootloader, iniciar pantalla/controles, montar almacenamiento, validar la imagen y disponer de rollback. La memoria de software medida del core y FAT32 no determina si el cargador cabe en el área real ni si una imagen DARK-1 es reubicable. La recuperación sigue dependiendo de un método oficial externo; no hay prueba de restauración automática.

## Requisitos del primer experimento físico futuro

Antes de cualquier sustitución: documentación independiente del mapa Flash, copia oficial reproducible de DARK-1, método oficial de restauración probado fuera del dispositivo, ABI/entry point, reservas SRAM/stack, watchdog, alimentación y procedimiento de interrupción. Después: revisión de seguridad específica y autorización expresa. Esta fase sólo aporta simulación, no una prueba de arranque.
