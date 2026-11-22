#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Persistent 5Y simulator. Only regular files containing synthetic data, no device transport."""
import argparse
from contextlib import contextmanager
from dataclasses import asdict, dataclass
import fcntl
import json
import os
from pathlib import Path
import re
import stat
import struct
import tempfile
import zlib

from boot_recovery_model import RecoveryModel
from virtual_flash_installer import BOOTLOADER_END, FLASH_SIZE, KEY_MAC_START, SECTOR, FlashRegionPolicy, sha

# Deliberately smaller than m-apps' FM-1 scope. This is a synthetic fixture map.
START = BOOTLOADER_END
END = 0x10000
POLICY = FlashRegionPolicy(((START, END),))
HEADER = 512
MAX_SLOT = HEADER + KEY_MAC_START - START
JEDEC = 0x856014
FIXTURE_MAGIC = b'FM1-VIRTUAL-5Y\0'
CATALOG_HEADER = ['FM1CAT|1', 'ID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT']
ID = re.compile(r'[A-Z0-9_]{1,8}')
HASH = re.compile(r'[a-f0-9]{64}')
CUTS = ('backup', 'journal', 'first-erase', 'body-erase', 'body', 'first-program', 'verified', 'committed')


@dataclass(frozen=True)
class Entry:
    id: str
    label: str
    version: str
    filename: str
    size: int
    sha256: str


def read_file(path, limit):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as stream:
        info = os.fstat(stream.fileno())
        if not stat.S_ISREG(info.st_mode) or info.st_size > limit:
            raise ValueError(f'{path.name}: expected bounded regular file')
        data = stream.read(limit + 1)
        if len(data) != info.st_size:
            raise ValueError(f'{path.name}: file changed while reading')
        return data


def catalog(card):
    """FM1CAT v1 host adapter, with the same bounds and naming as the embedded core."""
    return parse_catalog(read_file(card / 'FM1' / 'INDEX.CSV', 24576))


def parse_catalog(raw):
    raw = raw.replace(b'\r\n', b'\n')
    if not raw.endswith(b'\n') or b'\r' in raw:
        raise ValueError('INDEX.CSV: invalid line ending')
    lines = raw[:-1].decode('ascii').split('\n')
    if lines[:2] != CATALOG_HEADER or len(lines) - 2 > 64:
        raise ValueError('INDEX.CSV: invalid header or too many entries')
    entries = {}
    for line in lines[2:]:
        columns = line.split('|')
        if len(line) > 160 or len(columns) != 7:
            raise ValueError('INDEX.CSV: invalid record')
        ident, label, version, filename, size, digest, compatibility = columns
        if (not ID.fullmatch(ident) or ident in entries or
                not re.fullmatch(r'[A-Za-z0-9 ._-]{1,24}', label) or
                not re.fullmatch(r'[A-Za-z0-9._-]{1,16}', version) or
                filename not in (f'{ident}.BIN', f'{ident}.FWS') or
                not re.fullmatch(r'[0-9]{1,10}', size) or not 0 < int(size) <= 4 * 1024 * 1024 or
                not HASH.fullmatch(digest) or compatibility != 'UNVERIFIED'):
            raise ValueError('INDEX.CSV: invalid entry or duplicate id')
        entries[ident] = Entry(ident, label, version, filename, int(size), digest)
    return entries


