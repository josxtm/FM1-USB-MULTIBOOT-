#!/usr/bin/env python3
"""Audit the checked snapshot, the public release gate and historical candidates."""
import argparse
from collections import Counter
from datetime import datetime
from hashlib import sha256
import json
from pathlib import Path

from update_manager_catalog import asset, published_author


def test_release_selection():
    asset_row = {"id": 1, "name": "instrument-1.2.0.fwsc", "digest": "sha256:" + "1" * 64,
                 "state": "uploaded", "size": 100}
    repo = "author/repo"

    def release(tag, entry, **metadata):
        url = f"https://github.com/{repo}/releases/"
        return {"tag_name": tag, "html_url": url + "tag/" + tag,
                "published_at": "2026-11-21T10:00:00Z", "body": "Actualización comprobada",
                "assets": [{**entry, "browser_download_url": url + "download/" + tag + "/" + entry["name"]}],
                **metadata}

    old = release("v1.2.0", asset_row)
    new = release("v1.10.0", {**asset_row, "name": "instrument-1.10.0.fwsc", "id": 2, "digest": "sha256:" + "2" * 64})
    other = release("v1.3.0", {**asset_row, "name": "other-1.3.0.fwsc", "id": 3})
    draft = {**new, "draft": True}
    row = {"repo": repo, "asset": "1", "version": "1.2.0"}
    approved = published_author(row, [new, old, other, draft])
    assert [p["version"] for p in approved] == ["v1.10.0", "1.2.0"]
    assert all(p["publishedAt"] and p["notes"] for p in approved)
    assert asset(old["assets"][0], "v1.2.0", old, "AUTHOR", repo)
    assert not asset(old["assets"][0], "v1.2.0", old, "AUTHOR", "unrelated/repo")


def audit(path):
    raw = path.read_bytes()
    assert sha256(raw).hexdigest() == path.with_suffix(".sha256").read_text().strip()
    manifest = json.loads(raw)
    assert manifest["schema"] == 2 and datetime.fromisoformat(manifest["generatedAt"]).utcoffset().total_seconds() == 0
    rows = manifest["entries"]
    admissible = [p for r in rows for p in r["packages"] if p["availability"] == "AVAILABLE"]
    current = {p["sha256"] for p in admissible if p.get("current", True)}
    assert len(current) >= 250
    assert manifest["uniquePackages"] == len({p["sha256"] for p in admissible})
    assert len({r["id"] for r in rows}) == len(rows) and all(len(r["packages"]) <= 20 for r in rows)
    assert Counter(r["status"] for r in rows) == manifest["counts"]
    test_release_selection()
    return {"generated_at": manifest["generatedAt"], "rows": len(rows), "current_packages": len(current),
            "qualified_release_assets": manifest["uniquePackages"], "states": manifest["counts"]}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    print(json.dumps(audit(parser.parse_args().input), indent=2, sort_keys=True))
