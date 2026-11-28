# FM1 PRACTICE !!
 
Directorio independiente de la Biblia. Rama de trabajo: `docs/fm1-practice`. No modificar firmware, bootloader, módulos FM1 SCREEN/ANALIZER, ni fusionar automáticamente a main.

## Estado de referencia — v0.9.1 (2026-11-28)
- 40 perfiles / 800 apartados; 15 firmwares prioritarios; 49 prácticas, 183 pasos; 15 rutas de 6 etapas.
- 5 cursos por géneros, 8 recetas de síntesis, compositor y mezclador.
- 33 referencias visuales offline (28 con hash Git).
- 18 pendientes revisados: 15 siguen pendientes de investigación; 257 verificados documentalmente.
- Conservar v0.9.0 y v0.9.1 completos, y mantener historial. Las pruebas Android/FM1 físicas no están certificadas.
- La edición UNICO usa módulos comprimidos gzip y un iframe para navegar sin enlaces entre archivos Android. Mantener también la edición modular.

## Política de archivos
Este repositorio es público. No subir originales gráficos de terceros, grabaciones, fuentes de licencia no comprobada, claves, tokens ni ZIP íntegros que los contengan. Conservar los ZIP personales fuera de GitHub; subir código propio, scripts de reconstrucción, manifiestos y documentación original. No se ha cargado todavía el conjunto completo de fuentes de la aplicación.

## Flujo
1. Partir del ZIP de la última versión, verificar su SHA-256 e integridad.
2. Aplicar cambios con scripts versionados; conservar archivos anteriores.
3. Verificar coherencia matriz HTML/CSV, rutas, controles, sonidos y navegación offline.
4. Entregar ZIP y HTML con versión explícita. No generar APK sin autorización literal G E N E R A A P K (sin espacios internos).
5. Registrar limitaciones y cambios, y conservar evidencia de pruebas.

La recuperación de imágenes se ensayó en la rama `docs/fm1-practice-visual-v083` (PR #26); no es necesario fusionarla a main para trabajar en la Biblia.

No confundir este registro con una copia completa del código ni con una certificación de manuales.