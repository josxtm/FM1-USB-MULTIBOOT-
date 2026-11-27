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


def shot(out: Path, name: str):
    png = adb("exec-out", "screencap", "-p", binary=True)
    assert png.startswith(b"\x89PNG\r\n\x1a\n") and len(png) > 8_000
    (out / f"{name}.png").write_bytes(png)


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


def verify(apk: str, out: Path):
    out.mkdir(parents=True, exist_ok=True)
    adb("install", "-r", apk)
    adb("shell", "pm", "clear", PACKAGE)
    adb("shell", "wm", "size", "1200x1920")
    adb("shell", "wm", "density", "160")
    adb("shell", "cmd", "window", "user-rotation", "lock", "1")
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)
    tap("Open controls, or swipe down")
    tap("Vista")
    tap("Modo demostración")
    assert node("Modo demostración").get("checked") == "true"
    tap("Cerrar")
    node("DEMO")
    node("Keyboard, F3 to G5")
    shot(out, "01-full-control")
    tap("Controls view")
    node("BUTTONS")
    shot(out, "02-screen-control")
    tap("KEYS")
    node("Keyboard, F3 to G5")
    tap("Screen view")
    node("FM-1 display")
    shot(out, "03-big-screen")
    tap("Full view")
    tap("Open controls, or swipe down")
    tap("Colores")
    node("ORIGINAL MINT")
    tap("SILVER")
    tap("Pads")
    node("HEX")
    shot(out, "04-editor-colores")
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
    verify(sys.argv[1], Path(sys.argv[2]))
