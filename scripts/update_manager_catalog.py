#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Create the Android discovery manifest from public metadata, never from binaries."""
import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import subprocess
import time
from urllib.parse import urljoin, urlsplit
from urllib.request import Request, urlopen

HUB = "https://thefirmwarehub.com"
EDITOR = "https://fm1-editor.com/firmware/"
COLLECTION = "2bbb/fm1-firmware-collection"
COLLECTION_URL = f"https://github.com/{COLLECTION}"
REPO = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
DIGEST = re.compile(r"sha256:([a-f0-9]{64})")
FM1_ASSET = re.compile(r"\.(fwsc|ufw|bin)$", re.I)
LICENSE = re.compile(r"(GPL|LGPL|AGPL|MPL|MIT|BSD|Apache|CC0)(?:[- .+].*|$)")
TOOLS = re.compile(r"(?i)(emulat|downloader|unbrick|midi converter|firmware (tool|explor)|workbench|patch editor)")
OTHER = re.compile(r"(?i)(pocket amp|donner pocket|^SMK[- ]?37)")


class EditorLinks(HTMLParser):
    def __init__(self):
        super().__init__()
        self.projects = set()
        self.article = False

    def handle_starttag(self, tag, attrs):
        data = dict(attrs)
        if tag == "article":
            self.article = "firmware-entry" in data.get("class", "").split()
        if tag == "a" and self.article:
            url = urlsplit(data.get("href", ""))
            repo = url.path.strip("/")
            if url.hostname == "github.com" and REPO.fullmatch(repo):
                self.projects.add(repo.lower())

    def handle_endtag(self, tag):
        if tag == "article":
            self.article = False


def public_url(value, base=None):
    url = urljoin(base, value) if isinstance(value, str) and base else value
    if isinstance(url, str) and len(url) <= 512:
        p = urlsplit(url)
        if p.scheme == "https" and p.hostname and not p.username and not p.password:
            return url
    return None


def asset(a, version, release, kind):
    digest = DIGEST.fullmatch(a.get("digest") or "")
    name, size = a.get("name", ""), a.get("size", 0)
    url = public_url(a.get("browser_download_url"))
    if not (digest and FM1_ASSET.search(name) and a.get("state") == "uploaded" and
            type(size) is int and 0 < size <= 16 * 1024 * 1024 and url and
            urlsplit(url).hostname == "github.com" and "/releases/download/" in url):
        return None
    return {"version": version[:80], "file": name, "size": size, "sha256": digest[1], "url": url,
            "format": name.rsplit(".", 1)[-1].upper(), "provenance": kind, "release": release}


def get_web(url):
    with urlopen(Request(url, headers={"User-Agent": "FM1-Manager-To-USB / josxtm (catalog refresh)"}), timeout=30) as reply:
        data = reply.read(5 * 1024 * 1024 + 1)
    if len(data) > 5 * 1024 * 1024:
        raise ValueError("metadata too large")
    return data


def gh(path):
    for attempt in range(3):
        proc = subprocess.run(["gh", "api", path], capture_output=True, check=False, timeout=30)
        if not proc.returncode:
            return json.loads(proc.stdout)
        if b"HTTP 404" in proc.stderr or b"HTTP 451" in proc.stderr:
            return []
        if b"HTTP 50" not in proc.stderr:
            break
        time.sleep(attempt + 1)
    raise RuntimeError(f"GitHub metadata failed for {path}: {proc.stderr.decode(errors='replace')[:250]}")


def published_author(row, releases):
    """Match the hub's public asset ID or verbatim digest; never guess an asset."""
    reference = (row.get("asset") or "").split(" ", 1)[0]
    versions = []
    for release in releases:
        if release.get("draft"):
            continue
        for a in release.get("assets", []):
            if ((reference and str(a.get("id")) == reference) or
                    a.get("digest") == f"sha256:{row.get('sha256', '')}"):
                candidate = asset(a, row["version"], release["html_url"], "AUTHOR")
                if candidate:
                    versions.append(candidate)
    return versions[:1]


