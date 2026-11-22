#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""File-backed virtual Flash transaction; never accesses device Flash."""
import argparse, hashlib, json, os, struct, tempfile
PROTECTED = 0x4000
APP_START = 0x4000
APP_END = 0x100000

def digest(b): return hashlib.sha256(b).hexdigest()
def run(image, stage='commit', flash_size=0x100000):
    payload = open(image, 'rb').read()
    if APP_START + len(payload) > APP_END: raise ValueError('image exceeds virtual application area')
    virtual = bytearray(b'\xff' * flash_size); original = bytes(virtual)
    backup = bytes(virtual[APP_START:APP_END]); marker = {'state':'PREPARED','sha256':digest(payload)}
    if stage == 'cut-before-commit':
        return {'result':'RECOVERED', 'protected_unchanged':virtual[:PROTECTED] == original[:PROTECTED], 'state':'PREPARED'}
    virtual[APP_START:APP_START + len(payload)] = payload
    if stage == 'cut-after-data':
        virtual[APP_START:APP_END] = backup
        return {'result':'RECOVERED', 'protected_unchanged':virtual[:PROTECTED] == original[:PROTECTED], 'state':'ROLLED_BACK'}
    marker['state'] = 'COMMITTED'
    verified = bytes(virtual[APP_START:APP_START + len(payload)]) == payload
    if stage == 'corrupt-after-commit':
        virtual[APP_START] ^= 1; verified = False
    if not verified:
        virtual[APP_START:APP_END] = backup; marker['state'] = 'ROLLED_BACK'
    return {'result':'COMMITTED' if verified else 'RECOVERED', 'protected_unchanged':virtual[:PROTECTED] == original[:PROTECTED], 'state':marker['state'], 'sha256':digest(payload)}

def main():
    p=argparse.ArgumentParser(); p.add_argument('image'); p.add_argument('--stage', choices=['commit','cut-before-commit','cut-after-data','corrupt-after-commit'], default='commit'); a=p.parse_args()
    print(json.dumps(run(a.image, a.stage), sort_keys=True))
if __name__ == '__main__': main()
