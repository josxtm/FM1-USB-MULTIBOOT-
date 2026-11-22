/* SPDX-License-Identifier: MIT
 * Pure event adapter; physical sampling remains in keys.c. */
#ifndef EMBEDDED_KEYS_H
#define EMBEDDED_KEYS_H
#include "embedded_multiboot.h"
#include "mbl.h"
#define MB_KEY_PREVIOUS 1u
#define MB_KEY_NEXT 2u
#define MB_KEY_PLAY 4u
#define MB_KEY_REC 8u
struct mb_key_adapter { u32 candidate, stable, changed_at, repeat_at; u8 pressed; };
void mb_keys_init(struct mb_key_adapter *);
int mb_keys_poll(struct mb_key_adapter *, u32 raw, u32 now_ms, enum mb_event *event);
#endif
