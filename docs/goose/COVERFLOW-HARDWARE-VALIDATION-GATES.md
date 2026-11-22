# Gates Cover Flow y ST7789

| Gate | Estado |
|---|---|
| renderer C99 host | validado con sink simulado |
| PPM 240×240 | generado para 0/1/2/3/64 |
| buffer sin framebuffer completo | validado por diseño/compilación |
| `lcd_window/pixels/end` | reutilizados y compilables; pantalla no activada |
| ST7789 físico | pendiente |
| orientación/colores/timing SPI1 | pendiente |
| pinmux FM-1 y backlight | documentado en `lcd.c`, no medido |
| botones físicos | escáner existente; integración no ejecutada |
| navegación real | simulada con debounce |
| RAM/stack/ABI | pendientes de evidencia física |

Compilar no abre ningún gate físico. `MB_LAUNCH_BLOCKED` continúa obligatorio.
