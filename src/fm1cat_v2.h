/* SPDX-License-Identifier: MIT */
#ifndef FM1CAT_V2_H
#define FM1CAT_V2_H
#include "storage.h"
#include "mb_sha256.h"

#define CAT2_PAGE 32u
#define CAT2_MAX 4096u

struct cat2_record {
    char name[481], version[241], author[481], project[541];
    char path[34], sha[65], format[25], compatibility[11];
    u32 size;
};

struct cat2_index {
    mb_u64 sequence;
    u32 count;
    char generation[17];
    u8 hashes[CAT2_MAX / CAT2_PAGE][32];
};

struct cat2 {
    struct fat32 *fs;
    struct cat2_index index;
    struct cat2_record row;
    u32 slot;
    u8 recovered;
};

enum diag_result cat2_open(struct cat2 *, struct fat32 *);
typedef void (*cat2_visit)(void *, u32, const struct cat2_record *);
enum diag_result cat2_page(struct cat2 *, u32 page, cat2_visit, void *);
enum diag_result cat2_verify(struct cat2 *, const struct cat2_record *);
#endif
