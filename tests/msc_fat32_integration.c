/* SPDX-License-Identifier: MIT
 * Simulated BOT device feeding the existing FAT32 parser. */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "msc_bot.h"
#include "storage.h"
struct sim { FILE *file; u32 lba; u32 tag; int data; };
static enum mb_msc_result out(void *v, const u8 *p, u32 n)
{ struct sim *s=v; u32 t, x; int in; u8 l; assert(mb_msc_validate_cbw(p,n,&t,&x,&in,&l)==MB_MSC_OK); assert(in && x==512 && p[15]==0x28); s->tag=t; s->lba=bd32(p+17); s->data=1; return MB_MSC_OK; }
static enum mb_msc_result in(void *v, u8 *p, u32 n, u32 *got)
{ struct sim *s=v; *got=n; if (s->data) { assert(n==512); assert(fseek(s->file,(long)s->lba*512L,SEEK_SET)==0); assert(fread(p,1,512,s->file)==512); s->data=0; return MB_MSC_OK; } assert(n==13); memset(p,0,n); st32(p,MB_MSC_SIGNATURE_CSW); st32(p+4,s->tag); return MB_MSC_OK; }
static enum diag_result read_block(void *v, u32 lba, u8 *outbuf)
{ struct sim *s=v; struct mb_msc_bot bot; struct mb_msc_io io={s,out,in,0}; mb_msc_bot_init(&bot,io); bot.blocks=66581; return mb_msc_bot_read10(&bot,lba,outbuf)==MB_MSC_OK?DIAG_OK:DIAG_TRANSFER; }
int main(int argc,char **argv)
{ struct sim s; struct block_device disk; struct fat32 fs; struct fat_file index; u8 buf[512]; u32 got; assert(argc==2); s.file=fopen(argv[1],"rb"); assert(s.file); disk.context=&s; disk.count=66581; disk.read=read_block; assert(fat32_mount(&fs,disk)==DIAG_OK); assert(fat32_open_fm1_index(&fs,&index)==DIAG_OK); assert(fat32_read_prefix(&fs,&index,buf,sizeof buf,&got)==DIAG_OK); assert(got>9 && memcmp(buf,"FM1CAT|1\n",9)==0); fclose(s.file); puts("MSC BOT -> 512-byte block -> FAT32: OK"); return 0; }
