# Fase 5Y · Acceso al selector desde recuperación y microSD

## Recuperación del trabajo

La rama parte de `388a3737f74b5ee5cf56946b442e8d02371613ab`, cabecera de la PR #9. En las ramas remotas, las copias del proyecto y los archivos compartidos disponibles no apareció trabajo 5Y de Goose ni `virtual_slotfile`. La copia privada de Termux no estaba montada en este entorno. Se añadieron los componentes pendientes sobre FM1CAT, FAT32, MSC/BOT, selector textual, política de regiones y modelos de recuperación ya existentes. [PR #9](https://github.com/josxtm/FM1-USB-MULTIBOOT-/pull/9)

## Punto de entrada que sobrevive al cambio de firmware

El FM‑1 del propietario ya mostró **2.4-395E6CC** y volvió a DARK-1 con **BOOT APP**. Ese antecedente demuestra el menú preaplicación en su unidad. El código exacto del tag `v2.4` evalúa PLAY de arranque antes de invocar la aplicación XIP. Dentro de recuperación, `sd_probe()` añade **SD CARD** sólo cuando una tarjeta responde por SPI1. El menú carga `/MBOOT/MENU.BIN` como STG2 a `0x01C28000` y pasa el reloj LSB en MHz. Puede volver al menú si el stage-2 retorna respetando su RAM. El sondeo no ocurre durante el arranque normal. [Evidencia física previa](../PHYSICAL-SD-DIAGNOSTIC-PLAN.md) · [Código v2.4](https://github.com/jvitkauskas/m-bootloader/blob/395e6cc469ba1d6eae9922a752d1f416ff07b69a/src/boot.c) · [Contrato STG2](https://github.com/jvitkauskas/m-bootloader/blob/395e6cc469ba1d6eae9922a752d1f416ff07b69a/docs/SPEC.md#115-sd-card-stage-2)

La arquitectura propuesta conserva el menú de m‑bootloader como punto de entrada persistente. El selector se cargaría desde tarjeta al entrar en él. Tras una futura instalación autorizada y un reinicio normal, el bootloader arrancaría el firmware musical de Flash. El acceso al selector volvería a depender del punto de entrada **SD CARD**, incluso si la aplicación instalada fuese otra. Esta ruta sigue condicionada a una interfaz SPI física y a un stage-2 compatible que consuma `FM1/INDEX.CSV`. La Fase 5Y comprueba el contrato de entrada en software y conserva el selector textual existente. No se genera una imagen ejecutable de instalación.

| Ruta | Evidencia y decisión |
|---|---|
| Recuperación → SD CARD → STG2 SPI1 | Implementación documentada en el commit exacto instalado. El proceso propuesto conserva m‑bootloader. Falta comprobar en la unidad una interfaz SPI compatible y el encadenamiento al selector propio. |
| `m-apps/picker` | Referencia de la cadena `MENU.BIN` → `LAUNCH.BIN` → `BOOT.APP`. El repositorio depende del submódulo m‑bootloader `bdfda22`, distinto de `395e6cc`. Sirve para estudiar el contrato, sin atribuir compatibilidad a la unidad. |
| `m-apps/slots` | Referencia `SLOT` v1, copia previa, estado pendiente, primer sector invalidado al principio y restaurado al final, verificación y reinicio. Su alcance FM‑1 llega a `0xFF000` y puede incluir ajustes. La autorización de nuestra unidad exige un mapa propio de zonas protegidas. |
| Recuperación → DOWNLOAD MODE o MIDI MODE | Entrada disponible físicamente. Requiere un host USB para recibir comandos o datos. No proporciona una biblioteca autónoma. |
| Recuperación → lector USB-C MSC | El tag `v2.4` no implementa esa ruta en su menú. Un lector pasivo tampoco confirma rol Host ni VBUS en la placa. No se puede resolver como opción de catálogo sin una entrada de ejecución y evidencia eléctrica. |

Se revisó `m-apps` en `748bf0036b784ac38aa4c765c882fd7955e51332`. Su propia documentación describe pruebas de distintas apps y placas. No es evidencia de `picker` y `slots` sobre la combinación exacta del propietario. [picker](https://github.com/jvitkauskas/m-apps/tree/748bf0036b784ac38aa4c765c882fd7955e51332/apps/picker) · [slots](https://github.com/jvitkauskas/m-apps/tree/748bf0036b784ac38aa4c765c882fd7955e51332/apps/slots)

## Viabilidad de microSD

El m-card `ec63784` intercala una placa interna entre el LCD y J12. Comparte SPI1 y alimentación de 3,3 V. Su README aún marca la PCB como prototipo. El diseño requiere acceso al interior y queda como alternativa de investigación. Las pruebas del software SPI en otras unidades no prueban esa PCB en este FM‑1. [m-card](https://github.com/jvitkauskas/m-card/tree/ec63784fe94006296ad25bdeb5d32bf7afa834d1)

Melodee documenta USB MIDI Host a velocidad completa y dice explícitamente que su firmware no conduce VBUS. También separa la negociación USB‑C del soporte del controlador. M‑Unbricker aporta evidencia externa de comunicación USB entre dos instrumentos encendidos. Ninguno demuestra que el FM‑1 pueda alimentar el lector del propietario ni leerlo en recuperación. [Melodee, USB MIDI Host](https://github.com/keremimo/melodee/blob/165d177a425944f3d30b0b669255a6ed0050c3a5/BUILDING.md#usb-midi-host) · [M‑Unbricker](https://github.com/jvitkauskas/m-unbricker/tree/bd5ea96ca2fb925e1647d233373bac7e3881a098)

Para reconsiderar la vía USB‑C hacen falta dos evidencias independientes. Primero, confirmar en la revisión de placa el rol de datos, CC y la alimentación de 5 V con limitación de corriente y protección contra retorno. Una medición requiere equipo apropiado y autorización específica. Segundo, demostrar una entrada desde recuperación capaz de iniciar Host MSC de solo lectura. El volumen `vfat` visto en la tablet no distingue FAT16 de FAT32 ni demuestra acceso desde el FM‑1. El criterio de almacenamiento es lectura reproducible del BPB FAT32, `FM1/INDEX.CSV` y el SHA‑256 de un archivo de prueba sin escrituras. [Requisitos eléctricos anteriores](USB-C-HOST-REQUIREMENTS.md)

## Instalador virtual

`virtual_slotfile.py` mantiene un perfil de 1 MiB creado por el simulador y un diario en archivos regulares de host. `FM1/INDEX.CSV` conserva FM1CAT v1 y las rutas `FM1/FW/<ID>.BIN`. El prototipo interpreta `.BIN` como contenedor `SLOT` únicamente si validan cabecera, CRC32 de cabecera y datos, bitmap, placa, JEDEC, SHA-256 del catálogo y el marcador de fixture sintético. El mapa de prueba sólo autoriza `[0x4000, 0x10000)`. Los sectores ausentes del bitmap se interpretan como borrados únicamente dentro de esa ventana de prueba. Los mapas reales y los paquetes `APP1`, `FWSC` y binarios ordinarios no están autorizados para instalar.

La simulación separa selección, confirmación y modificación. Antes de modificar el archivo virtual, crea y relee un backup de 1 MiB identificado por SHA‑256 y persiste el diario. Primero invalida el sector inicial de la ventana sintética y lo programa al final. Después verifica la imagen completa y confirma el estado. Una interrupción con un diario pendiente bloquea otra instalación y permite restaurar el backup verificado con una instancia nueva, incluso si el catálogo desaparece. Si falta el backup, cambia su hash o no coinciden los bytes protegidos, el estado exige recuperación y permanece intacto. El protocolo usa `fsync` y reemplazos atómicos de archivos del host. Las pruebas ejercitan sus límites de persistencia y no caracterizan pérdidas de energía en la microSD o en la Flash física de un solo banco.

```sh
make virtual-slotfile-test virtual-slotfile-demo
python3 -m unittest discover -s tests -p '*test.py'
python3 scripts/virtual_slotfile.py selector usb-c --menu-open
python3 scripts/virtual_slotfile.py selector m-card-spi --menu-open
```

La demostración produce `build/virtual-slotfile-trace.json`. Confirma A, interrumpe B, reabre los archivos, recupera A y finalmente confirma B. Empaqueta el mismo catálogo y los mismos dos archivos en un FAT32 fragmentado. Los lee con los adaptadores FAT32 y MSC/BOT existentes. El selector textual verifica la entrada B y mantiene `MB_LAUNCH_BLOCKED`. No circula una orden de programación a través del núcleo C.

## Resultados y bloqueos

| Estado | Evidencia |
|---|---|
| Probado previamente en hardware del propietario | Menú de m‑bootloader 2.4-395E6CC y retorno desde **BOOT APP** a DARK‑1, según el informe archivado. |
| Comprobado en código y simulador del upstream | Pruebas locales del tag v2.4 sobre FAT32 generado y entorno NOR simulado. `sdboot-test` pasó y `boot-test-fm1` ejecutó 13 escenarios y 89 comprobaciones sin fallos. Los grupos con imágenes stock privadas se omitieron explícitamente. `picker_test.py` y `slots_test.py` de `m-apps` pasaron en host. El fixture upstream de `slots` simula SMK‑37. |
| Implementado y probado en esta rama | 11 pruebas nuevas, 30 Python acumuladas, regresión del selector textual y de FM1CAT. Demo compartida de dos entradas mediante FAT32 y MSC/BOT simulado. Ocho puntos de interrupción, rollback con nueva instancia y conservación de bytes protegidos. |
| Pendiente de hardware | `SD CARD` y selector después de sustituir la aplicación, acceso autónomo al `INDEX.CSV` en la unidad, verificación eléctrica del USB-C, copia externa validada y recuperación real. |

Se conserva la arquitectura `microSD → selector → instalación controlada en Flash → reinicio → firmware elegido`. El simulador realiza esa secuencia de extremo a extremo con archivos. La instalación física sigue bloqueada hasta demostrar el acceso autónomo al selector, validar la lectura de tarjeta, verificar una copia externa propia del FM‑1 y acreditar las regiones escribibles y la recuperación. La unidad no ha recibido cambios durante esta fase.
