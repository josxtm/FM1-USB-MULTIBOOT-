/* SPDX-License-Identifier: MIT */
#include "mb_launch_policy.h"

enum mb_compatibility mb_classify_launch(const struct mb_entry *e, struct mb_launch_metadata m)
{
    if (!e || !m.format_known) return MB_COMPAT_UNKNOWN;
    if (!m.integrity_verified) return MB_COMPAT_INCOMPATIBLE;
    if (m.install_required && !m.proprietary_authorized) return MB_COMPAT_INSTALL_REQUIRED;
    if (m.install_required && m.proprietary_authorized) return MB_COMPAT_FLASH_INSTALL_CANDIDATE;
    if (m.abi_verified && m.ram_verified) return MB_COMPAT_RAM_CANDIDATE;
    if (m.xip_verified) return MB_COMPAT_ADAPTATION_REQUIRED;
    return MB_COMPAT_INSPECTION_ONLY;
}

const char *mb_compatibility_name(enum mb_compatibility c)
{
    static const char *const names[] = {"INSPECTION_ONLY", "RAM_CANDIDATE", "FLASH_INSTALL_CANDIDATE",
        "ADAPTATION_REQUIRED", "INCOMPATIBLE", "UNKNOWN"};
    return c < (sizeof(names) / sizeof(names[0])) ? names[c] : "UNKNOWN";
}

const char *mb_compatibility_reason(enum mb_compatibility c)
{
    switch (c) {
    case MB_COMPAT_INSPECTION_ONLY: return "format/integrity known; execution ABI is not demonstrated";
    case MB_COMPAT_RAM_CANDIDATE: return "ABI and RAM evidence exist; execution remains blocked by policy";
    case MB_COMPAT_FLASH_INSTALL_CANDIDATE: return "installation transaction and recovery still require authorization";
    case MB_COMPAT_ADAPTATION_REQUIRED: return "XIP evidence exists but startup adaptation is missing";
    case MB_COMPAT_INCOMPATIBLE: return "integrity or structural requirement failed";
    default: return "format or required evidence is unknown";
    }
}
