import unittest
from sector_plan import SECTOR, prepare, execute


class Memory:
    def __init__(self, image):
        self.data = bytearray(image)
        self.writes = []

    def read(self, pos, size):
        return bytes(self.data[pos:pos+size])

    def replace_sector(self, pos, data):
        self.writes.append(pos)
        self.data[pos:pos+len(data)] = data


class SectorTests(unittest.TestCase):
    def test_blank_and_existing_application(self):
        for fill in (0xff, 0x12):
            image = bytes([fill]) * (1 << 20)
            device = Memory(image)
            plan = prepare(image, image, 0x4000, b'A' * (SECTOR + 7), {0x4000, 0x5000})
            execute(device, plan, lambda *_: None)
            self.assertEqual(device.writes, [0x4000, 0x5000])
            self.assertEqual(device.data[:0x4000], image[:0x4000])
            self.assertEqual(device.data[0x5007:], image[0x5007:])

    def test_protected_and_inconsistent(self):
        image = bytes(1 << 20)
        for pos in (0, 0x3000, 0xff000):
            with self.assertRaises(ValueError):
                prepare(image, image, pos, b'x', {pos})
        with self.assertRaises(ValueError):
            prepare(image, image[:-1], 0x4000, b'x', {0x4000})

    def test_cut_requires_new_read(self):
        image = bytes(1 << 20)
        plan = prepare(image, image, 0x4000, b'a' * (2 * SECTOR), {0x4000, 0x5000})
        device = Memory(image)
        def disconnect(*_):
            raise ConnectionError()
        with self.assertRaises(ConnectionError):
            execute(device, plan, disconnect)
        with self.assertRaises(ValueError):
            execute(device, plan, lambda *_: None)
        self.assertEqual(device.writes, [0x4000])
