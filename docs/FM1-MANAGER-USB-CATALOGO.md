# Descubrimiento y catálogo FM1 Manager To USB

## Inventario de la instantánea incluida

Datos de The Firmware Hub del **23 de noviembre de 2026**, normalizados con repositorios y Releases de autores, FM1 Editor y la colección de 2bbb.

| Estado de la ficha | Fichas |
|---|---:|
| Publicación del autor con activo GitHub | 280 |
| Compilación comunitaria publicada | 129 |
| Receta de compilación conocida | 133 |
| Sin descarga directa comprobada | 103 |
| Licencia o autorización sin acreditar | 85 |
| Distribución restringida | 1 |
| Repositorio con paquete o árbol sin confirmar para el FM‑1 | 40 |
| Fork sin paquete independiente verificado | 113 |
| Herramienta | 28 |
| Otro dispositivo | 1 |
| **Total** | **913** |

El manifiesto ofrece **410 activos con SHA‑256 distintos**. Hay variantes y, ocasionalmente, más de un activo asociado a una ficha. Las cifras miden procedencia y disponibilidad publicada de archivos, con clasificación de variantes por separado. La comprobación íntegra de bytes y formato se realiza en cada descarga. La selección masiva incluye los activos de cada ficha y la ficha enlaza al historial de versiones del autor. La caché y el catálogo usan SHA‑256 como clave de contenido. Un paquete actualizado con un hash distinto se añade como versión adicional.

## Cómo se reúne la evidencia

- **The Firmware Hub** define identidad, categorías, versiones, nombres, enlaces y metadatos. Incluye variantes y aporta enlaces a capturas y demos de los autores. La compilación de metadatos se usa bajo CC BY 4.0, con transformación y crédito visible. Las imágenes siguen atribuidas a los autores y se enlazan cuando la fuente proporciona una. La tarea automática lee `catalog.json` una vez por ejecución diaria programada y la APK no consulta el hub para refrescarlo.
- **Autores y GitHub Releases**. Para cada ficha del hub se busca el ID público de activo o el hash idéntico. Se toma tamaño, SHA‑256 y URL de GitHub. Los paquetes se descargan del Release de origen. Algunos instaladores web esperan una conexión para iniciar transferencias. Los que tienen publicación pública comprobada siguen siendo accesibles para biblioteca sin esa conexión. Los demás conservan su ficha como sin descarga directa o restringida según evidencia. El gestor carece de sesión USB, acceso al Web MIDI e importación de identidades del FM1.
- **2bbb/fm1-firmware-collection**. Aporta candidatos, recetas y commits. Los activos de terceros requieren publicación en Releases, coincidencia con un build exitoso y metadatos de licencia compatibles. La aplicación indica compilación comunitaria y hardware sin probar. Se descartan coincidencias de repositorio/commit y hashes duplicados. Los candidatos sin verificación se conservan como tales.
- **FM1 Editor**. Sirve para corroborar enlaces a repositorios. Sus textos e imágenes no se copian en la instantánea. **M‑VAVE FM1 Open V14** publica un kit y un Workbench HTML. Se registra como herramienta de desarrollo, fuera del recuento de firmwares disponibles.

[The Firmware Hub, términos del catálogo](https://thefirmwarehub.com/kb/#catalog) · [FM1 Editor](https://fm1-editor.com/firmware/) · [Colección 2bbb](https://github.com/2bbb/fm1-firmware-collection) · [M‑VAVE V14](https://github.com/m-vave/fm1_open/releases/tag/V14)

Se descargaron siete publicaciones como muestra de comprobación, cuatro paquetes de autores y tres compilaciones comunitarias. Coincidieron tamaño y SHA‑256, y el analizador Kotlin comprobó la identidad FM‑1 y los CRC16. La muestra incluyó DARK‑1, SLOOP y dos variantes de Hortator. Los binarios de esta comprobación quedaron fuera del repositorio y de la APK.

Para producir una instantánea sin recolectar desde cada tablet, el workflow `fm1-discovery-refresh.yml` ejecuta `scripts/update_manager_catalog.py` y publica un JSON con su SHA‑256. El trabajo programado de GitHub opera desde la rama predeterminada cuando el workflow esté allí. Esta fase vive en su PR en borrador. Mientras tanto, el regenerador puede ejecutarse con `gh` y un directorio de instantánea local. Reutilizar ese directorio evita repetir la descarga ese día. La APK inicia con la copia incluida y actualiza al abrir un nuevo manifiesto validado mediante **Cargar manifiesto**.

```sh
python3 scripts/update_manager_catalog.py --snapshot-dir /ruta/fm1-dia --output discovery.json
```

## FM1CAT v2 para almacenamiento

- El archivo raíz de compatibilidad `FM1/INDEX.CSV` sigue v1. Los índices v2 son `FM1/CATALOG/INDEXA.V2` e `INDEXB.V2`. Cada uno contiene `FM1CAT|2\n`, el SHA‑256 hexadecimal del cuerpo más LF y un objeto JSON UTF‑8. El cuerpo tiene una secuencia, entre 1 y 4.096 registros, identificador de generación y la tabla de SHA‑256 de páginas.
- Las páginas se guardan en `CATALOG/<primeros ocho>/<últimos ocho>/P0000001.JSN`. La generación es el prefijo de 16 caracteres del SHA‑256 de la concatenación ASCII de los hashes de páginas. Cada página empieza por `FM1CAT|2|PAGE|n\n`, tiene hasta 32 registros JSON por línea, termina en LF y está acotada a 128 KiB. El índice está acotado a 24 KiB. Un lector independiente puede comprobar páginas secuencialmente y conservar sólo el rango que muestra.
- Cada registro tiene proyecto, nombre, versión, nombre original, tamaño, SHA‑256 completo, tipo, identidad, CRC comprobados, regiones declaradas, fuente y `compat=UNVERIFIED`. El hash identifica el contenido y los registros se ordenan por hash. Un mismo hash conserva una sola copia. Distintos hashes de un proyecto pueden convivir.
- Los originales nuevos se guardan directamente en `FW/HHHHHHHH.EXT`. Los primeros ocho caracteres del SHA-256 en mayúsculas forman el nombre. Los registros v2 anteriores con ruta `FW/HH/HHHHHHHH/HHHHHHHH.EXT` se pueden leer y migrar. Un v1 nuevo puede compartir el mismo archivo plano. El hash completo del registro detecta una colisión del prefijo y la copia se rechaza. Las extensiones cortas son FWS, UFW y BIN. No representan una conversión. Los nombres y extensiones usan 8.3.
- Se verifican los originales y el hash de cada página antes de escribir en el slot de índice inactivo. Se relee el slot completo. El lector elige la secuencia válida más alta y cae al otro slot si la nueva publicación está incompleta. Los archivos ya referenciados y las versiones anteriores se conservan. Las generaciones no referenciadas no se eliminan automáticamente.
- El verificador Python `scripts/verify_manager_card.py` es independiente del serializador Android. El firmware embebido actual lee v1. Las pruebas de v2 son de software y no acreditan lectura embebida ni instalación.

[Formato FM1CAT v1](FM1CAT-v1.md) · [Guía de la aplicación y de la microSD](FM1-MANAGER-USB.md)
