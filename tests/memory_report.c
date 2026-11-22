#include "embedded_multiboot.h"
#include <stdio.h>
int main(void) {
    printf("sizeof(mb_entry)=%zu\n", sizeof(struct mb_entry));
    printf("sizeof(mb_catalog)=%zu\n", sizeof(struct mb_catalog));
    printf("sizeof(mb_core)=%zu\n", sizeof(struct mb_core));
    printf("catalog_limit=%u\n", MB_MAX_CATALOG);
    printf("sha_buffer=512\n");
    printf("render_framebuffer=0\n");
    return 0;
}
