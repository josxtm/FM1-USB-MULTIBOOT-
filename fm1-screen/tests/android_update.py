#!/usr/bin/env python3
"""Confirm a same-certificate update through the Android installer on an isolated emulator."""
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import time

from android_smoke import adb, find, hierarchy

PACKAGE = "com.josxtm.fm1screen"
RUNNER = f"{PACKAGE}.test/{PACKAGE}.UpdateInstrumentation"


def test_step(step):
    return ["adb", "shell", "am", "instrument", "-w", "-e", "step", step]


def complete(step):
    result = subprocess.check_output([*test_step(step), RUNNER], text=True, timeout=60)
    assert "FM1_UPDATE_PASS" in result, result
    return result


def wait_for_text(text, seconds=35):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            result = find(hierarchy(), text)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            print(f"UIAutomator transient failure: {error}", flush=True)
            time.sleep(2)
            continue
        if result is not None:
            return result
        time.sleep(1)
    raise AssertionError(f"Android UI did not show {text!r}")


def main():
    folder = Path(sys.argv[1])
    upgrade = Path(sys.argv[2])
    results = upgrade.parent / "results"
    results.mkdir(exist_ok=True)
    require_icon = "--require-icon" in sys.argv[3:]
    test = upgrade.with_name("test.apk")
    metadata = json.loads((folder / "FM1-SCREEN-update.json").read_text())
    base, = folder.glob("*.apk")
    code = metadata["versionCode"]
    build_hash = hashlib.sha256(upgrade.read_bytes()).hexdigest()
    adb("install", str(base))
    adb("install", str(test))
    (results / "seed.txt").write_text(complete("seed"))
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    wait_for_text("FM-1 display")
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(1.5)
    launcher = adb("shell", "cmd", "package", "resolve-activity", "--brief", "-a",
                   "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-p", PACKAGE)
    (results / "launcher-resolution.txt").write_text(launcher)
    component = launcher.strip().splitlines()[-1]
    assert component in (f"{PACKAGE}/.MainActivity", f"{PACKAGE}/{PACKAGE}.MainActivity"), launcher
    launch = adb("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c",
                 "android.intent.category.LAUNCHER", "-f", "0x10200000", "-n", component)
    (results / "launcher-start.txt").write_text(launch)
    try:
        wait_for_text("FM-1 display", 60)
    except Exception:
        for name, args in (("activities", ("shell", "dumpsys", "activity", "activities")),
                           ("windows", ("shell", "dumpsys", "window")),
                           ("logcat", ("logcat", "-d", "-t", "1000")),
                           ("ui", ("shell", "cat", "/sdcard/fm1-ui.xml"))):
            try:
                (results / f"launcher-{name}.txt").write_text(adb(*args))
            except Exception as error:
                print(f"Diagnostic {name}: {error}")
        (results / "launcher-failure.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
        raise
    for source, name in (
        (upgrade, "upgrade.apk"), (test, "wrong-id.apk"),
        (upgrade.with_name("wrong-signature.apk"), "wrong-signature.apk"),
    ):
        adb("push", str(source), f"/data/local/tmp/fm1-{name}")
    adb("shell", "appops", "set", PACKAGE, "REQUEST_INSTALL_PACKAGES", "default")
    with (results / "installation.txt").open("w") as log:
        process = subprocess.Popen([
            *test_step("install"), "-e", "sha256", build_hash, "-e", "size", str(upgrade.stat().st_size),
            "-e", "requireIcon", str(require_icon).lower(), RUNNER,
        ], stdout=log, stderr=subprocess.STDOUT)
        try:
            button = wait_for_text("Update", 60)
            assert "packageinstaller" in button.get("package", ""), button.attrib
            coords = list(map(int, re.findall(r"\d+", button.get("bounds", ""))))
            adb("shell", "input", "tap", str((coords[0] + coords[2]) // 2), str((coords[1] + coords[3]) // 2))
            status = ""
            for _ in range(40):
                status = adb("shell", "dumpsys", "package", PACKAGE)
                if re.search(rf"\bversionCode={code + 1}\b", status):
                    break
                time.sleep(0.5)
            else:
                raise AssertionError("Android did not install the higher version")
        finally:
            process.terminate()
            process.wait(timeout=10)
    (results / "verify.txt").write_text(complete("verify"))
    adb("shell", "am", "start", "-W", "-n", f"{PACKAGE}/.MainActivity")
    wait_for_text("FM-1 display")
    (results / "updated-offline.png").write_bytes(adb("exec-out", "screencap", "-p", binary=True))
    (results / "result.json").write_text(json.dumps({
        "baseline": code, "installed": code + 1, "installer": "Android",
        "preferencesThemesProfilesAndUpdateSettings": "preserved",
        "offlineLcdUi": "visible",
    }, indent=2))


if __name__ == "__main__":
    main()
