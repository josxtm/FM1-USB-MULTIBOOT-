#!/usr/bin/env python3
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

path = Path(sys.argv[1])
root = ET.parse(path).getroot()
assert root.tag.endswith("svg")
assert root.attrib["width"] == "240" and root.attrib["height"] == "240"
text = " ".join(node.text or "" for node in root.iter() if node.tag.endswith("text"))
assert "PLAY INSPECT ONLY" in text
assert "&" not in path.read_text(encoding="utf-8").replace("&amp;", "").replace("&quot;", "").replace("&apos;", "").replace("&lt;", "").replace("&gt;", "")
print(f"validated embedded SVG 240x240: {path}")
