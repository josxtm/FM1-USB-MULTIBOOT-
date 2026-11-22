# FM1CAT v1 — conformidad final C99/Android

El contrato no cambia. Ambos parsers aceptan exactamente ID `[A-Z0-9_]{1,8}`, LABEL `[A-Za-z0-9 _.-]{1,24}`, VERSION `[A-Za-z0-9._-]{1,16}`, FILE `ID.BIN` o `ID.FWS`, SIZE decimal 1..4 MiB, SHA-256 en minúsculas de 64 dígitos, COMPAT `UNVERIFIED`, máximo 64 entradas y máximo 24.576 bytes.

El parser C normaliza CRLF a LF, requiere las dos cabeceras exactas y una terminación LF para cada registro; rechaza líneas vacías, campos extra, controles, bytes no ASCII, duplicados, truncamiento de campos, overflow decimal, traversal y datos después del límite. Android consume los mismos fixtures en `FirmwareCatalogConformanceTest`; la matriz compartida contiene vacío, válido, CRLF, path inválido y duplicado. Las pruebas de núcleo cubren además 64 entradas, SHA, exceso, desconexión y navegación.

La ausencia de salto final no forma parte del exportador válido y se rechaza en C; Kotlin aplica la política de líneas documentada por su decoder. Esta decisión está explicitada porque el formato exige registros terminados y evita que una exportación truncada parezca válida. La compatibilidad automática sigue siendo `UNVERIFIED`, separada de integridad y presencia.

La prueba FAT32 usa el mismo contrato y pasa el texto del archivo real, no una constante embebida en la demostración integrada. No se afirma compatibilidad total de ejecución: ninguna entrada habilita lanzamiento.
