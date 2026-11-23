# FM1 MULTIBOOT OS — Fases USB-C 6–8

## Referencia del equipo

Firmware musical **SKIFF** (comunicado por el propietario, sin prueba física). El
m-bootloader `2.4-395E6CC` es la última identificación conocida; reconfirmar
en la sesión física. Nunca escribir Flash durante estas fases.

## Fase 6 — Telemetría diferenciada

El explorador ahora conserva resultados separados:

- `transport`: inicio USB Host, enumeración MSC y capacidad.
- `fat_status`: validez del volumen FAT32.
- `media`: resultado más reciente del catálogo o de la operación.
- `sectors_detected`: sectores realmente informados por READ CAPACITY, sólo
  mientras el transporte se mantiene operativo.

La pantalla técnica muestra los resultados sin confundir una FAT32 correcta
con la validez del catálogo ni afirmar lectura cuando falló la enumeración.

## Fase 7 — Desconexión física y protección del estado

- Durante la sesión del programa de laboratorio, verificar conexión física
  entre las consultas del teclado sin reiniciar USB Host.
- Al perder el lector: invalidar conteo, página, registros, índice V1/V2 y
  capacidad conocida; mostrar `MSC: DESCONECTADO` y estado correspondiente.
- Si un fallo ocurre durante la verificación SHA-256, invalidar el mismo
  estado y no permitir nuevos accesos a rutas antiguas.
- La operación de invalidación es idempotente y no escribe en la tarjeta.
- El usuario debe apagar/reiniciar el diagnóstico antes de volver a conectar
  otro lector: **sin reenumeración automática** ni reintentos ocultos.

## Fase 8 — Banco de regresión previo a prueba física

Los fixtures FAT32 ya presentes comprueban:

- Conteos 250, 410, 1.024 y 4.096 registros.
- Índices alternos corruptos/recuperación y archivos ausentes.
- SHA-256 real contra bytes leídos por READ(10).
- Errores de lectura y desconexión durante SHA.
- Navegación y cierre en LCD virtual.
- Separación de dos APP1 (diagnóstico pasivo y Host Lab).
- Auditoría pi32v2, memoria, llamadas y ausencia de operaciones Flash.

Se añaden aserciones sobre los cuatro estados de telemetría tras una
desconexión simulada y el renderizado del panel técnico en esa condición.

**La CI no demuestra lectura física en el FM1 ni garantiza VBUS real**. El
candidato de laboratorio sigue sujeto a cualificación eléctrica externa,
copia recuperable de firmware y un ensayo autorizado.

## Criterios de la prueba final

Un único ensayo controlado debe mostrar: arranque de APP1 en SRAM, lector USB
con VID/PID, estado MSC, capacidad, FAT32, FM1CAT v2, nombres reales,
SHA-256 correcto de archivo y salida con SKIFF funcionando.

En caso de VID/PID ausente, fallo de Host, FAT32 o desconexión, registrar
el código mostrado; no instalar firmware ni repetir saltos de ROM.
