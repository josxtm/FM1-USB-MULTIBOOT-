#!/usr/bin/env python3
"""Validate installed Android UI and capture the physical panel layout in explicit DEMO mode."""
import csv
import io
import json
import math
import re
import subprocess
import struct
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = "com.josxtm.fm1screen"
ACTIVITY = f"{PACKAGE}/.MainActivity"


def adb(*args: str, binary: bool = False):
    return subprocess.check_output(["adb", *args], timeout=30, text=not binary)


def hierarchy():
    output = adb("shell", "uiautomator", "dump", "/sdcard/fm1-ui.xml")
    if "ERROR" in output:
        return None
    return ET.fromstring(adb("shell", "cat", "/sdcard/fm1-ui.xml"))


def find(tree, text):
    if tree is None:
        return None
    for attribute in ("content-desc", "text"):
        for item in tree.iter("node"):
            if item.get(attribute) == text:
                return item
    return None


def node(text: str):
    for _ in range(5):
        item = find(hierarchy(), text)
        if item is not None:
            return item
        time.sleep(0.4)
    raise AssertionError(f"Visible control missing: {text}")


def bounds(item: ET.Element):
    return tuple(map(int, re.findall(r"-?\d+", item.get("bounds", ""))))


def center(item):
    x1, y1, x2, y2 = bounds(item)
    return (x1 + x2) // 2, (y1 + y2) // 2


def press(point):
    adb("shell", "input", "tap", *map(str, point))


def tap(text: str, wait=0.28):
    press(center(node(text)))
    time.sleep(wait)


def reveal_and_tap(label):
    for _ in range(4):
        tree = hierarchy()
        item = find(tree, label)
        if item is not None and bounds(item)[3] - bounds(item)[1] > 38:
            press(center(item))
            time.sleep(.28)
            return
        scroll = next(n for n in tree.iter("node") if n.get("class") == "android.widget.ScrollView")
        x1, y1, x2, y2 = bounds(scroll)
        x = (x1 + x2) // 2
        adb("shell", "input", "swipe", *map(str, (x, y2 - 45, x, y1 + 65, 280)))
        time.sleep(.25)
    raise AssertionError(f"Could not reach {label} within the drawer")


def screen_at_size(expected_size: tuple[int, int]) -> bytes:
    for _ in range(6):
        png = adb("exec-out", "screencap", "-p", binary=True)
        assert png.startswith(b"\x89PNG\r\n\x1a\n") and len(png) > 8_000
        if struct.unpack_from(">II", png, 16) == expected_size:
            return png
        time.sleep(0.5)
    raise AssertionError(f"Expected display {expected_size}, got {struct.unpack_from('>II', png, 16)}")


def shot(out: Path, name: str, expected_size: tuple[int, int]):
    (out / f"{name}.png").write_bytes(screen_at_size(expected_size))


def has_panel_color(rgb: bytes, tolerance: int = 0):
    raw = adb("exec-out", "screencap", binary=True)
    width, height, pixel_format = struct.unpack_from("<III", raw)
    assert pixel_format in (1, 2), f"Expected an RGBA/RGBX screenshot, got format {pixel_format}"
    offset = len(raw) - width * height * 4
    assert offset in (12, 16), f"Unexpected Android screenshot header length: {offset}"
    points = [(int(width * x), int(height * y)) for x in (0.025, 0.04) for y in (0.2, 0.4, 0.6, 0.8)]
    for x, y in points:
        start = offset + (y * width + x) * 4
        if all(abs(actual - expected) <= tolerance for actual, expected in zip(raw[start:start + 3], rgb)):
            return True
    return False


GEAR = "Abrir configuración"
CLOSE = "Cerrar configuración"
CATEGORIES = ("Conexión", "Firmware", "Pantalla", "Rendimiento", "Apariencia", "Diagnóstico", "Información")
PAGES = ("Full view", "Controls view", "Screen view")
VIEWS = ("01-full-control", "02-screen-control", "03-big-screen")


def switch_checked(label):
    tree = hierarchy()
    label_node = find(tree, label)
    assert label_node is not None, f"Switch label missing: {label}"
    parents = {child: parent for parent in tree.iter("node") for child in parent}
    while label_node is not None:
        if label_node.get("checkable") == "true":
            return label_node.get("checked") == "true"
        label_node = parents.get(label_node)
    raise AssertionError(f"No actionable switch for {label}")


