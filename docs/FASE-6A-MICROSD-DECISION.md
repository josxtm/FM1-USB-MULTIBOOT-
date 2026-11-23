# Fase 6A · Decisión de almacenamiento externo

| Ruta | Evidencia disponible | Estado |
|---|---|---|
| SPI1 / m-card | El m‑bootloader `2.4-395E6CC` sondea SPI1 en recuperación y ofrece **SD CARD** cuando responde la tarjeta. El STG2 5Z y FM1CAT ya compilan y se probaron con FAT32 simulado. | Vía preparada en software. La placa compatible no está demostrada en este FM‑1 y queda fuera del primer ensayo ROM. |
| Lector microSD USB‑C | El m‑bootloader instalado carece de entrada MSC Host en recuperación. La ruta CC/5 V, la limitación y el retorno de corriente de esta placa permanecen por comprobar. | **NO-GO** con el equipo actual. El diagnóstico ROM tampoco inicia Host ni VBUS. |

En la ruta SPI sería indispensable disponer de una interfaz que implemente el diseño m‑card compatible con la revisión del FM‑1, con intercalación en J12, FFC apropiados, lógica de selección invertida y alimentación de 3,3 V, más una microSD compatible FAT32 de bloques de 512 B. Es un proyecto de hardware separado y requiere abrir el sintetizador. Antes de autorizar su instalación, comprobar orientación, continuidad del conector y ausencia de cortos, tensión bajo carga y consumo, niveles PC7–PC10, aislamiento de D/C y ausencia de contención entre tarjeta y LCD. El upstream presenta la PCB como prototipo y el código probado en otras placas no certifica esta unidad. No se ha instalado ni comprado hardware.

Para reconsiderar USB‑C hacen falta tanto una entrada de recuperación compatible con el bootloader intacto como evidencia eléctrica del rol, CC y VBUS de esta revisión, incluida protección contra corriente inversa. Sin esos dos elementos, añadir alimentación externa no resuelve la compatibilidad ni es una prueba autorizada. [Contrato SPI y matriz 5Z](goose/FASE-5Z-MCARD-COMPATIBILITY.md) · [Evidencia USB‑C 5Z](goose/FASE-5Z-USB-C-HARDWARE.md) · [m-card](https://github.com/jvitkauskas/m-card/tree/ec63784fe94006296ad25bdeb5d32bf7afa834d1)
