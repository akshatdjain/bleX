"""
cleanup_graphify.py — Delete graphify-out noise, keep only what's needed.
"""
import shutil
from pathlib import Path

OUT = Path("O:/blex/graphify-out")

# ── Files to delete individually ─────────────────────────────────────────────
DELETE_FILES = [
    ".graphify_uncached.txt",
    ".graphify_labels.json",
    ".graphify_chunk_00.json",
]

# ── Patterns to delete ────────────────────────────────────────────────────────
DELETE_GLOBS = [
    ".chunk_*.txt",          # 49 chunk file lists
]

# ── Directories to delete entirely ───────────────────────────────────────────
DELETE_DIRS = [
    "cache",          # 100+ AST cache blobs
    "__pycache__",    # Python bytecode
]

# ── Files to KEEP (safety check) ─────────────────────────────────────────────
KEEP = {
    ".graphify_python",
    ".graphify_detect.json",
    ".graphify_ast.json",
    ".graphify_semantic.json",
    ".graphify_extract.json",
    ".graphify_analysis.json",
    "GRAPH_REPORT.md",
    "graph.json",
    "graph.html",
}

deleted = 0

for name in DELETE_FILES:
    f = OUT / name
    if f.exists():
        f.unlink()
        print(f"  deleted  {name}")
        deleted += 1

for pattern in DELETE_GLOBS:
    for f in OUT.glob(pattern):
        f.unlink()
        print(f"  deleted  {f.name}")
        deleted += 1

for d in DELETE_DIRS:
    p = OUT / d
    if p.exists() and p.is_dir():
        shutil.rmtree(p)
        print(f"  deleted  {d}/")
        deleted += 1

print(f"\nDone — {deleted} items removed.")
print("\nRemaining files:")
for f in sorted(OUT.iterdir()):
    marker = "OK" if f.name in KEEP else "??"
    print(f"  [{marker}]  {f.name}")
