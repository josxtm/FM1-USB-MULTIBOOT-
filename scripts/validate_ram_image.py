#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Validate an experimental pi32v2 APP1 RAM image without executing it.

Usage: validate_ram_image.py ELF FLAT APP1 [--entry HEX] [--app-base HEX]
This is a structural gate, never a physical-execution approval.
"""
import argparse, hashlib, struct, sys
APP=0x01C30000
RETAINED=0x01C7FD80
BSS_LOW=0x01C16800
BSS_HIGH=0x01C1E000
MACHINE=241
PT_LOAD=1

def fail(msg): raise ValueError(msg)
def u16(b,o): return struct.unpack_from('<H',b,o)[0]
def u32(b,o): return struct.unpack_from('<I',b,o)[0]
def symbol_gate(elf):
 shoff=u32(elf,32); shentsize=u16(elf,46); shnum=u16(elf,48)
 if shentsize!=40 or shoff+shentsize*shnum>len(elf): fail('invalid section table')
 sections=[]
 for i in range(shnum):
  o=shoff+i*shentsize; sections.append(struct.unpack_from('<10I',elf,o))
 forbidden={'flash_write','flash_erase','program_flash','erase_flash','execute_ram'}
 undefined=[]
 for sec in sections:
  if sec[1]!=2: continue
  off,size,link,entsize=sec[4],sec[5],sec[6],sec[9]
  if link>=len(sections) or entsize!=16 or off+size>len(elf): fail('invalid symbol table')
  stroff, strsize=sections[link][4],sections[link][5]; strings=elf[stroff:stroff+strsize]
  for pos in range(off,off+size,entsize):
   name,_,_,_,_,shndx=struct.unpack_from('<IIIBBH',elf,pos)
   end=strings.find(b'\0',name)
   symbol=strings[name:end].decode('ascii','replace') if name< len(strings) else ''
   if shndx==0 and symbol: undefined.append(symbol)
   if symbol in forbidden: fail('forbidden symbol: '+symbol)
 if undefined: fail('undefined symbols: '+','.join(undefined))

def main():
 p=argparse.ArgumentParser();p.add_argument('elf');p.add_argument('flat');p.add_argument('image');p.add_argument('--entry',type=lambda x:int(x,0),default=APP);p.add_argument('--app-base',type=lambda x:int(x,0),default=APP);a=p.parse_args()
 elf=open(a.elf,'rb').read();flat=open(a.flat,'rb').read();image=open(a.image,'rb').read()
 symbol_gate(elf)
 if len(elf)<52 or elf[:8]!=b'\x7fELF\x01\x01\x01\x00':fail('expected ELF32 little-endian')
 if u16(elf,18)!=MACHINE or u16(elf,16)!=2 or u32(elf,24)!=a.entry:fail('wrong ELF type, machine or entry')
 phoff=u32(elf,28); phentsize=u16(elf,42); phnum=u16(elf,44)
 if phentsize!=32 or phoff+phentsize*phnum>len(elf):fail('invalid program header table')
 loads=[]; bss=[]
 for i in range(phnum):
  o=phoff+i*phentsize; typ=u32(elf,o); off,va,pa,fs,ms,flags,align=struct.unpack_from('<7I',elf,o+4)
  if typ==PT_LOAD:
   if off+fs>len(elf) or ms<fs:fail('invalid PT_LOAD bounds')
   loads.append((va,pa,fs,ms,flags,elf[off:off+fs]))
   if fs==0 and not(flags&1):bss.append((va,ms))
 if not loads:fail('no PT_LOAD segments')
 app=[x for x in loads if x[0]==a.app_base and x[1]==a.app_base and x[4]&1]
 if len(app)!=1 or app[0][2]!=len(flat) or app[0][5]!=flat:fail('flat image does not equal executable load segment')
 end=a.app_base+len(flat)
 if end>RETAINED:fail('image enters retained bootloader memory')
 for va,ms in bss:
  if va<BSS_LOW or va+ms>BSS_HIGH:fail('BSS outside documented low RAM window')
 if len(image)<16 or len(image)>RETAINED-a.app_base or len(image)%2:fail('APP1 size/alignment invalid')
 if image[:-8]!=flat or image[-8:-4]!=b'APP1' or image[-2:]!=b'\x01\x01':fail('APP1 trailer or payload mismatch')
 crc=0
 for x in flat:
  crc^=x<<8
  for _ in range(8):crc=((crc<<1)^0x1021 if crc&0x8000 else crc<<1)&0xffff
 if struct.unpack_from('<H',image,len(image)-4)[0]!=crc:fail('APP1 CRC mismatch')
 print('VALID SOFTWARE ONLY: machine=pi32v2 entry=0x%08X image=%d flat_sha256=%s'%(a.entry,len(image),hashlib.sha256(flat).hexdigest()))
 return 0
if __name__=='__main__':
 try:sys.exit(main())
 except (ValueError,struct.error,OSError) as e: print('INVALID:',e,file=sys.stderr);sys.exit(1)
