# Fase 4 · Latencia gráfica en Android

## Referencia y cuello de botella

El propietario confirmó físicamente que la 017 recibe y representa el LCD real del M-VAVE FM-1 en SKIFF Atlatl v1.2.5. También percibió un retardo. La comparativa de esta fase usa tráfico sintético y un reloj virtual de presentación. La latencia física de la 018 queda pendiente de medir.

En la 017, el hilo MIDI aplicaba cada bloque antes de agrupar los cambios. Para Big Screen V1 esperaba 28 ms desde el primer cambio y comprobaba el plazo cada 16 ms en el tick de sesión. Cada publicación creaba una instantánea RGB565 de 115.200 B y copiaba esos mismos 115.200 B al búfer de la interfaz. En la reproducción inicial, recibir un fragmento, ensamblarlo, decodificar una fila y aplicar su región costaron menos de un microsegundo por etapa. La espera hasta publicar alcanzó decenas de milisegundos. El coste de presentación y la espera del firmware permanecen separados.

La 018 programa la presentación con su propio plazo. Aplica cada bloque válido en orden. El framebuffer RGB565 conserva una extensión modificada por fila y solo transfiere esas extensiones al buzón de una entrada. Una publicación nueva acumula regiones de la anterior antes de sustituirla. Android consume el estado más reciente en Choreographer, a lo sumo una vez por turno de pantalla. Un bloque idéntico que ya figura en el framebuffer incrementa recepción sin pedir otro dibujo. Un reinicio borra las regiones de la sesión anterior.

El bitmap RGB565 se crea una vez por publicación que Android consume, con una sola carga nativa de 115.200 B. Las referencias usadas por Canvas, captura PNG y el grabador MP4 conservan sus píxeles. Los bytes de transferencia por regiones del monitor excluyen esa carga nativa. El camino de LCD evita conversiones a ARGB8888 para presentar. La geometría, temas, zoom y navegación de RemoteFM1 permanecen con sus entradas habituales.

## Perfiles de presentación

| Modo | Big Screen V1 y fm1-base | Tratamiento del LCD |
|---|---|---|
| ULTRA LOW LATENCY | Publica tras drenar el lote. Intervalo mínimo de 8 ms entre publicaciones. | Vecino más próximo, con un único estado de presentación pendiente. |
| BALANCED, predeterminado | Agrupa durante 12 ms y publica con intervalo mínimo de 16 ms. | Respeta el escalado y el filtro seleccionados. |
| ECO | Agrupa durante 50 ms y publica con intervalo mínimo de 66 ms. | Disminuye la frecuencia de carga del bitmap. Respeta el filtro elegido. |

El panel **Vista › Rendimiento gráfico** guarda el perfil elegido. Big Screen V2 mantiene SYNC y el plazo de 60 ms en ausencia de SYNC. ECO puede aplicar además su intervalo mínimo. El refresco del dispositivo Android limita cuántas publicaciones se dibujan.

## Comparativa reproducible

Los dos núcleos se construyeron con Kotlin 2.1.21 y Java 17. Se usó un AMD EPYC 7763 x86-64, Temurin 17.0.21, heap fijo de 128 MiB y Serial GC. El mismo reproductor procesa 9.232 bloques sintéticos en 30 s virtuales. Inyecta cuatro franjas cambiantes de 32 píxeles cada 13 ms, fragmenta cada SysEx e intercala F8. El modelo presenta cada 16 ms y exige igualdad píxel a píxel con un raster de referencia. Se descartaron tres calentamientos y se tomaron cinco repeticiones por perfil. Los resultados temporales de visibilidad usan el reloj virtual. CPU, asignaciones y GC son mediciones reales de la JVM del ensayo, con sus adaptadores de prueba.

| Configuración | P50 / P95 / P99 desde bloque hasta presentación simulada | Presentaciones en 30 s | CPU mediana | Asignación JVM mediana | Bytes copiados en el traspaso de píxeles | GC mediano |
|---|---:|---:|---:|---:|---:|---:|
| 017 | 17 / 39 / 39 ms | 721 | 15,22 ms | 90.011.064 B | 166.118.400 B | 3 ciclos |
| 018 ULTRA | 8 / 15 / 15 ms | 1.875 | 14,56 ms | 6.319.848 B | 1.113.662 B | 0 ciclos |
| 018 BALANCED | 17 / 28 / 30 ms | 1.539 | 13,98 ms | 6.232.624 B | 940.880 B | 0 ciclos |
| 018 ECO | 40 / 71 / 77 ms | 455 | 10,28 ms | 5.049.240 B | 596.540 B | 0 ciclos |

