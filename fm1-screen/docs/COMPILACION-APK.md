# Compilación y firma

Desde fm1-screen, con JDK 17, Android SDK 35 y Build Tools 35.0.0, ejecuta:

```bash
./gradlew :core:jvmTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

El [workflow FM1 SCREEN Android](../../.github/workflows/fm1-screen-android.yml) usa `fm1Build` en `gradle.properties` como versionCode. Sube FM1-SCREEN-v1.0-comp.X.apk, SHA-256, firma, metadatos y resultados. La retención de artefactos es de 30 días.

## Certificado de distribución

Las actualizaciones conservan datos con el mismo applicationId, el mismo certificado y un versionCode creciente. Release admite un JKS externo y las variables FM1_SIGNING_PATH, FM1_SIGNING_ALIAS, FM1_SIGNING_STORE_PASSWORD y FM1_SIGNING_KEY_PASSWORD. GitHub Actions carga el JKS desde FM1_KEYSTORE_BASE64 y las otras variables desde los secretos homónimos.

Sin esos secretos, Actions entrega una APK de depuración firmada por el runner. El paso a otro certificado requiere una instalación nueva. Exporta temas y perfiles si necesitas migrarlos entre certificados.
