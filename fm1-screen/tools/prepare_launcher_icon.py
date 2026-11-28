#!/usr/bin/env python3
"""Generate Android launcher densities from the supplied approved artwork."""
import argparse
import hashlib
from pathlib import Path
import re

from PIL import Image, ImageDraw


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
    master = MODULE / "design/icon/approved-original.png"
    artwork = args.artwork.read_bytes()
    if master.exists() and master.read_bytes() != artwork:
        parser.error(f"{master} already contains a different original")
    master.write_bytes(artwork)
    for density, scale in DENSITIES.items():
        folder = output / "res" / f"mipmap-{density}"
        folder.mkdir(parents=True)
        legacy = round(48 * scale)
        image.resize((legacy, legacy), Image.Resampling.LANCZOS).save(folder / "fm1_launcher.png")
        round_icon = Image.new("RGBA", (legacy, legacy))
        mask = Image.new("L", (legacy * 4, legacy * 4))
        ImageDraw.Draw(mask).ellipse((0, 0, mask.width - 1, mask.height - 1), fill=255)
        round_icon.paste(args.background, (0, 0, legacy, legacy), mask.resize(round_icon.size, Image.Resampling.LANCZOS))
        safe_round = round(33 * scale)
        round_art = image.resize((safe_round, safe_round), Image.Resampling.LANCZOS)
        shift = (legacy - safe_round) // 2
        round_icon.alpha_composite(round_art, (shift, shift))
        round_icon.save(folder / "fm1_launcher_round.png")
        foreground = round(108 * scale)
        # A 46 dp square fits inside the launcher’s 66 dp circular safe zone.
        safe = round(46 * scale)
        layer = Image.new("RGBA", (foreground, foreground))
        shift = (foreground - safe) // 2
        layer.paste(image.resize((safe, safe), Image.Resampling.LANCZOS), (shift, shift))
        layer.save(folder / "fm1_launcher_foreground.png")
    adaptive = """<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/fm1_launcher_background" />
    <foreground android:drawable="@mipmap/fm1_launcher_foreground" />
</adaptive-icon>
"""
    for name in ("fm1_launcher", "fm1_launcher_round"):
        write(output / f"res/mipmap-anydpi-v26/{name}.xml", adaptive)
    write(output / "res/values/fm1_launcher.xml", f"""<resources>
    <color name="fm1_launcher_background">{args.background}</color>
</resources>
""")
    write(output / "AndroidManifest.xml", """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:icon="@mipmap/fm1_launcher" android:roundIcon="@mipmap/fm1_launcher_round" />
</manifest>
""")
    digest = hashlib.sha256(artwork).hexdigest()
    write(output / "SOURCE.sha256", f"{digest}  approved-original.png\n")


if __name__ == "__main__":
    main()
