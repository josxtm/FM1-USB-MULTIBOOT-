#include "embedded_multiboot.h"
#include "embedded_fat32_storage.h"
#include "embedded_lcd_renderer.h"
#include "embedded_keys.h"
#include <stdio.h>
int main(void) {
    printf("sizeof(mb_entry)=%zu\n", sizeof(struct mb_entry));
    printf("sizeof(mb_catalog)=%zu\n", sizeof(struct mb_catalog));
    printf("sizeof(mb_core)=%zu\n", sizeof(struct mb_core));
    printf("catalog_limit=%u\n", MB_MAX_CATALOG);
    printf("sha_buffer=512\n");
    printf("render_framebuffer=0\n");
    printf("sizeof(fat32)=%zu\n", sizeof(struct fat32));
    printf("sizeof(mb_fat32_storage)=%zu\n", sizeof(struct mb_fat32_storage));
    printf("sizeof(mb_lcd_renderer)=%zu\n", sizeof(struct mb_lcd_renderer));
    printf("lcd_scanline_bytes=%u\n", MB_LCD_LINE_BYTES);
    printf("sizeof(mb_key_adapter)=%zu\n", sizeof(struct mb_key_adapter));
    return 0;
}
