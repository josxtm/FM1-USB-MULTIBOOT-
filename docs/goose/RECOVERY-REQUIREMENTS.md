# Recuperación y reinstalación — requisitos

DARK-1 puede volver a obtenerse de su fuente oficial según la autorización del propietario, pero disponer del archivo no demuestra restaurabilidad. Antes de sustituirlo deben confirmarse formato oficial, versión compatible, método de actualización, checksum, modo de recuperación, preservación del m-bootloader y comportamiento ante interrupción.

La región `[0x00000,0x04000)` continúa protegida. No existe aún evidencia independiente del mapa completo, reservas, ABI, sectores protegidos adicionales ni retorno seguro. Por ello no se autoriza escritura Flash, comandos de programación, APP1 físico ni instalación del ELF.

La simulación FAT32 prueba integridad de archivos, pero no recuperación del dispositivo. La integración 5D.1 tampoco demuestra que un cargador residente pueda restaurar DARK-1: faltan mapa Flash, formato oficial, ABI, reserva del bootloader y prueba de interrupción. Un futuro simulador de actualización debe modelar doble imagen, marcador atómico, corrupción, interrupción, error de escritura, rollback y verificación posterior sobre archivos temporales. Sus resultados serán exclusivamente simulados.

Gate para experimento físico: copia oficial, mapa verificado, método compatible, procedimiento de retorno, protección del bootloader, pruebas de interrupción, alimentación segura y autorización específica. Hasta satisfacerlo, el cargador residente es sólo diseño.
