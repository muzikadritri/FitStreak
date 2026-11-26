#!/usr/bin/env bash
# Rendert das freigestellte Bild der 3D-Figur (für leere Diagramme)
# mit dem Software-Renderer aus tools/desktop-check und legt sie als WebP in die App.
set -euo pipefail
cd "$(dirname "$0")/../desktop-check"
tmp="$(mktemp -d)"
gradle :preview:run --args="art $tmp" -q --console=plain
python3 - "$tmp" ../../app/src/main/res/drawable-nodpi <<'PY'
import glob, os, sys
from PIL import Image
src, dst = sys.argv[1], sys.argv[2]
os.makedirs(dst, exist_ok=True)
for path in sorted(glob.glob(os.path.join(src, "art_*.png"))):
    name = os.path.splitext(os.path.basename(path))[0]
    out = os.path.join(dst, name + ".webp")
    Image.open(path).save(out, "WEBP", quality=90, method=6)
    print(f"{name}.webp: {os.path.getsize(out) // 1024} kB")
PY
rm -rf "$tmp"
