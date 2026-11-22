/* SPDX-License-Identifier: MIT
 * Pin-independent quadrature decoder. Physical PRESETS pin mapping is not
 * asserted; a board HAL supplies the two sampled bits. */
#ifndef EMBEDDED_PRESETS_H
#define EMBEDDED_PRESETS_H
#include "embedded_multiboot.h"
#include "mbl.h"
struct mb_presets_encoder { u8 previous, detent, initialized; s32 accumulator; };
void mb_presets_init(struct mb_presets_encoder *, u8 transitions_per_detent);
int mb_presets_poll(struct mb_presets_encoder *, u8 ab, enum mb_event *);
#endif
