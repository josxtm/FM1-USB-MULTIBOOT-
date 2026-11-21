/* SPDX-License-Identifier: MIT */
#include <stddef.h>

void *memset(void *target, int value, size_t size)
{
    unsigned char *out = target;
    while (size--) *out++ = (unsigned char)value;
    return target;
}

void *memcpy(void *restrict target, const void *restrict source, size_t size)
{
    unsigned char *out = target;
    const unsigned char *in = source;
    while (size--) *out++ = *in++;
    return target;
}
