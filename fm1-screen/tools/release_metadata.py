#!/usr/bin/env python3
"""Generate stable-channel metadata from the signed APK."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess


APP_ID = "com.josxtm.fm1screen"
MANIFEST = "FM1-SCREEN-update.json"
TAG = re.compile(r"fm1-screen-v(\d+\.\d+(?:\.\d+)?)-comp\.(\d{3,10})")
APK_LIMIT = 128 * 1024 * 1024


def generate(apk, tag, expected_certificate):
    sdk = Path(os.environ["ANDROID_HOME"]) / "build-tools" / "35.0.0"
    badging = subprocess.check_output([sdk / "aapt", "dump", "badging", apk], text=True)
    signature = subprocess.check_output(
        [sdk / "apksigner", "verify", "--verbose", "--print-certs", apk], text=True
    )
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    minimum = re.search(r"^sdkVersion:'(\d+)'", badging, re.M)
    signers = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})$", signature, re.M)
    if (not package or not minimum or signers != [expected_certificate]
            or "CN=Android Debug" in signature
            or "Verified using v2 scheme (APK Signature Scheme v2): true" not in signature):
        raise ValueError("Firma, certificado o metadatos de APK inválidos")
    app_id, code, version = package.groups()
    build = code.zfill(3)
    if (app_id != APP_ID or tag != f"fm1-screen-v{version}-comp.{build}" or not TAG.fullmatch(tag)
            or apk.name != f"FM1-SCREEN-v{version}-comp.{build}.apk" or int(minimum[1]) < 29):
        raise ValueError("La APK no corresponde al identificador, versión o etiqueta esperados")
    if apk.stat().st_size not in range(1, APK_LIMIT + 1):
        raise ValueError("Tamaño de APK fuera de límites")
    with apk.open("rb") as stream:
        sha = hashlib.file_digest(stream, "sha256").hexdigest()
    apk.with_suffix(".apk.sha256").write_text(f"{sha}  {apk.name}\n")
    apk.with_name("apk-signature.txt").write_text(signature)
    apk.with_name("apk-metadata.txt").write_text(badging)
    repository = os.environ["GITHUB_REPOSITORY"]
    notes = (Path("docs/releases") / f"comp.{build}.md").read_text().splitlines()
    if not 1 <= len(notes) <= 30 or any(not n.strip() or len(n) > 500 for n in notes):
        raise ValueError("Notas inválidas. Usa una novedad por línea.")
    result = {
        "schemaVersion": 1,
        "applicationId": app_id,
        "versionName": version,
        "versionCode": int(code),
        "build": build,
        "channel": "stable",
        "minSdk": int(minimum[1]),
        "apkUrl": f"https://github.com/{repository}/releases/download/{tag}/{apk.name}",
        "sha256": sha,
        "signingCertificateSha256": signers[0],
        "sizeBytes": apk.stat().st_size,
        "releaseNotes": notes,
    }
    apk.with_name(MANIFEST).write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("tag")
    parser.add_argument("certificate")
    args = parser.parse_args()
    generate(args.apk, args.tag, args.certificate)
