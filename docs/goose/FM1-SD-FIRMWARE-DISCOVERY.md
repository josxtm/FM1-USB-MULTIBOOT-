# Descubrimiento de firmwares en microSD

La ruta segura sigue requiriendo `FM1/INDEX.CSV` y nombres cortos en `FM1/FW/`. El parser FAT32 existente está deliberadamente restringido a paths FM1 y no se amplió a una búsqueda recursiva que pudiera interpretar archivos arbitrarios como ejecutables.

Se investigó la autodetección conceptual: enumerar `FM1/FW`, filtrar extensiones `.BIN`, `.FWS` y `.fwsc`, calcular tamaño/SHA y generar entradas `UNVERIFIED`. No se habilita todavía porque `.FWS`/`.fwsc` no tienen una especificación pública suficiente para establecer formato, entry o compatibilidad; el nombre no autoriza ejecución.

Una futura autodetección puede ser sólo un catálogo de inspección, nunca una autorización. `INDEX.CSV` continúa siendo la fuente explícita de metadatos, compatibilidad declarada y orden del selector. USB MSC/FAT32 mantiene lectura, timeout y desconexión sin fallback silencioso a DEMO.
