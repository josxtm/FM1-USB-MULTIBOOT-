# Primera prueba física USB-C — propuesta condicionada

**Estado: no autorizada y no ejecutada.** Antes de conectar un lector se necesita esquema/medición independiente de CC1/CC2, VBUS, GND, D+/D−, rol, protección y corriente.

Material potencial, solo tras autorización: FM-1 con recuperación independiente, cable/adaptador conocido, multímetro/osciloscopio aislado, hub autoalimentado con limitación y lector microSD conocido. El hub no elimina por sí solo backfeed ni problemas de CC.

Orden mínimo: (1) revisión documental; (2) comprobaciones pasivas sin inyectar alimentación; (3) confirmar ausencia de tensión peligrosa; (4) únicamente si el gate eléctrico pasa, enumeración de descriptors sin SCSI write; (5) después, y en otra autorización, READ CAPACITY y un READ(10). Abortará ante brownout, calor, backfeed, CC inesperado, reset repetido o pérdida de recuperación. Registrar tensión, rol, VID/PID, descriptors, clase, endpoints y errores. No ejecutar RAM/APP1, no Flash, no DARK-1 y no instalar firmware.
