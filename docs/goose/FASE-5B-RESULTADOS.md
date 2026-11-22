# FASE 5B — Integración FM1CAT, portabilidad pi32v2 y simulador embebido

**Rama:** `goose/fase3b-4a-preflight`  
**PR:** #8 (Draft)  
**Base:** `feat/mboot24-midi-ram-android`

## Implementación

- El HAL de firmware ahora construye rutas únicamente como `FM1/FW/<nombre corto>`; rechaza separadores, rutas absolutas, `..` y nombres fuera de FM1CAT v1.
- `mb_verify_selected()` diferencia `MB_IO_EOF` de error, desconexión, timeout y archivo ausente.
- Se lee incrementalmente, se controlan bytes por callback, se rechaza truncamiento, sobrelectura y cualquier dato adicional después del tamaño declarado.
- SHA-256 se calcula sobre el contenido completo y se compara junto con el tamaño catalogado. Integridad no cambia `UNVERIFIED` ni habilita ejecución.
- El parser acepta LF y CRLF, conserva los límites de 24 KiB/64 entradas y rechaza catálogos inválidos.
- NEXT/PREVIOUS invalidan el estado READY del elemento anterior y vuelven a BROWSE; PLAY continúa siendo inspección.
- Se añadieron fixtures compartidos C/Android en `tests/fixtures/fm1cat/` y prueba de conformidad Android.
- Se añadió objetivo separado `core-pi32v2-object`; no modifica ni enlaza el APP1 aprobado.
- El render HAL muestra tarjeta central, adyacente o tres tarjetas según el número de entradas.

## Validación

- `make core-test`: host C99, rutas `FM1/FW`, EOF, truncamiento, datos adicionales, SHA y navegación.
- `make test`: USB/FAT32 existente.
- Compilación freestanding host estricta: superada.
- Compilación pi32v2: workflow separado y artefacto `embedded_multiboot_pi32v2.o`; el enlace independiente y ejecución siguen pendientes.
- Android y simulador mantienen sus workflows; las fixtures compartidas comprueban casos válidos e inválidos.
- Sanitizers host deben ejecutarse en CI cuando el job esté disponible; no se infiere seguridad física de ellos.

## Compatibilidad y memoria

El núcleo no usa LCD, USB Host, Flash ni libc de host para I/O; se apoya en el runtime freestanding existente para memoria. El catálogo está acotado a 24 KiB y la verificación usa un buffer de 512 bytes. El mapa exacto de SRAM, pila, ABI y reservas del m-bootloader sigue sin estar demostrado.

## Validación CI

- Núcleo C99, fixtures, rutas `FM1/FW`, EOF, SHA y objeto pi32v2: ✅ [run 44969355754](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44969355754)
- Diagnóstico RAM/pi32v2, APP1 y coherencia: ✅ [run 44969355744](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44969355744)
- Android/Kotlin/Lint y conformance FM1CAT compartida: ✅ [run 44969352349](https://github.com/josxtm/FM1-USB-MULTIBOOT-/actions/runs/44969352349)
- El primer intento Android falló porque el test no localizaba la fixture desde el directorio de Gradle; se corrigió la búsqueda ascendente y la repetición fue satisfactoria.
- `make core-test`, `make test` y compilación freestanding host: ✅

## Bloqueos y seguridad

No se implementó ruta física de arranque. `mb_prepare_launch()` continúa devolviendo `MB_LAUNCH_BLOCKED`. No se habilitan `0x25`, `0x30`, Flash, saltos arbitrarios, carga RAM física, ST7789 físico ni USB Host físico. DARK-1, m-bootloader y `[0x00000,0x04000)` permanecen intactos.
