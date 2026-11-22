# USB-C del FM-1 — evidencia de pinout y alimentación

## Regla principal

No se asigna ningún pin USB-C a `D+`, `D−`, `CC1`, `CC2` o VBUS de la placa sin fotografía/esquema/medición de la unidad concreta. El pinout del encapsulado AC791N no es un pinout de la PCB.

## Matriz de evidencia

| Señal | Evidencia actual | Confianza | Falta |
|---|---|---:|---|
| D+ / D− | `husb.c` y Melodee muestran un USB0 full-speed del AC791N | media para controlador | continuidad USB-C→SoC, ESD y mux en FM-1 |
| CC1 / CC2 | ninguna evidencia de la placa FM-1 | desconocida | resistencias, orientación, rol UFP/DFP/DRP |
| VBUS | Melodee no conduce VBUS desde el firmware host | media para software externo | fuente/switch/fusible/limitación/backfeed de la placa |
| GND | debe identificarse por esquema/foto, no asumir blindaje como único retorno | baja | plano y puntos de masa |
| ESD | no identificado | desconocida | componente, conexión y capacidad |
| D+/D− del conector | no hay fotografía del ejemplar | desconocida | trazado, pares, resistencias y test points |
| USB0 pinmux | documentación/código externo | media teórica | registro y conexión física real |

## Consecuencia para un lector MSC

Un lector USB-C no es un accesorio pasivo equivalente a una microSD. Para que A —USB-C→MSC— sea segura hacen falta:

- rol host confirmado;
- CC compatible con una fuente host;
- VBUS de 5 V con limitación de corriente y protección contra backfeed;
- tierra común y par D+/D− correcto;
- alimentación suficiente para lector/hub;
- enumeración full-speed confirmada;
- compatibilidad de clase MSC/BOT;
- procedimiento que sólo admita TUR, REQUEST SENSE, INQUIRY, READ CAPACITY(10) y READ(10).

El código existente modela parte del protocolo y la recuperación, pero no prueba esos prerrequisitos eléctricos.

## Hoja de evidencia solicitada

El propietario debe aportar fotografías antes de cualquier prueba. Deben marcarse, sin soldar:

1. VBUS del receptáculo y su ruta hacia regulador/switch.
2. CC1 y CC2 y el valor/función de cada resistencia.
3. D+/D−, ESD y puntos de test.
4. Masa y blindaje.
5. Referencia del regulador y tensión nominal.

No se debe inyectar 5 V en un pin, ni puentear CC, ni conectar un hub/lector hasta revisar esa hoja.

## Fuentes

- [Melodee fm1_usb.h](https://github.com/keremimo/melodee/blob/main/firmware/hal/fm1_usb.h)
- [Melodee USB MIDI Host](https://github.com/keremimo/melodee/blob/main/BUILDING.md#usb-midi-host)
- [`src/husb.c` de este repositorio](../../src/husb.c)
