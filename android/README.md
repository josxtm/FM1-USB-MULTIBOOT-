# FM1 RAM Loader

APK en Kotlin y Jetpack Compose para tablets Android. Ofrece detección USB, identificación de ROM de solo lectura, inspección de APP1, simulación del protocolo de carga y exportación local de la consola. **Cargar en RAM** permanece bloqueado en esta compilación.

- [Procedimiento de conexión y uso](../docs/ANDROID-RAM-LOADER-CONNECTION.md)
- [Informe de integración USB](../docs/ANDROID-USB-INTEGRATION.md)
- [Informe de seguridad](../docs/ANDROID-RAM-LOADER-SECURITY.md)
- [Atribuciones](NOTICE.md)

## Compilar

Desde `android/`, con JDK 17 y SDK Android 35 y Build Tools 35.0.0:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

El APK local se genera en `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions distribuye `FM1_RAM_Loader_v1.0_comp2.apk`, con firma de depuración, SHA‑256 y resultados. Identificador de aplicación `com.josxtm.fm1ramloader`. API mínima 30, objetivo 35, diseño horizontal adaptable.
