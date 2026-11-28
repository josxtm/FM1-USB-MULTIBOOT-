#!/usr/bin/env python3
"""Generate Android launcher densities from the supplied approved artwork."""
import argparse
import hashlib
from pathlib import Path
import re

from PIL import Image


DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
MODULE = Path(__file__).resolve().parents[1]


def write(path, contents):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(contents, encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artwork", type=Path, help="Approved square PNG")
    parser.add_argument("--background", required=True, help="Approved adaptive icon background in #RRGGBB")
    args = parser.parse_args()
    if not re.fullmatch(r"#[0-9a-fA-F]{6}", args.background):
        parser.error("background must be #RRGGBB")
    output = MODULE / "app/src/officialIcon"
    if output.exists():
        parser.error(f"{output} already exists. Preserve or move it before regenerating.")
    with Image.open(args.artwork) as original:
        if original.format != "PNG" or original.width != original.height or original.width < 432:
            parser.error("the approved artwork must be a square PNG of at least 432 pixels")
        image = original.convert("RGBA")
    for density, scale in DENSITIES.items():
        folder = output / "res" / f"mipmap-{density}"
        folder.mkdir(parents=True)
        legacy = round(48 * scale)
        image.resize((legacy, legacy), Image.Resampling.LANCZOS).save(folder / "fm1_launcher.png")
        foreground = round(108 * scale)
        safe = round(66 * scale)
        layer = Image.new("RGBA", (foreground, foreground))
        shift = (foreground - safe) // 2
        layer.paste(image.resize((safe, safe), Image.Resampling.LANCZOS), (shift, shift))
        layer.save(folder / "fm1_launcher_foreground.png")
    write(output / "res/mipmap-anydpi-v26/fm1_launcher.xml", """<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/fm1_launcher_background" />
    <foreground android:drawable="@mipmap/fm1_launcher_foreground" />
</adaptive-icon>
""")
    write(output / "res/values/fm1_launcher.xml", f"""<resources>
    <color name="fm1_launcher_background">{args.background}</color>
</resources>
""")
    write(output / "AndroidManifest.xml", """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:icon="@mipmap/fm1_launcher" android:roundIcon="@mipmap/fm1_launcher" />
</manifest>
""")
    digest = hashlib.sha256(args.artwork.read_bytes()).hexdigest()
    write(output / "SOURCE.sha256", f"{digest}  approved-original.png\n")


if __name__ == "__main__":
    main()
