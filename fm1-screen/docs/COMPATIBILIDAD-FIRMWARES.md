# Compatibilidad de firmwares

Los estados corresponden a FM1 SCREEN. **P** indica pendiente de comprobación física. **Físicas** indica la secuencia F3–G5. Los FPS y la latencia requieren mediciones con esta app.

| Firmware | Versión | Protocolo | Detección USB | Detección BT | Recepción gráfica | Control remoto | Etiquetas de pads | Sincronización | FPS | Latencia | Evidencias | Estado | Limitaciones |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| DARK-1 | 1.0.7 | Sin emisor identificado | P | P | Sin evidencia | P | Físicas | — | — | — | [Código](https://github.com/sparky480v/Dark-1/tree/6c0cc34677c4188575158c9ea5f53c95200be4a0) | SIN INFORMACIÓN SUFICIENTE | Confirmar versión instalada. |
| SKIFF original | 2.7.2 | Cast de eventos | P | P | — | — | Físicas | — | — | — | [Cast](https://github.com/illuminus5/skiff/blob/v2.7.2/firmware/src/cast.c) | COMPATIBILIDAD PARCIAL | La pestaña Screen reproduce el firmware en el host. |
| SKIFF + Big Screen | 2.1.1 | Big Screen V1 | P | — | P | — | Físicas | — | — | — | [Variante](https://github.com/pfkellogg/skiff/blob/big-screen/BIG_SCREEN.md) | SOPORTADO SEGÚN CÓDIGO FUENTE | Binario USB separado. |
| FRASIL | 3.4.1 | fm1-base | P | Desactivado | P | P | Físicas | P | — | — | [mirror = true](https://github.com/styvesgoupil/frasil/blob/v3.4.1/app/fm1.toml) | SOPORTADO SEGÚN CÓDIGO FUENTE | Primer candidato para LCD y panel. |
| LOOM-8 | 0.13+ | fm1-base | P | P | P | P | Físicas | P | — | — | [Mirroring original](https://charlesvestal.github.io/fm1-loom/mirror/) | SOPORTADO SEGÚN CÓDIGO FUENTE | BLE depende de la compilación. |
| POLLY | 0.6.4 | fm1-base | P | P | P | P | Físicas | P | — | — | [Prueba del autor del visor](https://github.com/pfkellogg/fm1-mirror#other-firmwares-choralroot-jiant-fm-and-skiff) | SOPORTADO SEGÚN CÓDIGO FUENTE | Validación física con otra app. |
| SLOOP + Big Screen | 2.8.5 | Big Screen V1 | P | P | P | — | Físicas | — | — | — | [Variante](https://github.com/pfkellogg/sloop-fm1/blob/big-screen/BIG_SCREEN.md) | SOPORTADO SEGÚN CÓDIGO FUENTE | Verificar binario instalado. |
| JIANT FM + Big Screen | V10.49 | Big Screen V1 | P | — | P | — | Físicas | — | — | — | [Variante](https://github.com/pfkellogg/JIANT/blob/big-screen/BIG_SCREEN.md) | SOPORTADO SEGÚN CÓDIGO FUENTE | Puede enumerarse con nombre JIANT. |
| NACHTFAHRT + Big Screen | 1.0 | Big Screen V1 | P | — | P | — | Físicas | — | — | — | [Variante](https://github.com/pfkellogg/fm1-nachtfahrt/blob/big-screen/BIG_SCREEN.md) | SOPORTADO SEGÚN CÓDIGO FUENTE | Disponibilidad de la variante por confirmar. |
| NACHTBAR | Por verificar | Sin emisor identificado | P | P | — | — | Físicas | — | — | — | [Seguimiento](https://github.com/FartyPants/fm1-nachtbar/issues/3) | SIN INFORMACIÓN SUFICIENTE | El MIDI convencional no contiene el LCD. |
| FM1 original | V15/V16 | USB MIDI convencional | P | P | — | — | Físicas | — | — | — | [FM1 Open](https://github.com/m-vave/fm1_open) | SIN INFORMACIÓN SUFICIENTE | V14 Open con Big Screen es otro binario. |
| FM1 Open + Big Screen | V14 | Big Screen V1 | P | P | P | — | Físicas | — | — | — | [Emisor](https://github.com/m-vave/fm1_open/pull/24) | SOPORTADO SEGÚN CÓDIGO FUENTE | Confirmar variante instalada. |
| CHORALROOT + Big Screen | Rama big-screen | Big Screen V1 | P | — | P | — | Físicas | — | — | — | [Variante](https://github.com/pfkellogg/ChoralRootFM1/blob/big-screen/BIG_SCREEN.md) | SOPORTADO SEGÚN CÓDIGO FUENTE | Revisar nombre de producto. |
| MELODEE + Big Screen | 1.6.1 | Big Screen V1 | P | — | P | — | Físicas | — | — | — | [Variante](https://github.com/pfkellogg/melodee/blob/big-screen/BIG_SCREEN.md) | SOPORTADO SEGÚN CÓDIGO FUENTE | Requiere la variante Big Screen. |
| FOMAJAM | Revisión 885100f | Big Screen V2 | P | P | P | — | Físicas | — | — | — | [bigscreen.h](https://github.com/konsumer/foma/tree/885100f027356014b42a1d9c7d9902eb3ac41801/bigscreen) | SOPORTADO SEGÚN CÓDIGO FUENTE | Ensayo en FM-1 pendiente. |
| OCARINA, MUTABLE DREAMS, ECHOFIELD, JAMTRACK, KRIS JANGLE, LOAF BREAKER | Por verificar | fm1-base según upstream | P | P | P | P | Físicas | P | — | — | [Listado upstream](https://github.com/pfkellogg/fm1-mirror#other-firmwares-choralroot-jiant-fm-and-skiff) | EN INVESTIGACIÓN | Confirmar versión y compilación de cada uno. |
| Resto del catálogo | Por firmware | Por firmware | P | P | P | P | Físicas | P | — | — | [Catálogo](https://github.com/Quixotic7/RemoteFM1FW/tree/b2b1d3254563dee85fbbd210087d78125145f4f7/catalog) | EN INVESTIGACIÓN | El catálogo de cores host aporta versiones y metadatos. |

Los descriptores USB, las notas y los estados de teclas no contienen los colores y coordenadas del LCD. Hace falta un emisor, acceso documentado a un framebuffer o una captura de bus. Las interfaces de actualización y depuración quedan fuera de la conexión normal. La captura del TFT se estudia en [INVESTIGACION-TFT.md](INVESTIGACION-TFT.md).
