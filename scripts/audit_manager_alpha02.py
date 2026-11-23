#!/usr/bin/env python3
"""Compare the historical catalogue with alpha02 and test availability classification."""
import argparse
from collections import Counter
import json
from pathlib import Path

from update_manager_catalog import asset, response_status, path_license


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument("historical", type=Path)
    cli.add_argument("current", type=Path)
    args = cli.parse_args()
    before, now = [json.loads(p.read_bytes()) for p in (args.historical, args.current)]
    old = {a["sha256"]: r["id"] for r in before["entries"] for a in r["packages"]}
    available = {a["sha256"] for r in now["entries"] for a in r["packages"] if a["availability"] == "AVAILABLE"}
    statuses = {r["id"]: r["status"] for r in now["entries"]}
    assert len(before["entries"]) == len(now["entries"]) == 913 and len(old) == 415
    assert now["schema"] == 2 and len(available) == now["uniquePackages"] == 410 and available <= old.keys()
    assert Counter(statuses.values()) == now["counts"]
    excluded = [{"id": old[d], "sha256": d, "reason": statuses[old[d]]} for d in sorted(old.keys() - available)]
    assert len(excluded) == 5 and all(r["reason"] == "LICENSE_UNKNOWN" for r in excluded)
    assert [response_status(c) for c in (403, 404, 410, 429, 451, 502)] == [
        "ACCESS", "REMOVED", "REMOVED", "TEMPORARY", "BLOCKED", "TEMPORARY"
    ]
    assert path_license("GPL-3.0") == path_license("GPL-3.0-only") and path_license("NOASSERTION") is None
    reference = {"name": "original.fwsc", "digest": "sha256:" + "0" * 64, "state": "uploaded", "size": 100,
                 "browser_download_url": "https://github.com/author/repo/releases/download/v1/original.fwsc"}
    release = "https://github.com/author/repo/releases/tag/v1"
    assert asset(reference, "v1", release, "AUTHOR", "Author/Repo")
    assert not asset(reference, "v1", release + "wrong", "AUTHOR", "Author/Repo")
    assert not asset(reference, "v1", release, "AUTHOR", "Unrelated/Repo")
    assert not asset({**reference, "digest": None}, "v1", release, "AUTHOR", "Author/Repo")
    print(json.dumps({"historical_rows": len(before["entries"]), "historical_distinct_packages": len(old),
                      "available_rows": now["counts"]["AUTHOR"] + now["counts"]["COMMUNITY"],
                      "available_unique_packages": len(available), "excluded_from_previous": excluded,
                      "row_statuses": now["counts"]}, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
