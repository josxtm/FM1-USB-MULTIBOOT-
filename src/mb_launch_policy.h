/* SPDX-License-Identifier: MIT */
#ifndef MB_LAUNCH_POLICY_H
#define MB_LAUNCH_POLICY_H
#include "embedded_multiboot.h"
enum mb_compatibility { MB_COMPAT_INSPECTION, MB_COMPAT_SIMULATION, MB_COMPAT_INSTALL_REQUIRED,
    MB_COMPAT_ADAPTATION_REQUIRED, MB_COMPAT_INCOMPATIBLE, MB_COMPAT_UNKNOWN,
    /* FASE 5R public vocabulary; aliases preserve the 5M API. */
    MB_COMPAT_INSPECTION_ONLY = MB_COMPAT_INSPECTION,
    MB_COMPAT_RAM_CANDIDATE = MB_COMPAT_SIMULATION,
    MB_COMPAT_FLASH_INSTALL_CANDIDATE = MB_COMPAT_INSTALL_REQUIRED };
struct mb_launch_metadata { uint8_t format_known, abi_verified, ram_verified, xip_verified, install_required;
    uint8_t proprietary_authorized, integrity_verified; };
enum mb_compatibility mb_classify_launch(const struct mb_entry *, struct mb_launch_metadata);
const char *mb_compatibility_name(enum mb_compatibility);
const char *mb_compatibility_reason(enum mb_compatibility);
#endif
