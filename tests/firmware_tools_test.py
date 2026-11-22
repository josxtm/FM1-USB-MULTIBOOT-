#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
import json, os, subprocess, tempfile, unittest
ROOT=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
class FirmwareTools(unittest.TestCase):
    def invoke(self, script, *args):
        return json.loads(subprocess.check_output(['python3', os.path.join(ROOT,'scripts',script), *args], text=True))
    def test_inspector_unknown_and_app1(self):
        with tempfile.NamedTemporaryFile(suffix='.BIN') as f:
            f.write(b'opaque'); f.flush()
            self.assertEqual(self.invoke('inspect_firmware.py', f.name)['classification'], 'INSPECTION_ONLY')
    def test_opaque_container_names_are_not_executable(self):
        for suffix, expected in (('.fwsc', 'FWSC-OPAQUE'), ('flash.bin', 'FLASH.BIN-OPAQUE'),
                                 ('ota.bin', 'OTA.BIN-OPAQUE')):
            with tempfile.NamedTemporaryFile(suffix=suffix) as f:
                f.write(b'opaque-container'); f.flush()
                result = self.invoke('inspect_firmware.py', f.name)
                self.assertEqual(result['format'], expected)
                self.assertEqual(result['classification'], 'INSPECTION_ONLY')

    def test_inspector_app1(self):
        asset=os.path.join(ROOT,'android','app','src','main','assets','fm1-usb-diagnostics-ram-experimental.bin')
        result=self.invoke('inspect_firmware.py', asset)
        self.assertEqual(result['format'], 'APP1-v1')
        self.assertEqual(result['classification'], 'RAM_CANDIDATE')
        self.assertTrue(result['integrity'])
    def test_virtual_recovery(self):
        with tempfile.NamedTemporaryFile() as f:
            f.write(b'APP1-test'); f.flush()
            for stage in ('commit','cut-before-commit','cut-after-data','corrupt-after-commit'):
                result=self.invoke('virtual_flash_recovery.py', f.name, '--stage', stage)
                self.assertTrue(result['protected_unchanged'])
                self.assertIn(result['result'], ('COMMITTED','RECOVERED'))
if __name__ == '__main__': unittest.main()
