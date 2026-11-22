# Decisión de viabilidad FASE 5R

## Respuestas

1. **Varios firmwares completos desde RAM:** no demostrado. Es viable sólo para candidatos con ABI/SRAM/stack/vector/reloj comprobados. APP1 obtiene `RAM_CANDIDATE`, no autorización.
2. **Ejecución directa desde microSD:** no existe evidencia pública suficiente. La microSD es almacenamiento; no se demuestra que ROM o m-bootloader ejecute desde ella en contexto normal.
3. **Selector residente que reinstale:** técnicamente más plausible que ejecución directa si el bootloader sólo arranca el área de aplicación, pero mapa Flash, formato y recovery faltan. Sigue siendo hipótesis.
4. **Conservar m-bootloader:** diseño obligatorio; las simulaciones reservan la región protegida, pero no prueban todos los límites reales.
5. **Firmwares con compatibilidad documentada:** ninguno tiene compatibilidad de ejecución demostrada en esta unidad. El APP1 propio sólo tiene compatibilidad estructural con el perfil experimental.
6. **Formatos reconocibles:** APP1-v1, ELF32/ máquina 241, BIN/FWS opacos y fallback desconocido.
7. **Sin verificar:** cabeceras/entry/mapa de BIN/FWS y formatos propietarios, además de ABI de DARK-1/Melodee/Tessera.
8. **Recuperación fiable:** modo download, identidad, host M-Flasher, backup externo y mapa protegido confirmados; no sólo una simulación.
9. **Implementación añadida:** inspector JSON read-only, clasificación/razones C, pruebas de archivos desconocidos/APP1 y Flash virtual con fallos/rollback.
10. **Bloqueo físico:** ABI instalado, SRAM, reloj, vectores, watchdog, mapa Flash, USB-C eléctrico, procedimiento de recuperación y autorización.

## Decisión

La arquitectura recomendada para seguir investigando es selector residente + almacenamiento externo + instalación transaccional futura, manteniendo m-bootloader intacto. No se implementa ni autoriza porque la evidencia del mapa/ABI no alcanza el umbral de seguridad.
