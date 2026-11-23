# Fase 6B · Plan para revisión del primer ensayo

**Actualización del equipo de referencia:** SKIFF sustituye a DARK-1 como firmware musical instalado, según el propietario. Verificar el arranque de SKIFF antes y después del ensayo.

**BLOQUEADO.** Plan previsto para una sesión posterior autorizada por el propietario. El APP1 escribe en SRAM y altera estados volátiles al ejecutarse. Requiere un transporte que cumpla la parada estricta definida aquí. La versión estándar de M-Flasher 0.12.1 queda descartada para lanzar el ensayo por los fallos de detección de residente y de validación de transferencias. [Auditoría](FASE-6B-MFLASHER-AUDIT.md)

## A. Línea de base antes de conectar

1. Identificar por inspección el M‑VAVE FM‑1 y anotar el último estado conocido de SKIFF, pantalla, teclado y audio. Una anomalía previa impide atribuir diferencias al diagnóstico. Comprobar batería o alimentación normal, cable de datos conocido y ausencia de hubs, lectores microSD, alimentación adicional y otros equipos ROM en el anfitrión.
2. Desde apagado, mantener **PLAY** aproximadamente un segundo al encender, soltar cuando aparezca el menú. Confirmar FM‑1, versión `2.4-395E6CC`, Flash `856014 1024K`, aplicación válida y **UPDATE NONE**. Si hay otra versión, un error (incluida advertencia de SPI) o una actualización pendiente, parar antes de arrancar una aplicación o de seleccionar otra acción. En el código auditado la protección que puede escribir calibración es exclusiva de SMK. Un arranque normal con un actualizador pendiente puede ejecutarlo. Sólo después de confirmar UPDATE NONE y los demás requisitos, hacer un arranque normal y registrar pantalla, teclado y audio antes del diagnóstico.
3. Verificar copia externa recuperable ya existente y guardada en privado. Comprobar localmente integridad y pertenencia a esta unidad con el auditor existente y con minutas privadas. Si sólo existe una sesión de backup interna o no hay manera de recuperar un archivo verificable, dejar el ensayo bloqueado. [Recuperación](FASE-6B-RECOVERY.md)
4. Registrar preflight offline y verificar el candidato auditado. Reservar anfitrión Linux x86_64 local con automontaje, software MIDI y flasheadores alejados de ese puerto. Cualquier reenumeración invalida la ruta anterior. Comprobar en pruebas separadas y autorizadas los permisos, la exclusividad y el filtrado del transporte. La detección en la tablet no cubre este punto.

## B. Entrada documentada en ROM

Partir de apagado real y del menú verificado. Navegar con **OCT−/OCT+** a **DOWNLOAD MODE**, comprobar el texto seleccionado y pulsar **PLAY**. El menú deja la pantalla de aviso, escribe una solicitud volátil en RAM retenida y hace un reset de software. Esta transición no solicita programación. La ruta de emergencia de stage 0 con **REC** al encender durante un segundo tiene prioridad sobre PLAY. No inicializa el LCD, lo que limita su valor como confirmación visual. No usar instalación MIDI, SysEx ni soft reset del anfitrión.

En una futura sesión aprobada, correlacionar la nueva enumeración `4C4A:8057` y la respuesta `WL82 / UBOOT1.00` con el puerto físico y la unidad que acaba de entrar. Exigir un único candidato y calificar la sesión fría antes de transferir. Esas firmas no prueban ROM limpia. Un MFLR, otro residente, sonda ambigua, interfaz ocupada o reenumeración inesperada obligan a parar. [Contrato de modos y teclas](https://github.com/jvitkauskas/m-bootloader/blob/395e6cc469ba1d6eae9922a752d1f416ff07b69a/docs/SPEC.md#42-entering-rom-usb-download-mode)

## C. Transporte previsto

La futura herramienta de ensayo debe fijar identificador de candidato, SHA-256, entrada `0x01C30000`, extremo USB, direcciones por bloque y la lista positiva auditada. Debe comprobar en tiempo real el estado y longitud de cada fase, incluida la última de 4 B, y cerrar en el primer error o duda. No permite subcomandos genéricos, acceso Flash, callbacks, lecturas de memoria libre ni reintentos de operación. El salto se intenta como máximo una vez y sólo tras completar la carga. La herramienta de esta fase es offline y no contiene esa ruta de ejecución.

## D. Observación física

Las filas iniciales del programa son las siguientes. La lectura de segundos cambia según la primera observación.

```text
FM1 MULTIBOOT
HARDWARE TEST
DISPLAY: TEST
KEYS: TEST
FLASH: NOT WRITTEN
LSB PROFILE: 48 MHZ
TMR0 SEC: 000
OCT-: WAIT
OCT+: WAIT
PLAY: WAIT
REC: WAIT
HOLD REC 2S: FINISH
IRQ OFF / RAM ONLY
```

Soltar todas las teclas inicialmente. Pulsar y soltar OCT−, OCT+, PLAY y REC por separado y comprobar que cada fila registra `SEEN`. Para REC usar una pulsación corta durante esta parte. Registrar legibilidad, filas incompletas o artefactos y comparar la progresión de segundos con un reloj externo. La etiqueta **FLASH: NOT WRITTEN** es texto fijo del diagnóstico, sin lectura de Flash que mida el estado. Una confirmación por USB o el éxito del lanzador no sustituye las observaciones del operador. Un salto sin respuesta puede acompañar la desactivación de USB, en cuyo caso la pantalla es la única evidencia prevista. Si no responde la pantalla, parada y recuperación manual.

## E. Salida y comprobaciones posteriores

Mantener **REC** dos segundos hasta **POWER OFF THEN ON** o dejar que expire el límite de 120 s. Si el temporizador se detiene o bloquea el ensayo, seguir el plan de parada manual. Encender primero sin teclas y confirmar SKIFF, pantalla, botones y audio. Hacer otro arranque con PLAY para verificar menú y versión y volver a arrancar normalmente. La vuelta se verifica en el equipo y se registra en la plantilla. Una pérdida de enlace no justifica repetir el salto. [Recuperación y plantilla](FASE-6B-RECOVERY.md)
