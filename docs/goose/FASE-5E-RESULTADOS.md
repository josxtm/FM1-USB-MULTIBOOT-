# FASE 5E — resultados

## Hecho

* Reconciliación: rama local/remota en `b70cbc6bcbdc52e12b49daadb0602605945235b9`; PR #8 abierta Draft; `main` permanece `5d722d42c1d77adceddf9f890f0034846441c3e1`; workflows 5D.1 verdes.
* Investigación pública de Melodee, Tessera, m-bootloader y M-Flasher con URLs y licencias. Resultado: MIDI Host está documentado; MSC Host del FM-1 no.
* Añadida capa pura `msc_bot` de solo lectura y pruebas adversariales de framing/policy. `msc_fat32_integration` conecta BOT simulado → bloques 512 B → parser FAT32. Se enlaza en el target integrado pi32v2 sin controladores físicos.
* Se conservan FAT32, FM1CAT, Cover Flow, SHA, límites, integración 0/1/2/3/64 y launch bloqueado.

## Evidencia y límites

Los ensayos BOT son HAL simulada; las imágenes FAT32 son sintéticas. No se activó USB Host, VBUS/CC, microSD real, Flash, RAM, APP1 ni DARK-1. Por evidencia disponible, MSC es plausible pero no probado en el FM-1.

## Bloqueos siguientes

Faltan validación eléctrica USB-C/potencia, driver enumerador MSC real, pantalla/controles físicos y un ABI de aplicación aprobado. No existe todavía un mecanismo seguro para instalar/arrancar el firmware seleccionado. La primera prueba física segura debe ser una revisión esquemática y medición pasiva autorizada de CC/VBUS, sin conectar carga ni enviar comandos al FM-1.
