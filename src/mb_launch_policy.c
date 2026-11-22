/* SPDX-License-Identifier: MIT */
#include "mb_launch_policy.h"
enum mb_compatibility mb_classify_launch(const struct mb_entry *e,struct mb_launch_metadata m)
{
    if(!e||!m.format_known)return MB_COMPAT_UNKNOWN;
    if(!m.integrity_verified)return MB_COMPAT_INCOMPATIBLE;
    if(m.install_required&&!m.proprietary_authorized)return MB_COMPAT_INSTALL_REQUIRED;
    if(m.abi_verified&&m.ram_verified)return MB_COMPAT_SIMULATION;
    if(m.xip_verified)return MB_COMPAT_ADAPTATION_REQUIRED;
    return MB_COMPAT_INSPECTION;
}
const char *mb_compatibility_name(enum mb_compatibility c)
{
    static const char*n[]={"INSPECTION","SIMULATION","INSTALL_REQUIRED","ADAPTATION_REQUIRED","INCOMPATIBLE","UNKNOWN"};
    return c<sizeof(n)/sizeof(n[0])?n[c]:"UNKNOWN";
}
