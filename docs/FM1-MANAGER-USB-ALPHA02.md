# FM1 Manager To USB alpha02

## Correcciones de la Fase B

La única carpeta de salida de originales es `FM1/FW`. El prefijo de ocho dígitos hexadecimales del SHA‑256 en mayúsculas forma el nombre. Las extensiones FWS, UFW y BIN representan bytes originales. El lector debe comparar el hash completo. Una colisión de nombre se rechaza antes de sobrescribir. Se conserva el autor en los registros nuevos. Los antiguos que carezcan del campo siguen siendo legibles.

La reorganización es una operación explícita de **MicroSD → Reorganizar en FW**. Lee las rutas anidadas del índice vigente, valida tamaño y SHA‑256, copia sin sobrescribir y verifica el conjunto plano antes de publicar el slot alternativo. El índice anterior permanece como respaldo y sus originales no se retiran. Una nueva ejecución puede aprovechar copias planas completas ya escritas. La aplicación carece de limpieza de los originales anteriores. Una eventual limpieza manual requiere comprobar también qué archivos siguen referenciados por el índice de respaldo y consentir la eliminación.

La descarga exige un Release público, procedencia coherente, un activo con tamaño y SHA‑256 y licencia explícita reconocida. En compilaciones comunitarias se exige además un build exitoso y autorización de redistribución en la colección. La apertura de un URL se comprueba con HEAD sin autenticación, licencia GitHub implícita ni acceso privado. HTTP 404/410 indica retirada. 451 marca bloqueo de disponibilidad. 401/403 señala acceso por comprobar y 429 o 5xx indican una condición temporal. Durante la descarga se comprueban de nuevo tamaño, SHA‑256 y formato.

Sobre los 415 hashes anteriores, 410 pasaron los criterios, distribuidos en 409 fichas. Los cinco excluidos fueron Amenity, Berlinan, DX747, RemoteFM1 y SQ80. Cuatro carecían de concordancia suficiente entre las fuentes de licencia y DX747 declara una excepción específica del SDK que requiere revisión. En la pasada de HEAD de esta instantánea, los 410 candidatos admitidos respondieron correctamente. Las 504 fichas fuera del filtro se conservan para consulta. Las simulaciones prueban respuestas de servidor adversas y no publican paquetes reales.

## Contrato para el explorador futuro

Leer el slot válido de mayor secuencia entre `FM1/CATALOG/INDEXA.V2` y `INDEXB.V2`. Verificar el SHA‑256 del cuerpo, la generación y el SHA de cada página. Cada registro declara el nombre original, versión, autor si se conoce, tamaño, formato, SHA‑256 completo y ruta física. Alpha02 genera `FW/<8 HEX>.<FWS|UFW|BIN>`. En tarjetas migradas, el respaldo puede seguir apuntando a la estructura anidada de alpha01. El lector futuro debe validar rutas y hashes y conservar un límite de 32 registros por página y 4.096 por índice. FM1CAT v1 mantiene sus 64 entradas, el formato `INDEX.CSV` y los nombres 8.3 que acepta el selector actual.

FM1CAT v2 aún carece de lector embebido. La ruta autónoma del FM1 por USB‑C requiere validación de CC/VBUS, USB Host MSC y una entrada persistente al explorador. Las pruebas Android comprueban almacenamiento de tablet y estructura de tarjeta.
