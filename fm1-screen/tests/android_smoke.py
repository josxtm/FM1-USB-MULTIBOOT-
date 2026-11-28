#!/usr/bin/env python3
"""Validate installed Android UI and capture the physical panel layout in explicit DEMO mode."""
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


def node(text: str):
    for _ in range(5):
        if "ERROR" in adb("shell", "uiautomator", "dump", "/sdcard/fm1-ui.xml"):
            time.sleep(1)
            continue
        tree = ET.fromstring(adb("shell", "cat", "/sdcard/fm1-ui.xml"))
        # A switch has its action announced as a description and a separate static label.
        # Prefer the interactive node when both share the same visible wording.
        for attribute in ("content-desc", "text"):
            for item in tree.iter("node"):
                if item.get(attribute) == text:
                    return item
        time.sleep(1)
    raise AssertionError(f"Visible control missing: {text}")


def tap(text: str):
    x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", node(text).get("bounds", "")))
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(0.6)


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


def has_panel_color(rgb: bytes):
    raw = adb("exec-out", "screencap", binary=True)
    width, height, pixel_format = struct.unpack_from("<III", raw)
    assert pixel_format in (1, 2), f"Expected an RGBA/RGBX screenshot, got format {pixel_format}"
    offset = len(raw) - width * height * 4
    assert offset in (12, 16), f"Unexpected Android screenshot header length: {offset}"
    points = [(int(width * x), int(height * y)) for x in (0.025, 0.04) for y in (0.2, 0.4, 0.6, 0.8)]
    for x, y in points:
        start = offset + (y * width + x) * 4
        if raw[start:start + 3] == rgb:
            return True
    return False


def verify(apk: str, out: Path, device: str, build: int):
    out.mkdir(parents=True, exist_ok=True)
    prior = subprocess.run(["adb", "shell", "pm", "path", PACKAGE], capture_output=True, text=True, timeout=15)
    assert "package:" not in prior.stdout, "The test requires a clean emulator"
    adb("install", apk)
    portrait, density = {"tablet": ((1200, 1920), 160), "phone": ((1080, 1920), 440)}[device]
    landscape = portrait[::-1]
    adb("shell", "wm", "size", f"{portrait[0]}x{portrait[1]}")
    adb("shell", "wm", "density", str(density))
    adb("shell", "cmd", "window", "user-rotation", "lock", "0" if device == "phone" else "1")
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    screen_at_size(portrait if device == "phone" else landscape)
    tap("Open controls, or swipe down")
    node(f"FM1 SCREEN v1.0 · comp. {build:03}")
    tap("Vista")
    tap("Modo demostración")
    assert node("Modo demostración").get("checked") == "true"
    tap("Cerrar")
    if device == "phone":
        adb("shell", "cmd", "window", "user-rotation", "lock", "1")
        screen_at_size(landscape)
    node("DEMO")
    node("Keyboard, F3 to G5")
    shot(out, "01-full-control", landscape)
    tap("Controls view")
    node("BUTTONS")
    shot(out, "02-screen-control", landscape)
    tap("KEYS")
    node("Keyboard, F3 to G5")
    tap("Screen view")
    node("FM-1 display")
    shot(out, "03-big-screen", landscape)
    if device == "phone":
        adb("shell", "cmd", "window", "user-rotation", "lock", "0")
        time.sleep(1.5)
        node("FM-1 display")
        shot(out, "04-big-screen-portrait", portrait)
        print("Clean phone install, three landscape views and BIG SCREEN in portrait verified.")
        return
    tap("Full view")
    tap("Open controls, or swipe down")
    tap("Colores")
    node("ORIGINAL MINT")
    tap("SILVER")
    tap("Pads")
    node("HEX")
    shot(out, "04-editor-colores", landscape)
    tap("Cerrar")
    silver = bytes.fromhex("C7CED3")
    assert has_panel_color(silver), "Silver preset did not recolor the visible panel"
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    node("Keyboard, F3 to G5")
    assert has_panel_color(silver), "Silver panel color was not restored after restarting the app"
    adb("shell", "cmd", "window", "user-rotation", "lock", "0")
    node("Keyboard, F3 to G5")
    print("Installed the APK, navigated the three layouts, opened the editor and rotated the tablet.")


if __name__ == "__main__":
    output = Path(sys.argv[2])
    try:
        verify(sys.argv[1], output, sys.argv[3], int(sys.argv[4]))
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
