# FM1 MULTIBOOT OS · Auditoría 5Y–6D

Arquitectura conservada:

```text
microSD SPI1 → selector STG2 desde recuperación → catálogo FM1CAT → selección
→ futura instalación controlada en Flash → reinicio → sintetizador musical desde Flash
```

El STG2 y el APP1 de diagnóstico son programas temporales de control en SRAM. El futuro firmware musical requiere su mapa y modo de ejecución propios desde Flash. La elección no depende de Android.

| Área | Implementado | Verificado por software | Pendiente o bloqueo |
|---|---|---|---|
| 5Y, almacenamiento y recuperación | Instalador virtual en archivos regulares, journal, copias y restauración. Ventana sintética `[0x4000,0x10000)`. Auditor de respaldos. | Matriz de cortes y reapertura, hash y protección de regiones en datos ficticios. | Falta un mapa de la unidad verificado y un método de recuperación física ensayado. La política por defecto no autoriza ningún rango. |
| 5Z, selector autónomo | STG2 pi32v2 `0x01C28000` con retorno al menú, FAT32, selección y SHA-256. | Hash anclado, carga con el código m-bootloader 2.4 fijado y UI, SPI y errores simulados. | Pantalla, controles, retorno, alimentación SPI1 y acceso a la tarjeta en el FM‑1. |
| 6A, diagnóstico ROM | APP1 `0x01C30000`, pantalla, teclas y TMR0. Entrada ROM, pilas privadas, watchdog y espera tras salida. | ELF y tamaños, hash, MMIO simulado, ausencia de rutas Flash en el binario y pruebas de fallos. | Ejecución real, frecuencia, recuperación del menú y arranque posterior de DARK-1. |
| 6B y 6C, preflight y transporte | Verificación offline, auditoría upstream y motor de parada estricta con BOT en memoria y salto único. | 9 bloques completos del binario fijado, mutaciones de petición, CSW, sondas de residente, residuos, cortes, timeouts y sesión concurrente. | Backend USB físico deshabilitado. Se necesita una futura integración que mantenga estas garantías, con exclusividad y cualificación de ROM comprobadas. La versión estándar de M-Flasher 0.12.1 sigue descartada para el ensayo. |
| 6D, preparación de SD | ZIP verificable, catálogo y estructura para imágenes privadas, perfiles y fixtures marcados. | Paquetes reproducibles, cadena fragmentada MBR y superfloppy, cargador 2.4 y selector host con los mismos sectores. | No hay firmware musical real validado para instalar. No se ha probado m-card en este FM‑1. |
| USB-C MSC | Investigación y diagnósticos de software previos. | Simulación de piezas del transporte y FAT32. | **NO-GO con la recuperación instalada.** La revisión carece de ruta USB Host MSC en recuperación y las condiciones CC/VBUS no están demostradas. |

## Defectos y límites identificados

- La auditoría detectó que un rango explícito en `FlashRegionPolicy` podía cubrir el sector key/MAC aunque la política por defecto estuviera cerrada. Se corrigió: el constructor rechaza cualquier rango cuyo final invada ese sector. Se añadió una regresión. La corrección sólo afecta al simulador.
- Los defectos de sonda ambigua, conteos inválidos y reintentos del M-Flasher auditado quedan cubiertos por el nuevo motor de 6C en simulación. El CLI upstream permanece separado. No se hereda como backend físico.
- El cargador upstream de 2.4 recorre un directorio FAT mediante `next(c)` sin el límite de transiciones de nuestro selector. Un directorio sin terminador que forme un ciclo podría impedir que el control llegue al STG2. Se conserva el bootloader instalado por alcance. El medio del futuro ensayo debe verificarse en un ordenador y quedar libre de errores. Las pruebas del selector ejercitan fallos después de su propia entrada. [Código de lectura del bootloader](https://github.com/jvitkauskas/m-bootloader/blob/395e6cc469ba1d6eae9922a752d1f416ff07b69a/src/sdboot.c)
- La validación local de un SLOT aporta integridad y correspondencia de perfil, con compatibilidad de arranque aún `UNVERIFIED`. Quedan como trabajo de software un adaptador físico que implemente parada estricta y la validación de mapas de imágenes musicales con evidencia propia. La ausencia de una imagen verificada impide ensayar una instalación.

## Requisitos para futuras pruebas

| Prueba | Requisitos |
|---|---|
| Diagnóstico ROM | Autorización específica, copia externa verificable y vía de recuperación, anfitrión dedicado, perfil real de endpoints y negativa de sonda contrastados, sesión fría exclusiva, candidato 6A exacto y transporte que pare al primer fallo. Verificar al final arranque de DARK-1 y retorno al menú. |
| Selector SPI1 | Autorización independiente para m‑card, revisión/cable/holgura y alimentación medidos, ausencia de contención con LCD y medio FAT32 sano con el paquete vacío. Confirmar inicio desde SD CARD, menú, controles, avisos y retorno. |
| Instalación real de firmware | Imagen de procedencia autorizada, validación de mapa y aplicación XIP, preservación de bootloader/configuración/key/MAC/calibración, copia recuperable y verificación de recuperación ante fallo y pérdida de energía. Autorización adicional para la escritura. El lanzamiento permanece `MB_LAUNCH_BLOCKED`. |

**Decisión física al cierre de 6D: NO-GO.** Ambas fases se validan en software. Quedan pendientes la integración de transporte físico y las comprobaciones de la unidad. No hubo I/O sobre sintetizador o almacenamiento físico y las PR permanecen separadas y sin fusionar.

[Resultados 5Y](goose/FASE-5Y-RESULTADOS.md) · [Resultados 5Z](goose/FASE-5Z-RESULTADOS.md) · [Resultados 6A](FASE-6A-RESULTADOS.md) · [Resultados 6B](FASE-6B-RESULTADOS.md) · [Transporte 6C](FASE-6C-TRANSPORTE-ROM.md) · [Paquete 6D](FASE-6D-MICROSD-PAQUETE.md)
