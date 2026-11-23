"""Catálogo local y diagnóstico USB de solo lectura para la Zero W."""
import argparse
import hashlib
import html
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from urllib.parse import urlsplit


def attribute(path):
    try:
        return path.read_text().strip()
    except OSError:
        return ""


def usb_status(root):
    for inquiry in root.glob("*/device/model"):
        model = attribute(inquiry)
        vendor = attribute(inquiry.with_name("vendor"))
        if (vendor, model) == ("WL82", "UBOOT1.00"):
            return "Dispositivo WL82 UBOOT1.00 detectado en modo Download. Identidad de flash pendiente."
    return "Sin dispositivo WL82 UBOOT1.00 identificado en modo Download."


def catalog(folder):
    rows = []
    for file in sorted(folder.iterdir(), key=lambda p: p.name.casefold()):
        if file.is_symlink() or not file.is_file() or file.suffix.lower() != ".fwsc":
            continue
        with file.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        rows.append(f"<li><strong>{html.escape(file.name)}</strong><br>{file.stat().st_size} bytes"
                    f"<details><summary>SHA-256</summary><code>{digest}</code></details>"
                    "<small>Compatibilidad pendiente. Instalación bloqueada.</small></li>")
    return "<ul>" + "".join(rows) + "</ul>" if rows else "<p>La carpeta no contiene paquetes .fwsc.</p>"


class Page(BaseHTTPRequestHandler):
    def do_GET(self):
        if urlsplit(self.path).path != "/":
            self.send_error(404)
            return
        try:
            files = catalog(self.server.folder)
        except OSError:
            files = "<p>Carpeta de firmwares inaccesible. Comprueba FM1DATA.</p>"
        body = ("<!doctype html><html lang=es><meta charset=utf-8>"
                "<meta name=viewport content='width=device-width,initial-scale=1'>"
                "<title>FM-1 · Zero W</title><style>body{font:18px system-ui;max-width:42rem;"
                "margin:auto;padding:1rem;background:#12181a;color:#f4f8f8}li{margin:1em 0}"
                "code{overflow-wrap:anywhere}a{color:#74e4cf}</style>"
                "<h1>FM-1 · Zero W</h1><p>Conexión con la Zero establecida.</p>"
                f"<p>{usb_status(Path('/sys/class/scsi_generic'))}</p>"
                f"<h2>Firmwares de la tarjeta</h2>{files}<a href='/'>Actualizar</a></html>").encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", type=Path, default=Path("/mnt/FM1DATA/FM1/FIRMWARES"))
    parser.add_argument("--bind", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8080)
    args = parser.parse_args()
    with HTTPServer((args.bind, args.port), Page) as server:
        server.folder = args.folder
        server.serve_forever()
