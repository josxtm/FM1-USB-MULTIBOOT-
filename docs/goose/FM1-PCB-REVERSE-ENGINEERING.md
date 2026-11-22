# FM1 — ingeniería inversa de PCB

## Objetivo y límites

Este documento separa hechos de hipótesis. No existe aquí una fotografía de la placa del propietario ni un esquema de producción del FM-1; por tanto no se asignan pines de un conector USB-C a señales del AC791N por analogía.

## Evidencia externa disponible

### M-VAVE FM-1 / m-card

El repositorio público [jvitkauskas/m-card](https://github.com/jvitkauskas/m-card) describe una placa de 30 × 25,2 mm que se instala entre el LCD y la placa principal usando J12 y dos FFC de 10 vías. Su README publica el siguiente tap del bus LCD:

| Línea J12 | Señal documentada | Uso m-card |
|---:|---|---|
| 1, 10 | GND | masa |
| 2 | MISO, compartido con LCD D/C | DO/DAT0 mediante 220 Ω |
| 3 | LCD CS | inversor para CS de tarjeta |
| 4 | CLK | CLK mediante 470 Ω |
| 5 | MOSI | DI/CMD mediante 470 Ω |
| 7 | 3,3 V | alimentación con 10 µF + 100 nF |

Las líneas DAT1/DAT2 llevan pull-up de 47 kΩ. El diseño declara que no hay card-detect. La propia fuente dice **untested prototype**: el ajuste mecánico fue comprobado con una plantilla, pero no se había fabricado/probado una placa en la evidencia consultada.

### AC791N / USB0

El árbol público de Melodee expone registros USB0 alrededor de `0x11800`, `0x11804` y `0x51000`, y una secuencia de host full-speed con pull-downs. Eso es evidencia de controlador/documentación y no de la PCB FM-1. El firmware de Melodee indica que el host no conduce VBUS.

### Estado de la PCB real

| Elemento | Estado |
|---|---|
| Revisión exacta de placa | desconocida |
| Conector y cableado USB-C | no documentado en este repositorio |
| CC1/CC2 y resistencias | desconocido |
| VBUS y reguladores | desconocido |
| ESD/backfeed | desconocido |
| Test points | desconocidos |
| J12 y SPI1 | plausibles por m-card, no medidos en esta unidad |
| Pinmux USB0/SPI1 | código/documentación externa; no validado físicamente |

## Información que debe proporcionar el propietario

1. Fotografías macro, nítidas y perpendiculares de ambas caras.
2. Foto general con escala y conectores visibles.
3. Referencia serigrafiada, revisión y fecha/código de PCB.
4. Fotografías del USB-C, J12, batería, reguladores y componentes próximos.
5. Identificación legible del AC791N y de cualquier ESD/power switch.
6. Sin retirar blindajes, batería o componentes y sin aplicar sondas todavía.

Sólo después de comparar fotografías con la documentación se puede dibujar un netlist candidato. Ese netlist debe quedar marcado como hipótesis hasta confirmarlo con continuidad sin alimentación.
