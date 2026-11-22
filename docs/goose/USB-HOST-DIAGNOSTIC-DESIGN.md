# Diseño de diagnóstico USB Host mínimo

El diagnóstico futuro se separa en fases: `BOOT` → `ATTACH` → reset → device/config descriptors → VID/PID → interfaz 08/06/50 → Bulk IN/OUT → INQUIRY/TUR/REQUEST SENSE → READ CAPACITY(10) → un READ(10) autorizado → FAT32/INDEX. Cada paso devuelve un código y detiene la operación ante error.

Las capas existentes se reutilizan: `husb` controla USB0, `storage` enumera y adapta, `msc_bot` valida framing, `fat32` permanece único y el core no obtiene ninguna ruta de ejecución. El diagnóstico no depende de LCD; usa `usb_diag_log` compacto.

No se empaqueta ni se declara instalable ningún binario experimental. La ejecución depende de un ABI/memoria m-bootloader todavía no verificados y permanece bloqueada.