def manifest(hub, collection, builds, coll_releases, editor, author):
    known = {r["repo"].lower(): r for r in collection["firmwares"] if REPO.fullmatch(r.get("repo", ""))}
    links = EditorLinks()
    links.feed(editor)
    rows = []
    repo_ids = set()
    seen_hashes = set()
    coll_assets = {}
    for rel in coll_releases:
        if rel.get("draft"):
            continue
        for a in rel.get("assets", []):
            candidate = asset(a, rel["tag_name"], rel["html_url"], "COMMUNITY")
            if candidate:
                coll_assets.setdefault(a["name"], candidate)
    for row in hub["firmwares"]:
        repo = row.get("repo", "")
        if not REPO.fullmatch(repo):
            continue
        lic = row.get("lic") or "Sin licencia verificada"
        packages = [] if row.get("linkOnly") or not LICENSE.fullmatch(lic) else published_author(row, author.get(repo.lower(), []))
        status = "AUTHOR" if packages else "RESTRICTED" if row.get("linkOnly") or not LICENSE.fullmatch(lic) else "NO_DIRECT"
        if packages:
            if packages[0]["sha256"] in seen_hashes:
                packages = []
                status = "DUPLICATE"
            else:
                seen_hashes.add(packages[0]["sha256"])
        repo_ids.add(repo.lower())
        kinds = row.get("kind", [])
        rows.append({"id": "hub/" + row["id"], "name": row["name"], "author": row["author"],
                     "repo": repo, "source": public_url(row.get("source")) or f"https://github.com/{repo}",
                     "license": lic, "description": (row.get("what") or "")[:700],
                     "genres": [kinds] if isinstance(kinds, str) else kinds, "status": status,
                     "version": row.get("version") or "",
                     "compatibility": "FM-1 · flash " + (row.get("chip") or "sin datos"),
                     "identity": row.get("product") or "", "image": public_url(row.get("card"), HUB + "/"),
                     "demo": public_url(row.get("play")), "packages": packages,
                     "variantOf": known.get(repo.lower(), {}).get("fork_of"),
                     "discovered": ["The Firmware Hub"] + (["FM1 Editor"] if repo.lower() in links.projects else [])})
    for key, r in sorted(known.items()):
        repo, description = r["repo"], r.get("description") or ""
        lic = r.get("license") or "Sin licencia verificada"
        build = builds.get(r["id"], {})
        parent = known.get((r.get("fork_of") or "").lower(), {})
        if key in repo_ids and any(row["repo"].lower() == key and row["packages"] for row in rows):
            continue
        candidate = []
        if OTHER.search(description):
            status = "INCOMPATIBLE"
        elif TOOLS.search(description) or key == "m-vave/fm1_open":
            status = "TOOL"
        elif not r.get("redistributable") or not LICENSE.fullmatch(lic):
            status = "RESTRICTED"
        elif parent and (parent.get("head") == r.get("head") or
                         parent["repo"].split("/")[1].lower() == repo.split("/")[1].lower()):
            status = "FORK"
        elif build.get("status") == "success" and build.get("repo", "").lower() == key:
            for name in build.get("assets", []):
                a = coll_assets.get(name)
                if a and a["format"] == "FWSC" and a["sha256"] not in seen_hashes:
                    candidate.append({**a, "version": build["commit"][:7], "revision": build["commit"]})
                    seen_hashes.add(a["sha256"])
            status = "COMMUNITY" if candidate else "NO_DIRECT"
        elif r.get("fork_of"):
            status = "FORK"
        elif r.get("buildable"):
            status = "SOURCE"
        elif r.get("upstream_releases") or r.get("tree_fwsc"):
            status = "UNVERIFIED"
        else:
            status = "NO_DIRECT"
        if key in repo_ids and not candidate:
            continue
        rows.append({"id": "github/" + key, "name": repo.split("/")[1], "author": repo.split("/")[0],
                     "repo": repo, "source": f"https://github.com/{repo}", "license": lic,
                     "description": description[:700], "genres": [], "status": status,
                     "version": candidate[0]["version"] if candidate else "",
                     "compatibility": "Perfil declarado FM-1; sin validar" if candidate else "Sin verificar",
                     "identity": "", "image": None, "demo": None, "packages": candidate, "variantOf": r.get("fork_of"),
                     "discovered": ["2bbb/fm1-firmware-collection"] + (["FM1 Editor"] if key in links.projects else [])})
    if not any(row["repo"].lower() == "m-vave/fm1_open" for row in rows):
        rows.append({"id":"github/m-vave/fm1_open", "name":"FM1 Open V14", "author":"M-VAVE",
                     "repo":"m-vave/fm1_open", "source":"https://github.com/m-vave/fm1_open/releases/tag/V14",
                     "license":"NOASSERTION", "description":"Development Kit y Workbench publicados por M-VAVE.",
                     "genres":[], "status":"TOOL", "version":"V14", "compatibility":"Kit de desarrollo",
                     "identity":"", "image":None, "demo":None, "packages":[], "variantOf":None, "discovered":["M-VAVE"]})
    rows.sort(key=lambda r: (r["name"].casefold(), r["id"]))
    counts = dict(sorted(Counter(r["status"] for r in rows).items()))
    return {"schema": 1, "updated": hub["updated"], "attribution": "Firmware list: The Firmware Hub – thefirmwarehub.com (CC BY 4.0; normalizada)",
            "collection": COLLECTION_URL, "counts": counts, "uniquePackages": len(seen_hashes), "entries": rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--snapshot-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    d = args.snapshot_dir
    d.mkdir(parents=True, exist_ok=True)

    def cached(name, fetch):
        path = d / name
        if not path.exists():
            path.write_bytes(fetch())
        return path.read_bytes()

    def load(name, fetch):
        return json.loads(cached(name, fetch))

    hub = load("catalog.json", lambda: get_web(HUB + "/catalog.json"))
    raw = f"https://raw.githubusercontent.com/{COLLECTION}/main/catalog/"
    coll = load("collection.json", lambda: get_web(raw + "firmwares.json"))
    builds = load("builds.json", lambda: get_web(raw + "builds.json"))
    releases = load("coll-releases.json", lambda: json.dumps(gh(f"repos/{COLLECTION}/releases?per_page=100")).encode())
    editor = cached("fm1-editor.html", lambda: get_web(EDITOR)).decode()
    repositories = sorted({r["repo"].lower() for r in hub["firmwares"] if REPO.fullmatch(r.get("repo", ""))
                           and not r.get("linkOnly") and LICENSE.fullmatch(r.get("lic") or "")})

    unavailable = []

    def author(repo):
        try:
            return repo, load("gh-" + repo.replace("/", "__") + ".json",
                              lambda: json.dumps(gh(f"repos/{repo}/releases?per_page=12")).encode())
        except (RuntimeError, subprocess.TimeoutExpired):
            unavailable.append(repo)
            return repo, []

    with ThreadPoolExecutor(max_workers=6) as pool:
        result = manifest(hub, coll, builds, releases, editor, dict(pool.map(author, repositories)))
    result["unavailableSources"] = sorted(unavailable)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, separators=(",", ":")) + "\n", encoding="utf-8")
    print(json.dumps({"entries": len(result["entries"]), "counts": result["counts"],
                      "uniquePackages": result["uniquePackages"]}, sort_keys=True))


if __name__ == "__main__":
    main()
