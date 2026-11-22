# Selector persistente

## Requisito

Después de sustituir el firmware, PRESETS/PLAY sólo siguen disponibles si el bootloader invoca un selector residente o si existe una entrada de recovery documentada hacia él.

## Evidencia

El m-bootloader público puede abrir recovery, descargar por ROM USB y cargar un stage-2 SPI/m-card temporal. No hay contrato demostrado para:

- reservar una segunda aplicación residente;
- ejecutar nuestro selector antes de cualquier `app.bin`;
- enumerar un lector USB-C MSC antes de la aplicación;
- interceptar PRESETS/OCT−/OCT+ globalmente mientras corre firmware propietario.

Los botones sólo son universales dentro del menú/bootloader que los escanea. Un firmware sustituido puede reutilizar GPIO, reloj o periféricos.

## Resultado

**NO-GO** para selector autónomo persistente con el hardware y bootloader demostrados. La alternativa SPI/m-card es un stage-2 temporal y requiere modificación física; no se adopta como implementación actual. La recuperación oficial con host externo no cumple el requisito sin ordenador/móvil.
