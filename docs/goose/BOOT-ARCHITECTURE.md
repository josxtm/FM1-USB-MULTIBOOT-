# Boot architecture and recovery boundary

The software prototype separates four layers:

1. `embedded_multiboot.c`: portable state machine, FM1CAT v1 parser, selection, verification and render callbacks.
2. Storage HAL: a read-only `mb_storage.read` callback. The host tests provide an in-memory backend, and `embedded_fat32_storage.c` now adapts the existing FAT32 reader over a simulated block image. USB MSC remains unlinked and physical. FASE 5E adds a controller-independent read-only BOT policy layer (`msc_bot`) for simulation; it does not activate USB Host or imply MSC hardware support.
3. Render HAL: callbacks for cards and status. No ST7789 register access is linked by the portable core.
4. Future FM-1 backend: explicitly experimental and disabled in this delivery.

The core state machine includes BOOT, NO_STORAGE, LOADING_CATALOG, EMPTY, BROWSE, INSPECT, VERIFY, READY_FOR_SIMULATION, INCOMPATIBLE, ERROR and RECOVERY. `mb_prepare_launch()` always returns `MB_LAUNCH_BLOCKED`.

The existing m-bootloader, DARK-1, Boot ROM and protected Flash region are not replaced or modified. The observed return to DARK-1 is not treated as proof of complete recovery. Recovery requirements remain an external backup, verified restoration procedure, power-loss plan and independent map of protected regions.

The existing `src/main.c`, `src/storage.c`, `src/fat32.c`, `src/husb.c` and `src/lcd.c` are retained. They are not silently wired into the new core, because physical USB Host, display initialization and RAM execution remain unapproved.
