# FM1 RAM Loader

APK en Kotlin y Jetpack Compose para tablets Android. La aplicación actual incorpora un panel de biblioteca microSD mediante Storage Access Framework (SAF), sin acceso bruto a la tarjeta. Ofrece detección USB, identificación de ROM de solo lectura, inspección de APP1, simulación del protocolo de carga y exportación local de la consola. **Cargar en RAM** permanece bloqueado en esta compilación.

- [Procedimiento de conexión y uso](../docs/ANDROID-RAM-LOADER-CONNECTION.md)
- [Informe de integración USB](../docs/ANDROID-USB-INTEGRATION.md)
- [Informe de seguridad](../docs/ANDROID-RAM-LOADER-SECURITY.md)
- [Atribuciones](NOTICE.md)

## Exportar firmwares a microSD

1. En el gestor de archivos de Android, crea una carpeta llamada **FM1** dentro de la microSD. Android 11+ puede impedir seleccionar la raíz de una unidad; por eso elige esa subcarpeta.
2. Abre FM1 RAM Loader y pulsa **MicroSD · Biblioteca y exportación de firmwares**.
3. Selecciona un archivo **.bin** o **.fwsc** obtenido legalmente. La APK no descarga archivos y no verifica su compatibilidad con el FM1.
4. Comprueba o ajusta ID corto, título y versión. El ID permite máximo 8 letras ASCII mayúsculas, números o guiones bajos.
5. Selecciona la carpeta FM1 de la microSD y pulsa Exportar. La APK crea FM1/FW/ID.BIN o ID.FWS (para paquetes FWSC), verifica SHA-256 releyendo los bytes y genera FM1/INDEX.CSV.
6. La aplicación **nunca sobrescribe un ID existente** ni archivos externos a FM1. Ante errores de renombrado revisa INDEX.NEW o INDEX.BAK antes de repetir.

El firmware exportado mantiene intactos sus bytes. La extensión corta FWS es un alias de almacenamiento para un contenedor FWSC; no convierte ni desempaca su contenido. Todo firmware se marca **UNVERIFIED**. Ni la APK ni el simulador pueden arrancar esos archivos.

## Compilar

Desde `android/`, con JDK 17 y SDK Android 35 y Build Tools 35.0.0:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

El APK local se genera en `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions distribuye `FM1_RAM_Loader_v1.0_comp15.apk`, con firma de depuración, SHA‑256 y resultados. Identificador de aplicación `com.josxtm.fm1ramloader`. API mínima 30, objetivo 35, diseño horizontal adaptable.

## FM1 Manager To USB

La variante `manager` del módulo existente genera el gestor de catálogo y microSD con otro ID de aplicación. El APK `loader` permanece en su variante histórica. [Guía de Manager](../docs/FM1-MANAGER-USB.md) · [Descubrimiento y FM1CAT v2](../docs/FM1-MANAGER-USB-CATALOGO.md)