def decode_slot(data):
    """Read m-apps SLOT v1 structure for this FM-1 profile without writing firmware."""
    if len(data) < HEADER or data[:6] != b'SLOT\x01\x01':
        raise ValueError('expected SLOT v1 for FM-1')
    header, count, jedec = struct.unpack_from('<HII', data, 6)
    if header != HEADER or jedec != JEDEC or len(data) != HEADER + count * SECTOR:
        raise ValueError('SLOT: invalid size, count or JEDEC')
    if struct.unpack_from('<I', data, 508)[0] != zlib.crc32(data[:508]):
        raise ValueError('SLOT: bad header CRC32')
    if struct.unpack_from('<I', data, 112)[0] != zlib.crc32(data[HEADER:]):
        raise ValueError('SLOT: bad data CRC32')
    sectors = [i * SECTOR for i in range(FLASH_SIZE // SECTOR) if data[16 + i // 8] & 1 << (i % 8)]
    if len(sectors) != count or not sectors or sectors[0] != START or sectors[-1] >= KEY_MAC_START:
        raise ValueError('SLOT: inconsistent bitmap or protected sector')
    return {address: data[HEADER + i * SECTOR:HEADER + (i + 1) * SECTOR]
            for i, address in enumerate(sectors)}


def candidate(card, entry):
    if not entry.filename.endswith('.BIN') or entry.size > MAX_SLOT:
        raise ValueError('candidate is outside the .BIN SLOT profile')
    data = read_file(card / 'FM1' / 'FW' / entry.filename, entry.size)
    if len(data) != entry.size or sha(data) != entry.sha256:
        raise ValueError('candidate size or SHA-256 differs from INDEX.CSV')
    sectors = decode_slot(data)
    if not sectors[START].startswith(FIXTURE_MAGIC) or any(
            not any(lo <= at and at + SECTOR <= hi for lo, hi in POLICY.authorized) for at in sectors):
        raise ValueError('candidate is outside the authorized synthetic fixture profile')
    return data, sectors


def sync_dir(path):
    fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def persist(path, data):
    fd, temporary = tempfile.mkstemp(dir=path.parent)
    try:
        with os.fdopen(fd, 'wb') as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        sync_dir(path.parent)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


class VirtualSlotfile:
    """Transaction journal and backup are independent of the card's candidate files."""

    def __init__(self, root):
        self.root = Path(root)
        self.flash = self.root / 'flash.virtual'
        self.journal = self.root / 'state.json'
        self.backups = self.root / 'backups'

    def create(self):
        self.root.mkdir()
        self.backups.mkdir()
        flash = bytearray(b'\xff' * FLASH_SIZE)
        flash[:8] = b'BOOT-24!'
        flash[START:START + len(FIXTURE_MAGIC)] = FIXTURE_MAGIC
        flash[START + SECTOR:START + SECTOR + 3] = b'OLD'
        flash[KEY_MAC_START:KEY_MAC_START + 7] = b'FACTORY'
        persist(self.flash, flash)
        self._save({'schema': 1, 'active': {'id': 'CURRENT', 'sha256': sha(flash)}, 'pending': None})
        sync_dir(self.root.parent)
        return self._report('CREATED')

    @contextmanager
    def _locked(self):
        if self.root.is_symlink() or self.backups.is_symlink() or not self.backups.is_dir():
            raise ValueError('invalid virtual state directory')
        fd = os.open(self.root / '.lock', os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600)
        with os.fdopen(fd, 'rb+') as lock:
            if not stat.S_ISREG(os.fstat(lock.fileno()).st_mode):
                raise ValueError('expected regular lock file')
            fcntl.flock(lock, fcntl.LOCK_EX)
            yield

    def _save(self, state):
        persist(self.journal, json.dumps(state, sort_keys=True).encode('ascii') + b'\n')

    def _state(self):
        state = json.loads(read_file(self.journal, 4096))
        if not isinstance(state, dict) or set(state) != {'schema', 'active', 'pending'} or state['schema'] != 1:
            raise ValueError('invalid virtual journal')
        for value, keys in ((state['active'], {'id', 'sha256'}),
                            (state['pending'], {'id', 'sha256', 'backup'})):
            if value is None and keys == {'id', 'sha256', 'backup'}:
                continue
            if (not isinstance(value, dict) or set(value) != keys or
                    not isinstance(value['id'], str) or not ID.fullmatch(value['id']) or
                    any(not isinstance(value[k], str) or not HASH.fullmatch(value[k]) for k in keys - {'id'})):
                raise ValueError('invalid virtual journal record')
        if state['pending'] and state['pending']['backup'] != state['active']['sha256']:
            raise ValueError('backup does not match the prior committed state')
        return state

    @staticmethod
    def _image(path):
        flash = read_file(path, FLASH_SIZE)
        if len(flash) != FLASH_SIZE:
            raise ValueError('virtual Flash must be exactly 1 MiB')
        return flash

    @staticmethod
    def _report(status, **fields):
        return {'status': status, 'simulated': True, **fields}

    def status(self):
        with self._locked():
            state = self._state()
            current = self._image(self.flash)
            active = state['active']
            if state['pending'] or sha(current) != active['sha256']:
                return self._report('RECOVERY_REQUIRED', pending=state['pending'])
            return self._report('BOOTABLE_IN_MODEL', active=active, boot=RecoveryModel().simulate())

    def install(self, card, ident, confirmed=False, cut=None):
        if cut is not None and cut not in CUTS:
            raise ValueError('unknown interruption point')
        if not ID.fullmatch(ident):
            raise ValueError('invalid selection id')
        with self._locked():
            state = self._state()
            before = self._image(self.flash)
            before_hash = sha(before)
            if state['pending'] or state['active']['sha256'] != before_hash:
                return self._report('RECOVERY_REQUIRED')
            entry = catalog(card).get(ident)
            if entry is None:
                raise ValueError('selection not in INDEX.CSV')
            _, sectors = candidate(card, entry)
            blank = b'\xff' * SECTOR
            target = bytearray(before)
            changed = []
            for at in range(START, END, SECTOR):
                block = sectors.get(at, blank)
                if before[at:at + SECTOR] != block:
                    target[at:at + SECTOR] = block
                    changed.append(at)
            if not changed:
                return self._report('ALREADY_INSTALLED', active=state['active'])
            if not confirmed:
                return self._report('CONFIRMATION_REQUIRED', selected=ident, changed_sectors=len(changed))
            if cut in ('body-erase', 'body') and changed == [START]:
                raise ValueError('requested cut requires a changed body sector')

            def interrupted(stage):
                return self._report('INTERRUPTED', stage=stage, selected=ident)

            backup = self.backups / f'{before_hash}.bin'
            if not backup.exists():
                persist(backup, before)
            if self._image(backup) != before:
                raise ValueError('backup failed read-back verification')
            if cut == 'backup':
                return interrupted(cut)
            state['pending'] = {'id': ident, 'sha256': sha(target), 'backup': before_hash}
            self._save(state)
            if cut == 'journal':
                return interrupted(cut)

            # Invalidate the first firmware sector first and finish it last.
            work = bytearray(before)
            work[START:START + SECTOR] = blank
            persist(self.flash, work)
            if cut == 'first-erase':
                return interrupted(cut)
            for at in changed:
                if at == START:
                    continue
                work[at:at + SECTOR] = blank
                persist(self.flash, work)
                if cut == 'body-erase':
                    return interrupted(cut)
                work[at:at + SECTOR] = target[at:at + SECTOR]
                persist(self.flash, work)
                if cut == 'body':
                    return interrupted(cut)
            work[START:START + SECTOR] = target[START:START + SECTOR]
            persist(self.flash, work)
            if cut == 'first-program':
                return interrupted(cut)
            if self._image(self.flash) != target:
                return self._report('RECOVERY_REQUIRED', reason='virtual read-back mismatch')
            if cut == 'verified':
                return interrupted(cut)
            state['active'] = {'id': ident, 'sha256': state['pending']['sha256']}
            state['pending'] = None
            self._save(state)
            if cut == 'committed':
                return interrupted(cut)
            return self._report('COMMITTED', active=state['active'], changed_sectors=len(changed),
                                boot=RecoveryModel().simulate())

    def recover(self, cut_after_restore=False):
        with self._locked():
            state = self._state()
            current = self._image(self.flash)
            pending = state['pending']
            if pending is None:
                result = 'NO_PENDING' if sha(current) == state['active']['sha256'] else 'RECOVERY_REQUIRED'
                return self._report(result)
            try:
                backup = self._image(self.backups / f"{pending['backup']}.bin")
                if sha(backup) != pending['backup']:
                    raise ValueError('backup SHA-256 mismatch')
                if any(current[lo:hi] != backup[lo:hi] for lo, hi in POLICY.protected):
                    raise ValueError('protected virtual bytes differ')
            except (OSError, ValueError) as error:
                return self._report('RECOVERY_REQUIRED', reason=str(error))
            persist(self.flash, backup)
            if self._image(self.flash) != backup:
                return self._report('RECOVERY_REQUIRED', reason='restore read-back mismatch')
            if cut_after_restore:
                return self._report('INTERRUPTED', stage='restored')
            state['pending'] = None
            self._save(state)
            return self._report('RECOVERED', active=state['active'])


def demo(directory):
    """Generate two synthetic firmware slots and record a rollback from a fresh session."""
    directory.mkdir()
    card = directory / 'card'
    fw = card / 'FM1' / 'FW'
    fw.mkdir(parents=True)
    lines = list(CATALOG_HEADER)
    for ident, label in (('A', 'Demo A'), ('B', 'Demo B')):
        data = fixture_slot(ident)
        (fw / f'{ident}.BIN').write_bytes(data)
        lines.append(f'{ident}|{label}|1|{ident}.BIN|{len(data)}|{sha(data)}|UNVERIFIED')
    (card / 'FM1' / 'INDEX.CSV').write_text('\n'.join(lines) + '\n', encoding='ascii')
    disk = VirtualSlotfile(directory / 'machine')
    trace = [disk.create(), disk.install(card, 'A'), disk.install(card, 'A', confirmed=True),
             disk.install(card, 'B', confirmed=True, cut='body-erase')]
    reopened = VirtualSlotfile(disk.root)
    trace += [reopened.status(), reopened.recover(), reopened.status()]
    trace += [reopened.install(card, 'B', confirmed=True), reopened.status()]
    return trace


def fixture_slot(ident):
    name = ident.encode('ascii')
    if not ID.fullmatch(ident):
        raise ValueError('invalid fixture id')
    header = bytearray(HEADER)
    header[:6] = b'SLOT\x01\x01'
    struct.pack_into('<HII', header, 6, HEADER, 2, JEDEC)
    for address in (START, START + 2 * SECTOR):
        sector = address // SECTOR
        header[16 + sector // 8] |= 1 << (sector % 8)
    header[48:48 + len(name)] = name
    data = (FIXTURE_MAGIC + name).ljust(SECTOR, b'\xff') + name[:1] * SECTOR
    struct.pack_into('<I', header, 112, zlib.crc32(data))
    struct.pack_into('<I', header, 508, zlib.crc32(header[:508]))
    return bytes(header) + data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_subparsers(dest='mode', required=True)
    for name in ('init', 'status', 'recover', 'demo'):
        modes.add_parser(name).add_argument('directory', type=Path)
    modes.add_parser('list').add_argument('card', type=Path)
    selector = modes.add_parser('selector')
    selector.add_argument('source', choices=('usb-c', 'm-card-spi'))
    selector.add_argument('--menu-open', action='store_true')
    selector.add_argument('--spi-card', action='store_true')
    selector.add_argument('--stage2', type=Path)
    install = modes.add_parser('install')
    install.add_argument('directory', type=Path)
    install.add_argument('card', type=Path)
    install.add_argument('entry')
    install.add_argument('--confirm', action='store_true')
    install.add_argument('--cut', choices=CUTS)
    args = parser.parse_args()
    try:
        if args.mode == 'list':
            result = [asdict(entry) for entry in catalog(args.card).values()]
        elif args.mode == 'selector':
            stage2 = read_file(args.stage2, 0x8000) if args.stage2 else b''
            result = RecoveryModel().recovery_selector_v24(source=args.source, menu_open=args.menu_open,
                                                           spi_card=args.spi_card, stage2=stage2)
        elif args.mode == 'demo':
            result = demo(args.directory)
        else:
            state = VirtualSlotfile(args.directory)
            if args.mode == 'install':
                result = state.install(args.card, args.entry, args.confirm, args.cut)
            else:
                result = {'init': state.create, 'status': state.status, 'recover': state.recover}[args.mode]()
        print(json.dumps(result, sort_keys=True, indent=2))
    except (OSError, ValueError) as error:
        parser.exit(2, f'{error}\n')


if __name__ == '__main__':
    main()
