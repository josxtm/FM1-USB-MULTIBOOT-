# FM1 USB Multiboot · Fase 1

Prototipo de diagnóstico USB Host para **M-VAVE FM-1 con AC791N**. La configuración objetivo es Flash Puya `0x856014`, de 1 MiB, con DARK-1. El binario es una aplicación experimental **de RAM**. La compilación produce un ELF de auditoría y un binario plano APP1. No se han ejecutado en un FM-1 físico.

| Componente | Estado |
|---|---|
| Enumeración USB, MSC/BOT y FAT32 de solo lectura | Verificados con lector simulado. |
| ELF pi32v2 y binario APP1 | Compilados y validados en software. |
| Controlador USB0, LCD y botones | Prototipo enlazado, pendiente de verificación en el FM-1. |
| VBUS, rol USB-C y carga desde Android 16 | Pendientes. DARK-1 usa su Flash para la etapa OTA por MIDI. Las herramientas Android examinadas no ofrecen una vía compatible para este APP1. |

La validación de seguridad sigue pendiente antes de publicar un procedimiento de carga física.

- [Investigación USB Host, mapa y pruebas](docs/USB-HOST-INVESTIGATION.md)
- [Compatibilidad de Android y límites del despliegue](docs/ANDROID-INSTALLATION.md)
- [Licencias y atribuciones](NOTICE.md)

## Compilar y probar en Linux x86-64

```sh
make test
scripts/get-toolchain.sh
make all
(cd build && sha256sum -c SHA256SUMS.txt)
```

El workflow usa las mismas instrucciones. El binario esperado es `build/fm1-usb-diagnostics-ram-experimental.bin`. El informe `build/elf-report.txt` y el mapa permiten revisar la arquitectura y los rangos. El ejecutable de simulación comprueba el flujo desde los descriptores USB hasta los bytes leídos del archivo.
