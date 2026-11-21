# Compatibilidad de carga desde Android 16

**Estado actual: despliegue físico detenido.** El binario producido es experimental y enlazado para RAM. Con el DARK-1 actual y la tablet como único dispositivo, ninguna de las dos herramientas solicitadas ofrece un camino verificado que cargue este APP1 respetando la restricción de cero escrituras en Flash.

| Medio | Evidencia y decisión |
|---|---|
| FM-1 Flasher para Android | Su `UsbBootConnection.kt` sube sus dos imágenes auxiliares MFLR incluidas, de hash fijo. Las operaciones de biblioteca y recuperación escriben paquetes a Flash. La app no expone una operación de selección y salto a este APP1. [Código de la conexión ROM](https://github.com/ekim-pl/android-fm1-flasher/blob/bfede50cfc9d7a848809980f5b300a465a04c364/app/src/main/java/pl/ekim/fm1_flasher/UsbBootConnection.kt) |
| Chrome Android · Web MIDI | Con DARK-1, la actualización habitual guarda `ota.bin` en Flash antes del reinicio. M-Flasher web y la página de M-Unbricker pueden usar ese método para arrancar una etapa, pero el método incumple el requisito de esta fase. El MIDI de m-bootloader serviría si estuviese instalado y el contrato de invocación fuese compatible con este diagnóstico, que comprueba específicamente el arranque desde ROM. [M-Flasher web](https://github.com/jvitkauskas/m-flasher/blob/8fbece7348d758ac284325eed455781675ac95ff/web/README.md) · [Código OTA de DARK-1](https://github.com/sparky480v/Dark-1/blob/6c0cc34677c4188575158c9ea5f53c95200be4a0/firmware/src/ota.c) |
| Chrome Android · WebUSB | En ROM el FM-1 enumera como `4C4A:8057`, clase Mass Storage. Chromium considera esa clase protegida y no permite reclamarla desde una página normal, aunque la pantalla de autorización muestre un dispositivo USB. [Identificación de la ROM](https://github.com/jvitkauskas/m-flasher/blob/8fbece7348d758ac284325eed455781675ac95ff/src/scsi.rs) · [Clases protegidas en Chromium](https://github.com/chromium/chromium/blob/main/content/browser/usb/web_usb_service_impl.cc#L248) |

## Estado del artefacto y del despliegue

El binario APP1 es un artefacto de compilación para análisis. La documentación de M-Flasher de escritorio demuestra que su herramienta admite una ruta distinta, directamente por RAM. Esa ruta y este prototipo siguen sin validar en un FM-1 físico. La validación de seguridad y de hardware queda pendiente antes de publicar un procedimiento de carga. [Implementación de RAM en M-Flasher](https://github.com/jvitkauskas/m-flasher/blob/8fbece7348d758ac284325eed455781675ac95ff/src/jl.rs)

## Requisito pendiente en Android

Un transporte Android nativo dedicado a **cargar en RAM** tendría que comprobar que el dispositivo está en ROM, validar el rango y el hash de la aplicación y realizar solo la carga y el salto de ROM. Haría falta probar el permiso USB, la desconexión al cambiar de rol y la preservación de la Flash en un FM-1 real. Una detección correcta en Android como USB-MIDI no acredita este requisito.
