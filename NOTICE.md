# Licencias y atribuciones

El código original de este repositorio y sus adaptaciones se ofrecen bajo la licencia MIT. Se conserva el aviso de M-Unbricker en `LICENSE`.

- Julius Vitkauskas, **M-Unbricker**, commit `bd5ea96ca2fb925e1647d233373bac7e3881a098`. Proceden de este código `src/husb.c`, `src/husb.h`, `src/lcd.c`, `src/lcd.h`, los tipos y accesores de `src/mbl.h`, el acceso a registros de `src/hw.h`, `src/disp.h`, la lectura de botones de `src/keys.c`, el acceso P33 de `src/watchdog.c`, la configuración del temporizador, la entrada de RAM y `scripts/app_trailer.py`. El diagnóstico elimina la lógica de rescate, las imágenes de firmware, el protocolo de escritura Flash y las secuencias USB_KEY. [Fuentes MIT](https://github.com/jvitkauskas/m-unbricker/tree/bd5ea96ca2fb925e1647d233373bac7e3881a098)
- Julius Vitkauskas, **m-bootloader**, commit `121cfbea1ef6a66df8c804ee45c1338bc16b0a53`. Es el origen de parte del código de pantalla y botones que reutiliza M-Unbricker. Los glifos 5 × 7 de `src/glyphs.h` proceden de `src/display.c` en ese commit. [Fuentes MIT](https://github.com/jvitkauskas/m-bootloader/tree/121cfbea1ef6a66df8c804ee45c1338bc16b0a53) · [Aviso de glifos](https://github.com/jvitkauskas/m-bootloader/blob/121cfbea1ef6a66df8c804ee45c1338bc16b0a53/NOTICE.md)
- M-Unbricker documenta que su controlador USB se escribió a partir de hechos sobre los registros del SDK JieLi AC79. Este proyecto no distribuye código del SDK. [Notas del controlador original](https://github.com/jvitkauskas/m-unbricker/blob/bd5ea96ca2fb925e1647d233373bac7e3881a098/docs/husb-notes.md)

DARK-1, M-Flasher, Android FM-1 Flasher, FM‑1‑RE y el fichero de distribución del compilador se consultaron como referencias. Se conservan sus términos y atribuciones originales.
