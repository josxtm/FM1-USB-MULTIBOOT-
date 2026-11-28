# Perfiles de firmware JSON

Importa o exporta desde **Conexión › Firmware**. Los ejemplos viven en assets/profiles. Importar el mismo ID actualiza el registro local. El límite por documento es 64 KiB.

| Campo | Formato |
|---|---|
| schema | Entero 1 |
| id | Hasta 48 caracteres ASCII minúsculos, números, guion o guion bajo. Empieza con letra o número. |
| name, version | Cadenas de hasta 40 y 64 caracteres. |
| protocol | AUTO, BASE, BIG1 o BIG2. |
| resolution, pixels | [240,240] y RGB565. |
| deviceNames | Hasta 32 nombres de producto. Comparación exacta salvo mayúsculas. |
| pads | 27 entradas en orden cromático F3–G5. |
| buttons | FX, SEL, ENV, LFO, EDIT, GLO, HOME, SAVE, ARP, SEQ, PLAY, REC, OCT−, OCT+. |
| knobs | SELECT, ALGORITHM, PRESETS, K1, K2, K3, K4, MASTER. |
| evidence, source, limits | Cadenas de estado, fuente y límites. |

Cada leyenda admite hasta 18 caracteres o null para conservar el nombre físico. Las dos teclas OCT conservan su denominación. El mapa de operaciones permitido pertenece al decodificador. Un perfil proporciona datos y etiquetas.
