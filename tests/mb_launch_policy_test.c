/* SPDX-License-Identifier: MIT */
#include <stdio.h>
#include "mb_launch_policy.h"
int main(void)
{
 struct mb_entry e={0}; struct mb_launch_metadata m={1,0,0,0,0,0,1};
 if(mb_classify_launch(&e,m)!=MB_COMPAT_INSPECTION)return 1;
 m.abi_verified=m.ram_verified=1;
 if(mb_classify_launch(&e,m)!=MB_COMPAT_SIMULATION)return 2;
 m.install_required=1;m.proprietary_authorized=0;
 if(mb_classify_launch(&e,m)!=MB_COMPAT_INSTALL_REQUIRED)return 3;
 m.integrity_verified=0;
 if(mb_classify_launch(&e,m)!=MB_COMPAT_INCOMPATIBLE)return 4;
 puts("launch policy matrix: inspection, simulation, install gate, integrity gate: OK"); return 0;
}
