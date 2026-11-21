## Fase 1 de FM1 USB Multiboot

Recupera el diagnóstico de RAM ya desarrollado y lo publica sobre el commit inicial de este repositorio. Conserva el firmware, la prueba de lector simulado y el verificador de arquitectura, mapa y tráiler APP1. GitHub Actions compila y sube los binarios, sus hashes y los resultados.

## Estado

La enumeración, el transporte MSC/BOT y la lectura FAT32 pasaron en simulación. El binario y el ELF son artefactos de evaluación. El controlador USB0, el LCD, los botones y VBUS requieren comprobación física.

La publicación excluye procedimientos de carga física hasta validar la seguridad. El informe de Android recoge los impedimentos de la vía de carga desde la tablet.
