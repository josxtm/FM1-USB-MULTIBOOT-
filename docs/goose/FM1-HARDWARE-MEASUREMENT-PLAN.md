# FM1 — plan de mediciones hardware

Este plan está condicionado a la revisión de fotografías por el propietario. La etapa siguiente nunca se ejecuta automáticamente al completar la anterior.

## Etapa 1 — documentación sin modificaciones

**Puntos:** no tocar pads ni conectores.

**Instrumento/configuración:** cámara macro, iluminación difusa, escala milimétrica; unidad apagada y batería sin desconectar salvo que el propietario conozca el procedimiento seguro.

**Registrar:** ambas caras, serigrafía, revisión, USB-C, J12, AC791N, reguladores, ESD, batería, audio-jacks y cualquier test point. Guardar originales sin compresión y una copia anotada.

**Esperado:** identificar referencias, no afirmar nets.

**Parada:** si hay batería hinchada, corrosión, daño mecánico o no se puede fotografiar sin desmontaje seguro.

## Etapa 2 — continuidad y resistencia, sin alimentación

Sólo tras identificar puntos inequívocos y desconectar toda fuente, incluyendo batería y USB. Descargar/esperar según procedimiento del propietario; no cortocircuitar condensadores.

| Medida | Puntos | Instrumento | Resultado esperado | Incertidumbre |
|---|---|---|---|---|
| continuidad de masa | blindaje USB-C↔GND identificado | multímetro, buzzer y luego ohmios | conexión de baja resistencia | un buzzer no prueba capacidad de corriente |
| VBUS no corto | VBUS↔GND | ohmímetro, baja tensión de prueba | no corto franco; lectura puede cargar condensadores | no usar sólo un valor instantáneo |
| CC | CC1/CC2↔GND/VBUS | ohmímetro | valores coherentes con resistencias visibles | no inferir rol si no se ve red completa |
| USB diferencial | D+ / D− a ESD y SoC sólo si pads identificados | continuidad con corriente limitada | rutas separadas, sin corto D+↔D− | ESD/series pueden interrumpir lectura |
| J12 | cada línea J12 lado a lado y con LCD desconectado | continuidad | correspondencia con m-card sólo como hipótesis | no aplicar tensión |
| alimentación SPI | J12 línea 7↔GND | resistencia | no corto evidente | no demuestra regulación bajo carga |
| cortos de bus | CLK/MOSI/MISO/CS entre sí y a GND | ohmímetro | no corto franco | pull-ups e ICs alteran lecturas |

**Parada:** cualquier corto franco, lectura incompatible con el esquema/foto, continuidad inesperada a batería o necesidad de raspar máscara/soldar.

## Etapa 3 — medición alimentada, sólo con autorización posterior

No autorizada por este documento. Sólo podría definirse después de completar etapas 1–2 y revisar el circuito.

Requisitos mínimos: fuente limitada o alimentación original protegida, osciloscopio con sondas adecuadas, multímetro, monitor de corriente, protección ESD y plan de recuperación. No conectar lector SD, hub ni USB-C accesorio durante la primera medición.

Posibles medidas futuras, si los puntos son confirmados:

- tensión de VBUS en reposo y durante arranque;
- tensión de 3,3 V del bus J12 bajo carga conocida y limitada;
- estados CC1/CC2 sin forzar niveles;
- actividad D+/D− únicamente tras aprobar rol y protección;
- actividad SPI1 del LCD sin alterar CS.

**Parada inmediata:** corriente anormal, calentamiento, brownout, reset, pérdida de USB MIDI/recuperación, backfeed, CC inesperado o señales fuera de niveles del componente.

## Protocolo de registro

Cada fila debe guardar fecha, revisión de PCB, punto exacto/foto, instrumento y rango, valor bruto, repetición, incertidumbre y condición de parada. Una medida no convierte una hipótesis de netlist en un hecho permanente: debe reproducirse y correlacionarse con el esquema.
