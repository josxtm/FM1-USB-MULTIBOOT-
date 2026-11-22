# Estrategias de inicio

## A — RAM adaptada

APP1 propio demuestra una imagen estructural, no el ABI del bootloader instalado. Para firmwares externos faltan entry, relocación, vectores, stack, reloj y mapa; por tanto quedan `RAM_CANDIDATE`/inspección.

## B — módulos residentes

fm1-dock y selectores basados en controles son evidencia de una interfaz de selección, no prueba de que DARK-1, Melodee, Tessera o `.fwsc` sean módulos cargables. Un módulo debe tener contrato y relocación documentados.

## C — instalación en aplicación

Es la estrategia más plausible si el m-bootloader arranca únicamente el área de aplicación. Requiere mapa Flash, región protegida, transacción, recuperación externa y autorización. La simulación virtual no es rollback físico.

## PLAY

PLAY sólo solicita al planificador. No existe salto real ni programación. La siguiente etapa necesaria es obtener evidencia del ABI/memoria/Flash del bootloader instalado y diseñar un experimento mínimo de recuperación; no habilitar un ejecutor por nombre, CRC o SHA.
