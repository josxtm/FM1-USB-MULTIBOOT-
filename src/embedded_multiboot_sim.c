/* SPDX-License-Identifier: MIT */
#include "embedded_multiboot.h"

static void sim_begin(void *context) { (void)context; }
static void sim_card(void *context, const struct mb_entry *entry, int side, int focus)
{ (void)context; (void)entry; (void)side; (void)focus; }
static void sim_text(void *context, const char *text) { (void)context; (void)text; }
static void sim_end(void *context) { (void)context; }

/* Link-only pi32v2 entry. It has no storage or physical HAL and is not executable firmware. */
void embedded_multiboot_sim_entry(void)
{
    static struct mb_core core;
    static const struct mb_renderer renderer = { 0, sim_begin, sim_card, sim_text, sim_end };
    mb_init(&core, (struct mb_storage){ 0, 0 }, renderer);
    mb_render(&core);
}
