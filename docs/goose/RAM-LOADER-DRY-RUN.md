# Dry-run del cargador RAM

`scripts/ram_loader_dry_run.py` es un receptor completamente en memoria. No abre USB, MIDI, SCSI ni Flash.

Comprueba el APP1, CRC16, SHA-256, rango `0x01C30000..0x01C7FD80`, fragmentos de 512 bytes, orden, último bloque, ACK lógico y reconstrucción byte a byte. Registra la dirección destino de cada bloque y marca el salto como `jump_recorded`, sin ejecutarlo.

Casos reproducibles:

```sh
python3 scripts/ram_loader_dry_run.py build/ram_minimal.bin
python3 scripts/ram_loader_dry_run.py build/ram_minimal.bin --cancel-after 1
python3 scripts/ram_loader_dry_run.py build/ram_minimal.bin --timeout-at 1
```

Los dos últimos producen `CANCELLED` y `TIMEOUT`; el primero produce `READY_FOR_SIMULATION`. El emulador no contiene una operación de escritura Flash y no puede activar USB Host. Un ACK simulado no es evidencia de que el bootloader real acepte el comando.
