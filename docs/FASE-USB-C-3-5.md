# FM1 MULTIBOOT OS — Fases USB-C 3, 4 y 5

## Entrega encadenada

Este PR continúa los PR #21 (explorador) y #22 (transporte ROM Android de SRAM).
Se realizan **tres fases de implementación y una validación de software** sin solicitar
ensayos intermedios sobre el FM1.

### Fase 3 — Paso de Android/ROM a USB Host de laboratorio

Se crean **dos APP1 diferentes**, ambos para RAM y sin escritura SPI NOR:

| Artefacto | Política USB Host |
|---|---|
| `FM1_USB_C_SD_READER_READONLY.bin` | SIEMPRE bloqueado con `USB_POWER_UNKNOWN` |
| `FM1_USB_C_SD_READER_LAB.bin` | Puede entrar en Host tras la secuencia física de laboratorio |

El segundo NO se ejecuta automáticamente al cargarlo. Primero desactiva el
dispositivo USB y muestra instrucciones en la LCD. El operador **debe retirar
la conexión Android** y conectar un lector que haya sido cualificado por pruebas
eléctricas externas (CC1/CC2, VBUS, tensión y corriente bajo carga, protección
contra retorno y polaridad). Si no existe esa evidencia, **no se autoriza la
prueba**, aunque el programa compile.

La secuencia para el APP1 de laboratorio es:

1. Encender en `DOWNLOAD MODE` y cargar el APP1 exacto mediante el transporte
   RAM validado previamente, sin acceder a Flash.
2. El FM1 muestra `DESCONEXION ANDROID / ADAPTADOR SD EXTERNO`.
3. Desconectar Android y conectar exclusivamente el lector previamente
   caracterizado. Confirmar que no hay ningún anfitrión USB conectado al
   mismo puerto.
4. Soltar todas las teclas durante al menos 200 ms.
5. Mantener **OCT+ y PLAY juntos durante 2 segundos**. Si se interrumpe,
   comienza de nuevo. Si pasan 55 segundos sin activar, se detiene.
6. Soltar ambas teclas. Sólo después se inicia la sesión USB Host.
7. Ante REC, error del temporizador o retirada del lector, abortar,
   no repetir automáticamente ni seleccionar operaciones Flash.

**Un gesto en teclas no prueba que exista alimentación eléctrica**.
El bit `VBUSVALID` del controlador USB no demuestra los 5 V reales del conector.
La instrumentación y cualificación física quedan fuera del software.

### Fase 4 — Lectura MSC/FAT32 y pantalla de diagnóstico

Se reutilizan `husb`, `storage`, `fat32`, `fm1cat_v2` y
`usbc_explorer` existentes: enumeración Full Speed USB MSC 08/06/50,
SCSI INQUIRY/TUR/READ CAPACITY/READ(10), sectores de 512 B, FAT32,
`FM1/FW/`, `FM1/CATALOG/INDEXA.V2` e `INDEXB.V2`.

La pantalla técnica de 240x240 visualiza:

- VID/PID del lector USB.
- Confirmación MSC/BOT.
- Número de sectores accesibles.
- Estado FAT32.
- FM1CAT v1/v2 y cantidad de firmwares.
- Advertencia permanente de que no hay escritura Flash.

PLAY abre el explorador; OCT± navega; PLAY abre la ficha y verifica SHA-256;
REC sale. En la sesión de laboratorio se puede volver a la pantalla técnica
con **OCT- + PLAY** (mantener ~0,9 s). Los nombres largos se paginan.

La lectura es de **solo lectura**, con buffers y ventanas existentes.
No hay SCSI WRITE, borrado de tarjeta, firmware FLASH, actualización MIDI,
ni instalación de firmware musical.

### Fase 5 — Construcción, regresiones y preparación de prueba final

La CI debe:

- Construir por separado los APP1 seguro y de laboratorio, en formato
  pi32v2 con CRC16, APP1 y dirección de entrada correcta.
- Ejecutar pruebas de secuencia de armado, tecla retenida, liberación,
  cancelación y agotamiento de tiempo.
- Volver a ejecutar la matriz MSC/FAT32/FM1CAT (250, 410, 1024, 4096),
  corrupción de índice, desconexión y archivos fragmentados.
- Auditar memoria, símbolo de arranque, BSS, stack, ausencia de operaciones
  Flash y SHA-256 de los archivos.
- Construir Android RAM Loader por separado. Su lista de candidatos no debe
  aceptar un binario cualquiera.
- Mantener FM1 Manager To USB sin cambios funcionales ni retrocesos.

### Prueba física final: criterios de aceptación

**No programar Flash ni fusionar PR por superar CI.**

Sólo después de haber caracterizado la alimentación y de disponer de copia
recuperable de DARK-1 se ejecutará UNA sesión física de laboratorio.

| Etapa | Evidencia de aprobación |
|---|---|
| ROM fría | Transición desde m-bootloader 2.4 comprobada en el FM1 real |
| APP1 | Imagen íntegra, programa en LCD, ausencia de escrituras Flash |
| Cableado | No hay anfitrión Android conectado al activar Host; lector cualificado |
| Host | VID/PID del lector real y protocolo MSC 08/06/50 |
| Almacenamiento | Lectura de capacidad, FAT32 válida |
| Catálogo | Contador y nombres reales de FM1CAT v2 |
| Integridad | SHA-256 de un firmware del directorio `FM1/FW` verificado |
| Salida | Apagado completo, DARK-1 y m-bootloader intactos |

**Resultado físico por defecto: NO VERIFICADO.** Una compilación no prueba
alimentación USB-C, lectura física ni viabilidad del arranque autónomo.

### Bloqueo de producto aún vigente

El m-bootloader 2.4 expone `SD CARD` mediante **SPI1 interno**, no por USB-C.
Aunque funcione esta prueba de RAM, todavía no existe un explorador persistente
que sobreviva al reemplazo del firmware musical sin modificar el bootloader.
No se implementa el instalador Flash hasta resolver el mecanismo de recuperación.

El objetivo de este PR es terminar las etapas de **software** por adelantado
y reservar exclusivamente el ensayo físico para el propietario.
