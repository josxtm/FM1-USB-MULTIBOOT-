#!/usr/bin/env python3
"""Stage assets in a draft, then make the verified release discoverable."""
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

from release_metadata import MANIFEST, TAG


def gh(*args):
    return subprocess.check_output(["gh", *args], text=True)


def main():
    repo = os.environ["GITHUB_REPOSITORY"]
    tag = os.environ["GITHUB_REF_NAME"]
    folder = Path("distribution")
    manifest_file = folder / MANIFEST
    metadata = json.loads(manifest_file.read_text())
    apk = folder / f"FM1-SCREEN-v{metadata['versionName']}-comp.{metadata['build']}.apk"
    checksum = apk.with_suffix(".apk.sha256")
    with apk.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    if (digest != metadata["sha256"] or checksum.read_text() != f"{digest}  {apk.name}\n"
            or apk.stat().st_size != metadata["sizeBytes"]):
        raise ValueError("Los archivos de distribución no coinciden")
    if json.loads(gh("api", f"repos/{repo}"))["visibility"] != "public":
        raise ValueError("El canal sin autenticación requiere un repositorio público")
    pages = json.loads(gh("api", "--paginate", "--slurp", f"repos/{repo}/releases?per_page=100"))
    staged = None
    for release in (item for page in pages for item in page):
        old_tag = release["tag_name"]
        if old_tag == tag:
            if not release["draft"] or release["body"].splitlines()[-1:] != [f"Commit: {os.environ['GITHUB_SHA']}"]:
                raise ValueError("La publicación ya existe")
            staged = release
        match = TAG.fullmatch(old_tag)
        if not match or release["draft"] or release["prerelease"]:
            continue
        if int(match[2]) >= metadata["versionCode"]:
            raise ValueError(f"Ya existe una compilación estable igual o superior: {old_tag}")
        old_manifest = subprocess.check_output([
            "curl", "-q", "--proto", "=https", "--proto-redir", "=https", "--fail", "--silent",
            "--show-error", "--location", "--max-time", "30",
            f"https://github.com/{repo}/releases/download/{old_tag}/{MANIFEST}",
        ])
        if json.loads(old_manifest)["signingCertificateSha256"] != metadata["signingCertificateSha256"]:
            raise ValueError("El certificado difiere del historial oficial")
    if staged is None:
        notes = "\n".join(f"- {note}" for note in metadata["releaseNotes"])
        notes += f"\n\nSHA-256: `{digest}`\n\nCertificado: `{metadata['signingCertificateSha256']}`"
        notes += f"\n\nCommit: {os.environ['GITHUB_SHA']}"
        gh("release", "create", tag, "--repo", repo, "--verify-tag", "--draft", "--latest=false",
           "--title", f"FM1 SCREEN v{metadata['versionName']} comp.{metadata['build']}", "--notes", notes)
    draft = json.loads(gh("release", "view", tag, "--repo", repo, "--json", "isDraft,assets"))
    if not draft["isDraft"]:
        raise ValueError("El borrador cambió durante la publicación")
    existing = {asset["name"] for asset in draft["assets"]}
    upload = []
    for asset in (apk, checksum, manifest_file):
        if asset.name in existing:
            with tempfile.TemporaryDirectory() as temp:
                existing_file = Path(temp) / asset.name
                gh("release", "download", tag, "--repo", repo, "--pattern", asset.name, "--dir", temp)
                if asset == manifest_file:
                    raw = existing_file.read_bytes()
                    old = json.loads(raw)
                    published = old.pop("publishedAt")
                    datetime.fromisoformat(published)
                    if old != metadata:
                        raise ValueError("El manifiesto del borrador difiere")
                    manifest_file.write_bytes(raw)
                elif existing_file.read_bytes() != asset.read_bytes():
                    raise ValueError(f"El borrador ya contiene un recurso distinto: {asset.name}")
        else:
            if asset == manifest_file:
                metadata["publishedAt"] = datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")
                manifest_file.write_text(json.dumps(metadata, indent=2, ensure_ascii=False) + "\n")
            upload.append(str(asset))
    if upload:
        gh("release", "upload", tag, *upload, "--repo", repo)
    print(gh("release", "edit", tag, "--repo", repo, "--draft=false", "--latest=false"))
    for url, expected in [
        (metadata["apkUrl"], digest),
        (metadata["apkUrl"] + ".sha256", hashlib.sha256(checksum.read_bytes()).hexdigest()),
        (metadata["apkUrl"].rsplit("/", 1)[0] + "/" + MANIFEST,
         hashlib.sha256(manifest_file.read_bytes()).hexdigest()),
    ]:
        data = subprocess.check_output([
            "curl", "-q", "--proto", "=https", "--proto-redir", "=https", "--fail", "--silent",
            "--show-error", "--location", "--retry", "3", "--max-time", "90", url,
        ])
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError(f"El recurso público difiere del verificado: {url}")
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
        summary.write(f"\n[APK]({metadata['apkUrl']}) · SHA-256 `{digest}`\n\n")
        summary.write(f"Certificado SHA-256 `{metadata['signingCertificateSha256']}`\n")


if __name__ == "__main__":
    main()
