"""Plan and verify a sector update against an explicit application profile."""
from dataclasses import dataclass
from hashlib import sha256

SECTOR = 4096


@dataclass(frozen=True)
class Plan:
    before: bytes
    after: bytes
    changed: tuple[int, ...]


def prepare(first, second, address, payload, allowed):
    if len(first) != 1 << 20 or first != second:
        raise ValueError("Respaldo ausente o inconsistente")
    if not payload or address < 0 or address % SECTOR or address + len(payload) > len(first):
        raise ValueError("Carga incompleta o fuera de rango")
    sectors = range(address, address + len(payload), SECTOR)
    if any(pos not in allowed or pos < 0x4000 or pos >= 0xff000 for pos in sectors):
        raise ValueError("Sector fuera del perfil autorizado")
    after = first[:address] + payload + first[address + len(payload):]
    changed = tuple(pos for pos in sectors if first[pos:pos+SECTOR] != after[pos:pos+SECTOR])
    return Plan(bytes(first), bytes(after), changed)


def execute(device, plan, progress):
    if device.read(0, len(plan.before)) != plan.before:
        raise ValueError("El dispositivo ha cambiado desde el respaldo")
    for number, pos in enumerate(plan.changed, 1):
        data = plan.after[pos:pos+SECTOR]
        device.replace_sector(pos, data)
        if device.read(pos, SECTOR) != data:
            raise IOError("Fallo de verificación del sector")
        progress(number, len(plan.changed))
    if device.read(0, len(plan.after)) != plan.after:
        raise IOError("La flash final difiere del plan")
    return sha256(plan.after).hexdigest()
