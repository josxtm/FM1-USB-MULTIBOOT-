# FM1CAT v1 — catálogo de microSD y exportación Android

Estado: biblioteca experimental. No es firmware instalable ni prueba compatibilidad.

## Árbol de la microSD

Se elige mediante Android Storage Access Framework una carpeta FM1 existente dentro de la microSD, no la raíz.

    FM1/
      INDEX.CSV
      FW/
        DARK1.BIN
        FACTORY.FWS

- BIN: copia exacta del archivo fuente .bin.
- FWS: copia exacta del contenedor original .fwsc; el nombre corto NO convierte su formato.
- INDEX.CSV: índice ASCII de hasta 24.576 bytes.
- El Cover Flow sigue siendo simulador, sin lectura real de FW ni arranque.

## Índice FM1CAT v1

Formato estrictamente ASCII, separado por barras verticales |, sin comillas ni escapes. Cabeceras:

    FM1CAT|1
    ID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT

Ejemplo puramente ficticio:

    DARK1|DARK-1|1.2|DARK1.BIN|1024|aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa|UNVERIFIED

Reglas:
- Máximo 64 entradas sin IDs duplicados.
- ID: 1–8 caracteres A-Z 0-9 _.
- LABEL: 1–24 caracteres ASCII alfanuméricos, espacios y ._-.
- VERSION: 1–16 caracteres ASCII alfanuméricos y ._-.
- FILE: exclusivamente ID.BIN o ID.FWS, dentro de FW.
- SIZE: entero decimal 1–4.194.304 bytes.
- SHA256: exactamente 64 caracteres hexadecimales minúsculos del archivo exportado.
- COMPAT: exclusivamente UNVERIFIED.

El hash acredita integridad de la copia, NO la posibilidad de ejecutar el firmware.

## Secuencia de exportación Android

1. Seleccionar el firmware .bin o .fwsc con el selector de documentos de Android.
2. Introducir metadatos, elegir carpeta FM1 (no el directorio raíz).
3. No sobrescribir ficheros ni IDs existentes.
4. Copiar mediante flujo acotado y calcular SHA-256.
5. Reabrir el archivo en microSD y verificar longitud y SHA-256.
6. Preparar y validar INDEX.NEW y preservar el catálogo anterior como INDEX.BAK si existe.
7. Renombrar INDEX.NEW a INDEX.CSV para publicar la nueva biblioteca.

El renombrado SAF depende del proveedor y NO garantiza atomicidad. Si aparecen INDEX.NEW o INDEX.BAK tras un error, revisar manualmente antes de repetir. No retirar la tarjeta durante escritura. Los archivos originales no se modifican.

## Cover Flow

El simulador web importa INDEX.CSV con un selector de archivo y muestra nombres y versiones reales del índice. No lee FW, no verifica las imágenes y no arranca firmware.

## Próximos requisitos embebidos

- Validar alimentación VBUS, CC USB-C, USB Host y lector MSC real del FM1.
- Leer FAT32 e índice en modo solo lectura.
- Verificar hash de archivo seleccionado antes de considerar acciones de arranque.
- Diseñar compatibilidad por imagen RAM, Flash XIP o no ejecutable.
- Validar recuperación antes de escribir Flash y sustituir DARK-1.
