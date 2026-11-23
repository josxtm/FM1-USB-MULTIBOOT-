# FM1 MULTIBOOT OS · Fase USB‑C 1

## Resultados y decisión

| Estado | Evidencia |
|---|---|
| Validado en simulación | Enumeración USB de velocidad completa, VID/PID, MSC 08/06/50 y BOT. INQUIRY, TEST UNIT READY, REQUEST SENSE, READ CAPACITY(10), READ(10), bloques de 512 B. Montaje MBR y superfloppy, FAT32 fragmentada, FM1CAT v1/v2, menú, botones y relectura SHA‑256. |
| Compilado para el FM1 | APP1 experimental pi32v2/r3, ABI de diagnóstico ROM, pantalla SPI1 ST7789 de 240 × 240 y salida terminal. La inicialización USB Host está bloqueada en el punto de entrada. |
| Demostrado físicamente en el FM1 | Sin resultados en esta fase. Android ya puede preparar la tarjeta del usuario a través de su propio puerto. |
| Bloqueado | Alimentación y rol de la placa, validación de la sesión ROM y entrada autónoma al explorador con el bootloader actual. |

**NO-GO para conectar el lector pasivo y arrancar el explorador desde recuperación.** Hay un bloqueo de arranque demostrado en el código de recuperación. La capacidad del puerto para alimentar un lector pasivo sigue sin demostrarse en la revisión del usuario. La falta de evidencia funda el bloqueo de seguridad eléctrico. Si una comprobación controlada verifica que el puerto no ofrece VBUS en rol anfitrión, un lector pasivo quedaría sin alimentación en esa configuración.

## USB0, placa y alimentación

| Referencia | Qué permite afirmar |
|---|---|
| SDK JieLi AC79 y notas USB0 de M‑Unbricker | El SoC ofrece PHY USB0 Full Speed y un controlador Host con transferencias de control y bulk. El bit VBUS de CON0 y el override de VBUSVALID pertenecen al controlador. El init existente activa PHY, SIE y pull-downs de datos. |
| Melodee | Documenta Host MIDI Full Speed y declara expresamente que su firmware no conduce VBUS. Su prueba con alimentación propia no demuestra el suministro de 5 V desde la placa del FM1. |
| M‑Unbricker | Documenta Host desde un FM1 en una sesión con otra unidad alimentada. Se reutiliza el controlador previamente incorporado al proyecto. El mecanismo de rescate de M‑Unbricker opera sobre la Flash del destino y queda fuera de este diagnóstico. |
| RemoteFM1 | USB MIDI/bulk de dispositivo para una aplicación anfitriona y control remoto. Publica un uso del puerto en el rol opuesto al de un lector MSC externo. |
| Revisión concreta del FM1 | Las referencias revisadas no identifican el circuito CC1/CC2, una fuente de 5 V con limitación de corriente, la topología de VBUS o la protección contra retorno. Se requiere evidencia de la revisión precisa o caracterización de laboratorio externa al conector, sin abrir el FM1. |

