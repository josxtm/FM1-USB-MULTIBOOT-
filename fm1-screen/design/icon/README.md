# Icono aprobado de FM1 SCREEN

El original aprobado no estaba entre los archivos de la rama ni en los adjuntos disponibles al preparar esta compilación. Android utiliza su recurso predeterminado de aplicación hasta incorporar el original.

La especificación visual identifica el original por el cuerpo negro mate en tres dimensiones, ocho mandos negros, pads y botones turquesa, pantalla naranja, inscripción FM1 y esquinas redondeadas.

Con el PNG cuadrado aprobado, ejecutar desde fm1-screen:

```sh
python3 tools/prepare_launcher_icon.py /ruta/al/original.png --background '#0B0B0B'
```

La herramienta usa Pillow para producir los mipmaps mdpi, hdpi, xhdpi, xxhdpi y xxxhdpi y el icono adaptativo. Conserva el original y documenta su SHA-256. El parámetro de fondo permite reproducir el fondo aprobado para las máscaras del lanzador. La parte frontal se ajusta a la zona segura. Gradle incorpora automáticamente los recursos de app/src/officialIcon cuando existe su manifiesto. Revisar las máscaras de Android antes de distribuir la siguiente compilación.
