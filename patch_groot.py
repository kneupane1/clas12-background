#!/usr/bin/env python3
"""
patch_groot.py  –  patch GROOT 4.0.5 to suppress the "×N" color-axis exponent.

In GraphicsAxis$GraphicsAxisTicks.processAxisExponent() GROOT checks
whether tick-label strings have > 2 trailing zeros, and if so prepends
an "x3" (or xN) multiplier to the color bar, compressing labels like
"14000" into "14  x3".

The fix: change the conditional branch (if_icmple, opcode 0xA4) that
skips the exponent when minZeros ≤ 2 into an unconditional jump (goto,
opcode 0xA7).  One byte change → the skip always fires → no exponent.

Bytecode context in processAxisExponent (offsets 5-9):
  5:  iload_1        (0x1B)   ← minZeros
  6:  iconst_2       (0x05)   ← threshold
  7:  if_icmple +93  (0xA4 0x00 0x5D)  → change 0xA4 to 0xA7 (goto)

Usage (run from any directory):
    python3 patch_groot.py
"""

import os, shutil, zipfile, struct

JAR = os.path.expanduser(
    "~/.m2/repository/org/jlab/groot/4.0.5/groot-4.0.5.jar"
)
BACKUP = JAR + ".bak"
CLASS  = "org/jlab/groot/graphics/GraphicsAxis$GraphicsAxisTicks.class"

# bytes[5..9] of processAxisExponent
PATTERN     = bytes([0x1B, 0x05, 0xA4, 0x00, 0x5D])  # iload_1 iconst_2 if_icmple +93
REPLACEMENT = bytes([0x1B, 0x05, 0xA7, 0x00, 0x5D])  # iload_1 iconst_2 goto    +93

def patch():
    # ── back up original ────────────────────────────────────────────────────
    if not os.path.exists(BACKUP):
        shutil.copy2(JAR, BACKUP)
        print(f"Backed up original jar to {BACKUP}")
    else:
        print(f"Backup already exists: {BACKUP}")

    # ── read class bytes ─────────────────────────────────────────────────────
    with zipfile.ZipFile(JAR, "r") as zin:
        raw = zin.read(CLASS)

    if PATTERN not in raw:
        if REPLACEMENT in raw:
            print("Jar already patched – nothing to do.")
        else:
            print("ERROR: expected byte pattern not found.  "
                  "GROOT version may differ; inspect manually.")
        return

    patched = raw.replace(PATTERN, REPLACEMENT, 1)
    assert patched != raw, "replace had no effect"

    # ── rebuild jar with patched class ──────────────────────────────────────
    tmp = JAR + ".tmp"
    with zipfile.ZipFile(JAR, "r") as zin, \
         zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            if item.filename == CLASS:
                zout.writestr(item, patched)
            else:
                zout.writestr(item, zin.read(item.filename))

    os.replace(tmp, JAR)
    print(f"Patched {CLASS}")
    print(f"  {PATTERN.hex(' ')}  →  {REPLACEMENT.hex(' ')}")
    print("Done.  Rebuild / rerun the background analysis to see plain tick labels.")

if __name__ == "__main__":
    patch()