En ULTRA, el P95 de los bloques bajó 24 ms en el modelo, un 62 %. Se omitieron 433 presentaciones intermedias después de acumular sus píxeles. Los cuatro rasters resultantes fueron idénticos a la referencia, con el identificador `81531C59` de Arrays.hashCode. ECO redujo las presentaciones un 37 % respecto a 017 y aumentó la espera. El pico RSS del proceso JVM osciló entre 83,7 y 95,9 MiB. No se midió una reducción del RSS. La comparativa excluye las asignaciones nativas de Bitmap y el coste del compositor. Con la cadencia simulada, subir una imagen por presentación equivaldría a unos 83 MB en 017, 216 MB en ULTRA, 177 MB en BALANCED y 52 MB en ECO a lo largo de 30 s. Son volúmenes de carga acumulados, no memoria residente.

Para repetir, construye `:core:jar` tanto en `ea111c5` como en la revisión de esta fase. Compila el reproductor por separado contra cada JAR, porque la firma de aplicación de regiones cambió. Usa el mismo Kotlin stdlib en ambos.

```sh
mkdir -p /tmp/fm1-replay017 /tmp/fm1-replay018
javac -cp "core017.jar:kotlin-stdlib.jar" -d /tmp/fm1-replay017 tests/Fm1LatencyReplay.java
javac -cp "core018.jar:kotlin-stdlib.jar" -d /tmp/fm1-replay018 tests/Fm1LatencyReplay.java
java -Xms128m -Xmx128m -XX:+UseSerialGC -cp "/tmp/fm1-replay017:core017.jar:kotlin-stdlib.jar" Fm1LatencyReplay 017
for mode in ULTRA BALANCED ECO; do
  java -Xms128m -Xmx128m -XX:+UseSerialGC -cp "/tmp/fm1-replay018:core018.jar:kotlin-stdlib.jar" Fm1LatencyReplay "$mode"
done
```

## Qué mide el monitor

- P50, P95 y P99 de las últimas 512 muestras por tramo. Registra callback MIDI, cola, trabajo de ensamblado, tiempo entre primer y último fragmento, decodificación, aplicación RGB565, agrupación, espera de la interfaz, carga del bitmap y registro del LCD en Canvas.
- La latencia interna empieza en el callback MIDI. Se conserva la edad tanto del primer cambio pendiente como del más reciente. El último punto de la medición es el registro de instrucciones de dibujo. La presentación del panel físico, el viaje por USB, la GPU y la emisión de luz de Android quedan fuera de esa medición. Las marcas monotónicas MIDI válidas se muestran como un tramo aparte.
- Big Screen V1 informa bloques/s, publicaciones/s y dibujos/s. Su protocolo carece de contadores de secuencia y marcas de fotograma. El contador de bloques perdidos por secuencia corresponde a fm1-base. El monitor informa por separado bytes descartados por saturación, profundidad y pico de cola, y edad de la señal gráfica. Para V2 se añade el ritmo de SYNC.
- La CPU es tiempo del proceso por tiempo transcurrido y puede superar 100 % con más de un núcleo. Heap Java y nativo se muestran por separado. Las asignaciones y GC vienen de ART cuando el sistema los expone. Los registros de diagnóstico incluyen las cifras, percentiles y perfil seleccionados. Clock F8 sigue agregado.

## Límites del firmware y verificación física

En la fuente de Atlatl v1.2.5, los saludos `FS 00` renuevan el emisor durante tres segundos, `FS 01` pide un repintado y `FS 02` consulta el nombre de sonido. Ninguno negocia FPS. El emisor obtiene filas de los cambios que pinta el firmware y su cola USB es compartida. El ciclo UI tiene una espera mínima de 15 ms por vuelta. La 018 conserva las solicitudes, el orden de negociación, la renovación objetivo de 900 ms y la recuperación de la 017. Una prueba compara marcas de tiempo y bytes TX de los tres perfiles con el mismo reloj de sesión. [Emisor y comandos](https://github.com/LIkst4/skiff-atlatl/blob/2d4e390bc024640d7c8ca0621fc475363be7dd6e/firmware/src/mirror.c#L100) · [Ciclo UI](https://github.com/LIkst4/skiff-atlatl/blob/2d4e390bc024640d7c8ca0621fc475363be7dd6e/firmware/src/main.c#L397).

Filma ambos LCD simultáneamente, idealmente a 240 fps, con el mismo cable, firmware, ajuste de pantalla y página. Compara la 017 con los tres perfiles de 018. Repite transiciones de página al menos 100 veces si quieres estimar P99, cuenta los fotogramas entre el cambio en ambos LCD y exporta los diagnósticos por separado para cada perfil. Mantén el secuenciador en marcha durante una parte del ensayo y comprueba diez minutos de audio en el FM-1, cobertura, reconexión y ausencia de corrupción. La primera sesión física de la 018 está pendiente. [Guía física](../PHYSICAL-TEST-PLAN.md).
