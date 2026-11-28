# Compatibilidad de firmware · Fase 2

| Capacidad | fm1-base compatible | Big Screen V1 / V2 | MIDI estándar, Cast y sin emisor |
|---|---|---|---|
| Detección USB | Implementada por Android. Pendiente con FM-1. | Pendiente con cada variante. | Pendiente con cada variante. |
| MIDI estándar | Ensamblado y métricas verificados mediante test automatizado. | Pendiente de validación en hardware. | Pendiente de validación en hardware. |
| BLE MIDI | Adaptador Android implementado. LCD BLE pendiente de evidencia y prueba. | Experimental según firmware y transporte. | Depende de la variante. |
| Pantalla LCD | Parche, Fill y RGB565 verificados mediante test automatizado. | Decodificación y límites verificados mediante test automatizado. | Sin emisor de píxeles identificado en las ediciones de la tabla. |
| Actualizaciones parciales | Verificadas mediante test automatizado. | Verificadas mediante test automatizado. | Pendientes. |
| Estados de pads y botones | Recepción comprobada mediante prueba automatizada en v2. | Fuera de las tramas gráficas implementadas. | Pendientes. |
| Valores de potenciómetros | MASTER absoluto cuando llega telemetría. Los detentes relativos recibidos permiten inferir un valor de encoder. Preset y modo sin estado genérico documentado. | Pendientes. | Pendientes. |
| Control remoto | Verificado mediante test automatizado con autorización, ACK y filtro SAVE. | Pendiente de un protocolo de panel compatible. | Pendiente. |
| Identificación de firmware | Identidad USB sugerida por nombre de producto. El Hello identifica protocolo y capacidades, no garantiza la versión del firmware. | La firma identifica el protocolo gráfico. | Depende del descriptor. |
| Validación física | Pendiente. | Pendiente. | Pendiente. |

| Firmware | Versión / revisión | Evidencia y estado |
|---|---|---|
| FRASIL | 3.4.1 | fm1-base activo y BLE deshabilitado. Perfil incluido. Verificado mediante código fuente. [Configuración](https://github.com/styvesgoupil/frasil/blob/v3.4.1/app/fm1.toml) |
| POLLY | 0.6.4, `fecf93e` | LCD USB y panel declarados, BLE habilitado para MIDI. Perfil incluido. Verificado mediante código fuente. [Configuración](https://github.com/charlesvestal/fm1-polly/blob/fecf93ed5a08d294b6ee2f2bdabf37c30882930e/app/fm1.toml) |
| LOOM-8 | 0.13 y 0.18.2 | LCD USB habilitado en las dos revisiones examinadas. Perfil incluido. Verificado mediante código fuente. [0.13](https://github.com/charlesvestal/fm1-loom/blob/f9bf7dc531016fcda0d7ef111e67e856452cf574/app/fm1.toml) · [0.18.2](https://github.com/charlesvestal/fm1-loom/blob/7f774c4e24527563ef808ccd0d9cb5c2fe55cec1/app/fm1.toml) |
| LOOM-8 estándar | 0.19, `30032b4` | No compatible con LCD. La configuración conserva MIDI y desactiva el emisor. [Configuración](https://github.com/charlesvestal/fm1-loom/blob/30032b41317b4175fbd431b748f75dca2d23af06/app/fm1.toml) |
| DARK-1 | 1.0.7 | Pendiente. Perfil de referencia, con emisor de LCD aún sin evidencia. [Fuente](https://github.com/sparky480v/Dark-1/tree/6c0cc34677c4188575158c9ea5f53c95200be4a0) |
| SKIFF | 2.7.2 estándar / 2.1.1 Big Screen | Cast de eventos en estándar. Big Screen V1 habilitado en variante. Compatibilidad parcial según edición. [Cast](https://github.com/illuminus5/skiff/blob/v2.7.2/firmware/src/cast.c) · [Variante](https://github.com/pfkellogg/skiff/blob/big-screen/BIG_SCREEN.md) |
| SLOOP, JIANT FM, NACHTFAHRT | 2.8.5, V10.49, 1.0 con Big Screen | Big Screen V1 en las variantes publicadas. Verificado mediante código fuente. [SLOOP](https://github.com/pfkellogg/sloop-fm1/blob/big-screen/BIG_SCREEN.md) · [JIANT](https://github.com/pfkellogg/JIANT/blob/big-screen/BIG_SCREEN.md) · [NACHTFAHRT](https://github.com/pfkellogg/fm1-nachtfahrt/blob/big-screen/BIG_SCREEN.md) |
| MELODEE y CHORALROOT | 1.6.1 y rama big-screen | Big Screen V1 en las variantes publicadas. Verificado mediante código fuente. [MELODEE](https://github.com/pfkellogg/melodee/blob/big-screen/BIG_SCREEN.md) · [CHORALROOT](https://github.com/pfkellogg/ChoralRootFM1/blob/big-screen/BIG_SCREEN.md) |
| FOMAJAM | `885100f` | Big Screen V2. Verificado mediante código fuente. [Especificación](https://github.com/konsumer/foma/tree/885100f027356014b42a1d9c7d9902eb3ac41801/bigscreen) |
| FM1 Open y M-VAVE original | Open V14 variante Big Screen / comercial V15 y V16 | Open con Big Screen V1 según el código de la variante. LCD comercial pendiente de evidencia. [Variante](https://github.com/m-vave/fm1_open/pull/24) |
| NACHTBAR y resto del catálogo | Por edición | Pendiente. Confirmar emisor, versión, nombre USB y capacidades. [Seguimiento NACHTBAR](https://github.com/FartyPants/fm1-nachtbar/issues/3) · [Catálogo](https://thefirmwarehub.com/) |

Los perfiles incluidos usan las etiquetas físicas verificadas cuando faltan nombres de funciones por pantalla. Una identidad compatible autoriza únicamente consultas documentadas y todavía exige respuesta válida para habilitar control. Las tasas y latencias físicas están pendientes. [Matriz de referencia ampliada](docs/COMPATIBILIDAD-FIRMWARES.md).
