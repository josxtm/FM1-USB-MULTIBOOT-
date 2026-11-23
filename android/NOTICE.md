# Licencias y atribuciones

El código de la aplicación se ofrece con la licencia [MIT del proyecto](../LICENSE). El diagnóstico APP1 incluido es el artefacto MIT de la Fase 1, con sus fuentes y atribuciones en el mismo repositorio. [Avisos de la Fase 1](../NOTICE.md)

El algoritmo de transformación por bloque, su constante CRC y el formato de comandos de RAM que adapta `RomProtocol.kt` proceden de M-Flasher, de Julius Vitkauskas, bajo MIT. Su licencia y aviso de copyright están en [LICENSE](../LICENSE). [Código original, commit 8fbece7](https://github.com/jvitkauskas/m-flasher/blob/8fbece7348d758ac284325eed455781675ac95ff/src/jl.rs)

La implementación de BOT para Android se contrastó con Android FM-1 Flasher, de ekim.pl, bajo MIT. Esta aplicación implementa su propia consulta aislada y no incorpora sus imágenes MFLR. [Referencia Android](https://github.com/ekim-pl/android-fm1-flasher/blob/bfede50cfc9d7a848809980f5b300a465a04c364/app/src/main/java/pl/ekim/fm1_flasher/UsbBootConnection.kt)

## FM1 Manager To USB

El catálogo incluye metadatos normalizados de The Firmware Hub (CC BY 4.0), con imágenes y textos de los autores identificados por su repositorio, y referencias públicas de 2bbb/fm1-firmware-collection y FM1 Editor. La APK no distribuye paquetes de firmware. [The Firmware Hub](https://thefirmwarehub.com/kb/#catalog)

El analizador de cabeceras FWSC/UFW usa la descripción de formato de M‑Flasher `8fbece7348d758ac284325eed455781675ac95ff` (MIT). La implementación Kotlin sólo inspecciona el contenedor. [M‑Flasher](https://github.com/jvitkauskas/m-flasher/blob/8fbece7348d758ac284325eed455781675ac95ff/src/formats.rs#L449)