def rotate(rotation, size):
    # Apply to the foreground activity. An emulator launcher may keep a portrait
    # layout during a reinstall even when the requested user orientation changed.
    adb("shell", "cmd", "window", "user-rotation", "lock", str(rotation))
    return screen_at_size(size)


def launch(apk, resolution, dpi, rotation):
    prior = subprocess.run(["adb", "shell", "pm", "path", PACKAGE], capture_output=True, text=True, timeout=15)
    assert "package:" not in prior.stdout, "The test requires a clean emulator"
    adb("install", apk)
    adb("shell", "wm", "size", f"{resolution[0]}x{resolution[1]}")
    adb("shell", "wm", "density", str(dpi))
    adb("shell", "cmd", "window", "user-rotation", "lock", str(rotation))
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)


def expect_page(index):
    tree = hierarchy()
    assert find(tree, "FM-1 display") is not None, "LCD missing"
    if index == 0:
        assert find(tree, "Keyboard, F3 to G5") is not None, "FULL CONTROL changed page"
    else:
        assert find(tree, "Keyboard, F3 to G5") is None, "Unexpected keyboard"
        assert (find(tree, "BUTTONS") is not None) == (index == 1), "Unexpected layout"


def rgb_pixel(raw, x, y):
    width, height, _ = struct.unpack_from("<III", raw)
    offset = len(raw) - width * height * 4
    i = offset + (y * width + x) * 4
    return raw[i:i+3]


def sample_pixel(point):
    return rgb_pixel(adb("exec-out", "screencap", binary=True), *point)


def timings():
    raw = adb("shell", "dumpsys", "gfxinfo", PACKAGE, "framestats")
    samples = []
    for section in raw.split("---PROFILEDATA---")[1::2]:
        for frame in csv.DictReader(io.StringIO(section.strip())):
            if frame.get("Flags") == "0":
                duration = (int(frame["FrameCompleted"]) - int(frame["IntendedVsync"])) / 1_000_000
                if 0 < duration < 1000:
                    samples.append(duration)
    assert samples, "gfxinfo provided no valid frame durations"
    samples.sort()
    pct = lambda q: round(samples[math.ceil(q * len(samples)) - 1], 3)
    return {"count": len(samples), "p50_ms": pct(.50), "p95_ms": pct(.95), "p99_ms": pct(.99)}


def toggle_script(gear, close, count, pause):
    x, y = gear
    a, b = close
    adb("shell", f"for i in $(seq {count}); do input tap {x} {y}; sleep {pause}; "
        f"input tap {a} {b}; sleep {pause}; done")


