from __future__ import annotations

import base64
import gzip
import io
import tarfile
from pathlib import Path

# S10 validation trigger: workflow exists before this push.
ROOT = Path(__file__).resolve().parent
parts = "".join(path.read_text() for path in sorted((ROOT / ".s10-payload").glob("part-*")))
raw = gzip.decompress(base64.b64decode(parts))
with tarfile.open(fileobj=io.BytesIO(raw), mode="r:") as archive:
    for member in archive.getmembers():
        target = (ROOT / member.name).resolve()
        if target != ROOT and ROOT not in target.parents:
            raise RuntimeError(f"Unsafe payload path: {member.name}")
    archive.extractall(ROOT, filter="data")
print("Applied reviewed S10 payload")
