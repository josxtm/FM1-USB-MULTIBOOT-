# Sistema de actualizaciones de FM1 SCREEN

FM1 SCREEN busca publicaciones públicas de GitHub Releases con etiqueta `fm1-screen-v<versión>-comp.<compilación>`. Solo considera publicaciones estables con el manifiesto `FM1-SCREEN-update.json`. Ordena por `versionCode` y filtra por versión mínima de Android y certificado de la instalación. La API y los recursos se consultan con HTTPS, sin autenticación. La app comprueba automáticamente al iniciarse, como máximo una vez cada seis horas. **Avanzado → Actualizaciones** permite comprobar manualmente y desactivar la comprobación automática.

El aviso en la vista principal es discreto. Se puede posponer por compilación o abrir para consultar el tamaño, la fecha y las novedades. La descarga se inicia por acción del usuario y se puede cancelar. Comprueba el tamaño, el SHA-256 y los metadatos que Android lee de la APK. El paquete debe ser `com.josxtm.fm1screen`, tener un código superior al instalado, declarar la versión y el SDK esperados y estar firmado por el mismo certificado que la instalación. Android controla los permisos y presenta la confirmación de instalación. Si la app está en segundo plano o la duplicación está activa al terminar la descarga, la APK queda lista para instalar cuando el usuario vuelva a solicitarlo.

## Firma y migración

Conservar una clave de firma release durante toda la vida del canal estable. El propietario puede reutilizar una clave release recuperable o crear una en su equipo con el diálogo **Generate Signed Bundle / APK** de Android Studio. Guardar al menos dos copias cifradas en ubicaciones independientes. Guardar las contraseñas por separado en el gestor de contraseñas y probar la recuperación generando y verificando una APK de ensayo. El keystore y sus contraseñas se cargan únicamente en **Settings → Secrets and variables → Actions** del repositorio.

| Tipo | Nombre | Contenido |
|---|---|---|
| Secret | `FM1_KEYSTORE_BASE64` | Archivo del keystore codificado en Base64 |
| Secret | `FM1_SIGNING_STORE_PASSWORD` | Contraseña del almacén |
| Secret | `FM1_SIGNING_ALIAS` | Alias de la clave release |
| Secret | `FM1_SIGNING_KEY_PASSWORD` | Contraseña de la clave release |
| Variable | `FM1_SCREEN_CERT_SHA256` | Huella pública del certificado release, 64 caracteres hexadecimales en minúsculas |

`keytool -list -v -keystore archivo.jks -alias ALIAS` muestra la huella pública. Quitar los dos puntos y convertir `A–F` a minúsculas antes de configurar la variable. Se puede verificar la misma huella con `apksigner verify --print-certs` sobre una APK firmada. Proteger la creación de las etiquetas oficiales con las reglas de GitHub disponibles para el repositorio.

La 027 se comprobó con `apksigner` y usa Android Debug. Antes de desinstalarla, exportar cada tema personalizado en **Colores** y cada perfil importado en **Conexión**. Guardar los JSON fuera de la aplicación, anotar los ajustes que se quieran reproducir y verificar que los archivos abren. Después de la instalación limpia de la primera versión release, importar los JSON y restablecer los ajustes manualmente. Las actualizaciones normales con la misma firma conservan los almacenes `preferences`, `profiles`, `themes` y `updates`.

## Publicar una compilación

1. Incrementar `fm1Build` en `fm1-screen/gradle.properties` y añadir `fm1-screen/docs/releases/comp.NNN.md` con una novedad por línea. Esperar el resultado de desarrollo en la rama Android.
2. El propietario o un publicador autorizado crea y envía la etiqueta `fm1-screen-v1.0-comp.NNN` en el commit aprobado de `feat/fm1-screen-android`. La etiqueta autoriza la publicación de ese commit.
3. Actions comprueba el original del icono, ejecuta pruebas JVM y Lint, firma la release, verifica el certificado fijado y genera SHA-256 y metadatos a partir de la APK. Ejecuta una prueba de instalación con una segunda APK firmada y de versión superior en Android 16 y el ensayo de estabilidad Atlatl. La segunda APK solo existe en el entorno de pruebas.
4. Tras superar los trabajos obligatorios, Actions carga el APK, el SHA-256 y el manifiesto en un borrador. Verifica la continuidad de la firma con el historial. Publica el borrador y comprueba la descarga anónima y los bytes de los tres recursos. La fecha del manifiesto se genera en el paso de promoción del borrador. GitHub registra la hora efectiva en la Release.
5. Revisar la URL, el certificado y los informes en el resumen de Actions. La app descubre la publicación en la próxima comprobación. Una reejecución puede continuar un borrador del mismo commit. Una publicación visible o una compilación igual o superior bloquea la promoción.

Los pushes de desarrollo y las solicitudes de cambios ejecutan las comprobaciones de desarrollo. Las APK de pruebas llevan firma debug y son artefactos temporales. El workflow mantiene los permisos de publicación limitados al trabajo que promueve la Release.