def measure(gear, close):
    toggle_script(gear, close, 2, .30)
    adb("shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    toggle_script(gear, close, 15, .28)
    return timings()


def wait_pixel(point, expected, message):
    deadline = time.monotonic() + 1.8
    while time.monotonic() < deadline:
        observed = sample_pixel(point)
        if observed == expected:
            return
        time.sleep(.035)
    raise AssertionError(f"{message}: pixel {point} was {observed.hex()}, expected {expected.hex()}")


def cycle_checks(gear, close, point, count):
    node(GEAR)
    closed = sample_pixel(point)
    press(gear)
    node(CLOSE)
    opened = sample_pixel(point)
    assert opened != closed, "Drawer did not cover its expected region"
    press(close)
    wait_pixel(point, closed, "Drawer did not close")
    for i in range(count):
        press(gear)
        wait_pixel(point, opened, f"Failed to open in cycle {i + 1}")
        press(close)
        wait_pixel(point, closed, f"Failed to close in cycle {i + 1}")


def configuration_categories(out, size):
    for index, category in enumerate(CATEGORIES):
        tap(category)
        shot(out, f"category-{index + 1:02}", size)


def validate_tablet(out, size, build):
    tap("Información")
    node(f"FM1 SCREEN v1.0 · comp. {build:03}")
    tap("Firmware")
    tap("Selección de firmware")
    assert find(hierarchy(), "Automática") is not None
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    node("Protocolo")
    tap("Rendimiento")
    tap("ECO")
    tap("ULTRA LOW LATENCY")
    tap("Apariencia")
    tap("Tema")
    tap("SILVER")
    assert has_panel_color(bytes.fromhex("C7CED3"), tolerance=18), "Silver did not recolor the open panel background"
    shot(out, "theme-silver", size)
    tap("Tema")
    tap("DARK")
    shot(out, "theme-dark", size)
    tap("Colores personalizados")
    tap("Fondo principal")
    tap("Muestras")
    tap("#4FCDBF")
    assert has_panel_color(bytes.fromhex("4FCDBF"), tolerance=18), "Custom swatch did not recolor the open view"
    shot(out, "theme-custom", size)
    # Persist the selected light preset and the selected performance mode across process restart.
    tap("Tema")
    tap("SILVER")
    tap(CLOSE)
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    node(GEAR)
    for _ in range(10):
        if has_panel_color(bytes.fromhex("C7CED3")):
            break
        time.sleep(.12)
    else:
        raise AssertionError("The restored SILVER background did not reach the screen")
    tap(GEAR)
    tap("Rendimiento")
    node("Respuesta inmediata · sin suavizado")
    tap("Diagnóstico")
    node("Estado MIDI")
    node("Compartir diagnóstico")
    tap("Diagnóstico avanzado")
    node("RX cerrado · TX cerrado · 0 reconexiones")
    tap("Diagnóstico avanzado")
    tap("Consola MIDI / SysEx")
    tap("Filtro MIDI")
    tap("Clock F8")
    tap(CLOSE)


def baseline(apk, out, device):
    portrait, density = {"tablet": ((1200, 1920), 160), "phone": ((1080, 1920), 440)}[device]
    landscape = portrait[::-1]
    launch(apk, portrait, density, 1)
    tap("Open controls, or swipe down")
    shot(out, "baseline-settings", landscape)
    tap("Vista")
    tap("Modo demostración")
    tap("Cerrar")
    info = {}
    for index, view in enumerate(VIEWS):
        if index:
            tap(PAGES[index])
        info[view] = bounds(node("FM-1 display"))
        shot(out, f"baseline-{view}", landscape)
    gear = center(node("Open controls, or swipe down"))
    press(gear)
    close = center(node("Cerrar"))
    press(close)
    time.sleep(.28)
    result = measure(gear, close)
    expect_page(2)
    if device == "phone":
        rotate(0, portrait)
        tap("Open controls, or swipe down")
        shot(out, "baseline-settings-portrait", portrait)
    adb("uninstall", PACKAGE)
    return info, result


def verify(apk, out, device, build, original=None):
    out.mkdir(parents=True, exist_ok=True)
    before, pre_stats = baseline(original, out, device) if original else ({}, None)
    portrait, density = {"tablet": ((1200, 1920), 160), "phone": ((1080, 1920), 440)}[device]
    landscape = portrait[::-1]
    launch(apk, portrait, density, 0 if device == "phone" else 1)
    rotate(1, landscape)
    tap(GEAR)
    shot(out, "settings", landscape)
    tap("Pantalla")
    tap("Modo demostración")
    assert switch_checked("Modo demostración")
    for label in ("SCREEN + CONTROL", "BIG SCREEN", "FULL CONTROL"):
        tap(label)
        node(CLOSE)
        assert node(label).get("selected") == "true", f"View not selected: {label}"
    tap(CLOSE)
    expect_page(0)

    all_bounds = {}
    total = 0
    for index, view in enumerate(VIEWS):
        if index:
            tap(PAGES[index])
        expect_page(index)
        all_bounds[view] = bounds(node("FM-1 display"))
        if before:
            assert list(all_bounds[view]) == list(before[view]), f"LCD geometry changed in {view}"
        shot(out, view, landscape)
        gear_node = node(GEAR)
        gear = center(gear_node)
        x1, y1, x2, y2 = bounds(gear_node)
        touch = math.floor(48 * density / 160)
        assert x2 - x1 >= touch and y2 - y1 >= touch
        assert gear[0] > landscape[0] * 0.82 and gear[1] < landscape[1] * 0.2
        press(gear)
        close_node = node(CLOSE)
        close = center(close_node)
        node("Solicitar duplicación")
        if device == "tablet":
            node("Solo visualización")
            node("Resincronizar pantalla")
        before_close = hierarchy()
        assert find(before_close, "Selección de firmware") is None, "Firmware list is permanently expanded"
        # Verify that tapping empty background in the drawer never reaches the faceplate or backdrop.
        blank = (bounds(close_node)[2] + round(6 * density / 160), landscape[1] * 2 // 3)
        press(blank)
        node(CLOSE)
        press(close)
        time.sleep(.23)
        count = (34 if index == 0 else 33) if device == "tablet" else 4
        cycle_checks(gear, close, blank, count)
        total += count
        expect_page(index)
    print(f"{device}: {total} ciclos de apertura y cierre verificados en las 3 vistas", flush=True)
    toggle_script(gear, close, 12, .03)
    time.sleep(.30)
    tree = hierarchy()
    if find(tree, CLOSE) is not None:
        tap(CLOSE)
    expect_page(2)
    tap(PAGES[1])
    adb("shell", "input", "swipe", *map(str, (*gear, gear[0] - landscape[0] // 3, gear[1], 225)))
    expect_page(1)
    tap(PAGES[2])
    # A swipe outside the gear area must retain the original way of navigating back one view.
    adb("shell", "input", "swipe", str(landscape[0] // 8), str(gear[1]),
        str(landscape[0] // 2), str(gear[1]), "250")
    time.sleep(.32)
    expect_page(1)
    tap(PAGES[2])
    now_stats = measure(gear, close)
    expect_page(2)

    (out / "validation.json").write_text(json.dumps({
        "device": device, "cycle_count": total, "rapid_cycles": 12, "baseline_lcd_bounds": before,
        "lcd_bounds": all_bounds, "baseline_gfxinfo": pre_stats, "current_gfxinfo": now_stats,
        "note": "Tiempo de fotograma de la interfaz bajo apertura y cierre en emulador. DEMO sin MIDI.",
    }, indent=2) + "\n")

    tap(GEAR)
    configuration_categories(out, landscape)
    if device == "tablet":
        validate_tablet(out, landscape, build)
    else:
        tap(CLOSE)
        rotate(0, portrait)
        shot(out, "04-big-screen-portrait", portrait)
        for index in (2, 1, 0):
            if index != 2:
                tap(PAGES[index])
            expect_page(index)
            tap(GEAR)
            node(CLOSE)
            if index == 2:
                tap("Conexión")
                shot(out, "settings-portrait", portrait)
                tap("Información")
                node(f"FM1 SCREEN v1.0 · comp. {build:03}")
                tap("Apariencia")
                tap("Tema")
                tap("SILVER")
                assert has_panel_color(bytes.fromhex("C7CED3"), tolerance=18)
                shot(out, "theme-silver-portrait", portrait)
                tap("Tema")
                tap("DARK")
                shot(out, "theme-dark-portrait", portrait)
                tap("Colores personalizados")
                tap("Fondo principal")
                reveal_and_tap("Muestras")
                reveal_and_tap("#4FCDBF")
                assert has_panel_color(bytes.fromhex("4FCDBF"), tolerance=18)
                shot(out, "theme-custom-portrait", portrait)
            tap(CLOSE)
            expect_page(index)

    print(f"{device}: clean install, LCD geometry, gestures, seven categories and settings verified.")


if __name__ == "__main__":
    output = Path(sys.argv[2])
    try:
        verify(sys.argv[1], output, sys.argv[3], int(sys.argv[4]), sys.argv[5] if len(sys.argv) > 5 else None)
    except (AssertionError, subprocess.SubprocessError):
        try:
            diagnostic = output / "diagnostics"
            diagnostic.mkdir(parents=True, exist_ok=True)
            (diagnostic / "last-screen.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
            adb("shell", "uiautomator", "dump", "/sdcard/fm1-ui.xml")
            (diagnostic / "last-tree.xml").write_text(adb("shell", "cat", "/sdcard/fm1-ui.xml"), encoding="utf-8")
        except subprocess.SubprocessError as error:
            print(f"Could not collect the last emulator screen: {error}", file=sys.stderr)
        raise
