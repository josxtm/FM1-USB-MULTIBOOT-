/* SPDX-License-Identifier: MIT */
#ifndef FM1_MB_SHA256_H
#define FM1_MB_SHA256_H
#include "embedded_multiboot.h"

struct sha { uint32_t h[8]; mb_u64 n; uint8_t b[64]; unsigned used; };
void shinit(struct sha *);
void shadd(struct sha *, const uint8_t *, unsigned long);
void shfinal(struct sha *, char out[65]);
#endif