Fuentes revisadas. [JieLi AC79 WL82 USB](https://gitee.com/Jieli-Tech/fw-AC79_AIoT_SDK/blob/d179b4484759423312073f5fbb232501aa491047/include_lib/driver/cpu/wl82/asm/usb.h) · [M‑Unbricker, notas USB0](https://github.com/jvitkauskas/m-unbricker/blob/bd5ea96ca2fb925e1647d233373bac7e3881a098/docs/husb-notes.md) · [Melodee, USB MIDI Host](https://github.com/keremimo/melodee/blob/165d177a425944f3d30b0b669255a6ed0050c3a5/BUILDING.md#usb-midi-host) · [RemoteFM1 0.4.0](https://github.com/Quixotic7/RemoteFM1/blob/86e59756036fb7ff7151e1b3834d7a53d518e23d/README.md) · [FM‑1‑RE, USB](https://github.com/AL-255/FM-1-RE/blob/ec832f281ca165a7d5e0722985f3acb1465046d5/docs/io/06-usb.md)

Una futura sesión eléctrica autorizada requeriría un analizador USB‑C de laboratorio con detección pasiva y capacidad de comprobar roles, VBUS y corriente en ambas orientaciones. Hay que verificar el rol de fuente y el comportamiento de VBUS tanto en reposo como tras adjuntar una carga cualificada. La protección contra retorno y el presupuesto del lector requieren evidencia independiente. Una fuente externa pertenece a un ensayo de laboratorio separado, sujeto a esas comprobaciones. El candidato no mide VBUS, no declara una lectura obtenida desde el controlador como prueba eléctrica y no incorpora un enlace al hardware del propietario.

## Arranque y salida

| Contrato | Aplicación a esta fase |
|---|---|
| **APP1 desde ROM** | La imagen experimental usa entrada `0x01C30000`, board 1, tráiler APP1 formato 1 y CRC16-XMODEM. Usa el arranque con pilas privadas, validación de reloj/TMR0, preparación del panel y salida terminal de 6A. REC sale de la lista y deja USB pasivo. El regreso al firmware depende de un ciclo de corriente y requiere validación física. |
| **Carga temporal desde anfitrión** | Podría utilizar un futuro transporte físico exclusivo y estrictamente cualificado, con preflight, candidato fijado, comprobación ante cargadores residentes y parada al primer fallo. 6C admite únicamente el hash de su APP1 de diagnóstico original en simulación. No puede ejecutar este candidato. No se cambia su lista de confianza. |
| **STG2 desde recuperación** | El menú del bootloader 2.4 llama a `sd_probe(60)` y `sd_stage2(60)` por SPI1. DOWNLOAD MODE y MIDI MODE usan el FM1 como dispositivo. El APP1 de este trabajo carece del formato, dirección y ABI del STG2. |
| **Aplicación en Flash** | La entrada XIP arrancaría desde la imagen musical instalada o desde una imagen propia instalada. Es incompatible con preservar el objetivo de esta fase. Al cambiar por completo la imagen musical se pierde el código del explorador que esa imagen pudiera contener. No se ha identificado una entrada persistente por USB‑C con el bootloader intacto. |

En un ensayo futuro, el primer objetivo de ejecución sería validar el APP1 original de 6A y el regreso a DARK‑1. Antes de usar un diagnóstico USB se necesita caracterización eléctrica, plan de recuperación verificable, transporte físico aprobado y revisión del candidato exacto. APP1 cambia SRAM y estados volátiles. El archivo de esta fase queda para revisión y reproducibilidad.

[Código de recuperación de m‑bootloader 2.4-395E6CC](https://github.com/jvitkauskas/m-bootloader/blob/395e6cc469ba1d6eae9922a752d1f416ff07b69a/src/boot.c) · [Ruta SD SPI1 exacta](https://github.com/jvitkauskas/m-bootloader/blob/395e6cc469ba1d6eae9922a752d1f416ff07b69a/src/sdboot.c) · [ABI 6A](FASE-6A-ROM-ABI.md) · [Transporte 6C](FASE-6C-TRANSPORTE-ROM.md)

## Lectura y memoria

- El explorador reutiliza USB0 `husb.c`, el transporte de almacenamiento y la política CDB de MSC/BOT. Comprueba descriptores USB y sólo inicia la sesión de laboratorio tras la comprobación de alimentación. Los paquetes OUT llevan comandos BOT sin carga útil de escritura. El conjunto permitido consiste en INQUIRY, TEST UNIT READY, REQUEST SENSE, READ CAPACITY(10) y READ(10). Un reinicio BOT o clear-halt afecta al estado de comunicación.
- El lector conserva el FAT32 existente y amplía localmente la búsqueda de rutas DOS 8.3 bajo FM1. Admite hasta 4.096 sectores de directorio para encontrar archivos en las bibliotecas planas grandes. Sigue FAT de sólo lectura, detecta cadenas de directorio cíclicas y comprueba el final de cada archivo verificado. Los diseños anunciados son MBR y superfloppy FAT32, dispositivo directo Full Speed, SCSI y BOT. UASP, hubs, High Speed, exFAT y LFN quedan fuera del perfil.
- El lector FM1CAT v2 comprueba SHA‑256 del índice, recuenta páginas, recalcula la generación y compara secuencias de 64 bits. Los empates se rechazan. Puede recuperar el slot alternativo si el índice o su primera página fallan. Cada página que visita el usuario verifica su hash, número, cantidad de filas y orden por digest antes de que se dibujen sus registros. El escaneo inicial es perezoso. Una corrupción en una página posterior se informa al abrirla. Tiene un búfer de 512 B para lectura, 4 KiB para tabla de hashes y espacio para un registro en curso, una ficha y nueve rótulos visibles.
- La ruta histórica anidada se acepta sólo si deriva del SHA‑256 con el mismo algoritmo que la app. El archivo se verifica leyendo todos sus bytes y la terminación de su cadena FAT. El campo de compatibilidad de Android es `UNVERIFIED`; la comprobación de integridad no cambia ese valor. FM1CAT v1 se usa sólo en ausencia de los dos índices v2 y conserva 64 registros.
- La pantalla usa una scanline RGB565 y controles OCT±, PLAY y REC. En la ficha, PLAY verifica SHA‑256. OCT± permite recorrer el nombre largo. El parser conserva los metadatos UTF‑8 y el juego de glifos existente muestra los caracteres no ASCII mediante sustitución visual.
- Los informes de ELF y enlaces de la compilación incluyen entrada, CRC, presupuesto de RAM y pila y comprobaciones estáticas de la superficie de Flash. Se verifican los hashes del STG2 y del APP1 de 6A para detectar regresiones de las fases anteriores.

## Reproducción

```sh
make usbc-test usbc-sanitize
make TOOLCHAIN=toolchain usbc-explorer rom-diagnostic-regression
(cd build/usbc && sha256sum -c SHA256SUMS.txt)
```

La fixture versionada en `tests/fixtures/manager-alpha03/FM1` procede de `ManagerContractsTest.kotlinExportFeedsEmbeddedV1AndRecoversBackup` de la PR #20. Sus dos originales SLOT son sintéticos. Se conserva la serialización tal como se escribió por Kotlin, incluidos el orden de claves y los escapes de Android/JVM. Las matrices adicionales tienen 250, 410, 1.024 y 4.096 filas y archivos sintéticos, incluida una lectura fragmentada de 400.123 bytes. Los tests incluyen ausencia de VBUS, lector ausente, descriptor inválido, no MSC, timeout, desconexión, transferencia corta, CSW incorrecto, índice ambiguo, corrupción de página, archivo faltante y alterado y comprobaciones ASan/UBSan.

El resultado de CI incluye APP1, ELF, mapa, desensamblado, CRC, SHA‑256, resultados de pruebas y una captura de la pantalla simulada. El punto de entrada del candidato sólo admite `USB_POWER_UNKNOWN`. Ante ese estado no inicia Host ni conduce el par USB durante la sesión de diagnóstico. No hay lanzador físico incluido.
