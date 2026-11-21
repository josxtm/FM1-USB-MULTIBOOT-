# Cover Flow simulator · FM1 MULTIBOOT OS

Simulador autónomo, **solo interfaz**. No usa USB, no escribe Flash y no ejecuta firmware.

## Abrir desde Android

1. Abre el archivo index.html de esta carpeta en GitHub.
2. Pulsa Raw y guarda el HTML en Descargas, o usa Download raw file.
3. Abre el HTML descargado con un navegador que admita archivos locales. No requiere conexión ni servidor ni bibliotecas externas.
4. Usa los cuatro botones virtuales, desliza horizontalmente sobre la pantalla o conecta teclado (flechas izquierda/derecha, Intro y Escape).

La pantalla usa un canvas lógico de 240 × 240 px y se amplía en la tablet sin cambiar su resolución interna.

- OCT−: carátula anterior.
- OCT+: siguiente carátula.
- PLAY: muestra el detalle simulado y aclara que no arranca firmware.
- REC: vuelve al menú.

Las portadas son gráficos procedurales originales de ejemplo. Los nombres DARK-1, ORIGINAL, FELUCCA y SLOOP son **marcadores de catálogo**, no descargas ni certificados de compatibilidad. La carátula central y las dos laterales simulan el efecto Cover Flow; la perspectiva aún no está portada a la pantalla del sintetizador.

## Pruebas automáticas

En cualquier equipo con Node.js 20 o superior, ejecutar desde la raíz del repositorio:

    node --test simulator/tests/coverflow.test.mjs

GitHub Actions ejecuta lo mismo en la rama y la PR. No genera un firmware instalable.

## Relación con el hardware

La arquitectura está descrita en docs/FM1-MULTIBOOT-ARCHITECTURE.md. Aún no se han verificado TFT, botones, controladores USB Host, alimentación VBUS, acceso FAT32 en un lector físico ni arrancado firmware desde microSD. DARK-1 permanece instalado en el FM1.