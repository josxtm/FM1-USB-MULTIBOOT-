# FM1 SCREEN v1.0 · compilación 028

**Estado de distribución.** Pendiente del archivo maestro del icono, de la configuración de firma permanente y de validar la instalación firmada en Android 16. La imagen recibida muestra el diseño dentro de una captura con barras del sistema. Se necesita el PNG cuadrado original.

## Referencia verificada

- APK de compilación 027, commit `697cdfd30dcecfbd80a747aaa1ca46409ef80d16`, artefacto `15271339637`. SHA-256 `00542138a9f4f16e8cdbc1ee75b75bbd14ed5a14f8299707f84345b47114e9aa`.
- `apksigner verify --print-certs` confirmó Android Debug y huella SHA-256 `3b021b4e969fd235810fecbd0aad173e14e44c4ccf65d221152f5e3e8d285590`. La 021 tenía una huella diferente. La primera instalación release requiere exportar temas y perfiles y hacer una instalación limpia. [Procedimiento](SISTEMA-ACTUALIZACIONES.md#firma-y-migración)
- La rama base del PR #25 es `feat/fm1-screen-android` en `de6a0f4cbfb45869f0b8bbad748769e82c518f2c`. El propietario confirmó el funcionamiento físico de la 027.

## Cambios de la compilación

- `versionCode 28`, identificador `com.josxtm.fm1screen`, Android 10 o superior.
- Comprobación del canal estable en segundo plano, apartado **Avanzado → Actualizaciones**, aviso discreto, descarga y verificación de APK, y entrega al instalador Android.
- Flujo oficial en GitHub Actions con firma fijada, metadatos derivados de la APK, prueba aislada de Android 16 y publicación por etiqueta. [Operación](SISTEMA-ACTUALIZACIONES.md)
- Se mantienen los archivos y claves que usan los temas, perfiles, USB/BLE, y la interfaz. Los ajustes de actualización se guardan en su propio archivo de preferencias.

## Evidencias de distribución oficial pendientes

Completar después de una ejecución oficial válida. Faltan la URL de la Release y del APK, el SHA-256, el certificado release, el commit de distribución y el resultado de la instalación en emulador con conservación de datos. Las APK de desarrollo quedan identificadas como artefactos de prueba.
