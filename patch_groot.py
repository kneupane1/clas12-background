#!/usr/bin/env python3
"""
patch_groot.py  –  patch GROOT 4.0.5 to fix two display issues.

Patch 1: Suppress the "×N" color-axis exponent
  In GraphicsAxis$GraphicsAxisTicks.processAxisExponent() GROOT checks
  whether tick-label strings have > 2 trailing zeros, and if so prepends
  an "x3" (or xN) multiplier to the color bar.
  Fix: change if_icmple (0xA4) → goto (0xA7) so the skip always fires.

Patch 2: Widen the color bar from 8 px to 30 px
  GraphicsAxis.drawColorAxis() uses hardcoded bipush 8 (0x10 0x08) for
  both the fillRect and drawRect calls that draw the color bar.
  Fix: change the width argument from 8 to 30 in both calls.

Usage (run from any directory BEFORE mvn install):
    python3 patch_groot.py
"""

import os, shutil, zipfile

JAR = os.path.expanduser(
    "~/.m2/repository/org/jlab/groot/4.0.5/groot-4.0.5.jar"
)
BACKUP = JAR + ".bak"

# ── Patch 1: suppress ×N exponent ───────────────────────────────────────────
CLASS_TICKS = "org/jlab/groot/graphics/GraphicsAxis$GraphicsAxisTicks.class"
# bytes[5..9] of processAxisExponent
PATTERN_EXP     = bytes([0x1B, 0x05, 0xA4, 0x00, 0x5D])  # iload_1 iconst_2 if_icmple +93
REPLACEMENT_EXP = bytes([0x1B, 0x05, 0xA7, 0x00, 0x5D])  # iload_1 iconst_2 goto    +93

# ── Patch 2: widen color bar 8 px → 30 px ───────────────────────────────────
CLASS_AXIS = "org/jlab/groot/graphics/GraphicsAxis.class"
BAR_WIDTH = 30  # desired color bar width in pixels

# fillRect call: isub(64) bipush(10) 8(08) iload14(15 0e) invokevirtual-fillRect(b6 01 82)
PATTERN_FILL     = bytes([0x64, 0x10, 0x08, 0x15, 0x0e, 0xb6, 0x01, 0x82])
REPLACEMENT_FILL = bytes([0x64, 0x10, BAR_WIDTH, 0x15, 0x0e, 0xb6, 0x01, 0x82])

# drawRect call: bipush(10) 8(08) aload_0(2a) getfield ... invokevirtual-drawRect(b6 01 85)
PATTERN_DRAW     = bytes([0x10, 0x08, 0x2a, 0xb4, 0x00, 0x0c, 0xb6, 0x00, 0x5f,
                          0xb6, 0x01, 0x7b, 0xb8, 0x00, 0xb6, 0x8e, 0xb6, 0x01, 0x85])
REPLACEMENT_DRAW = bytes([0x10, BAR_WIDTH, 0x2a, 0xb4, 0x00, 0x0c, 0xb6, 0x00, 0x5f,
                          0xb6, 0x01, 0x7b, 0xb8, 0x00, 0xb6, 0x8e, 0xb6, 0x01, 0x85])


def apply_patch(raw, pattern, replacement, label):
    if pattern not in raw:
        if replacement in raw:
            print(f"  [{label}] already patched – skipping.")
            return raw, False
        else:
            print(f"  [{label}] ERROR: pattern not found. GROOT version may differ.")
            return raw, False
    patched = raw.replace(pattern, replacement, 1)
    assert patched != raw
    print(f"  [{label}] patched: {pattern.hex(' ')}  →  {replacement.hex(' ')}")
    return patched, True


def patch():
    if not os.path.exists(BACKUP):
        shutil.copy2(JAR, BACKUP)
        print(f"Backed up original jar to {BACKUP}")
    else:
        print(f"Backup already exists: {BACKUP}")

    # Read both classes
    with zipfile.ZipFile(JAR, "r") as zin:
        raw_ticks = zin.read(CLASS_TICKS)
        raw_axis  = zin.read(CLASS_AXIS)

    print("\nApplying patches:")
    raw_ticks, _ = apply_patch(raw_ticks, PATTERN_EXP,   REPLACEMENT_EXP,   "exponent suppress")
    raw_axis,  _ = apply_patch(raw_axis,  PATTERN_FILL,  REPLACEMENT_FILL,  f"fillRect  width→{BAR_WIDTH}px")
    raw_axis,  _ = apply_patch(raw_axis,  PATTERN_DRAW,  REPLACEMENT_DRAW,  f"drawRect  width→{BAR_WIDTH}px")

    # Rebuild jar with patched classes
    tmp = JAR + ".tmp"
    with zipfile.ZipFile(JAR, "r") as zin, \
         zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            if item.filename == CLASS_TICKS:
                zout.writestr(item, raw_ticks)
            elif item.filename == CLASS_AXIS:
                zout.writestr(item, raw_axis)
            else:
                zout.writestr(item, zin.read(item.filename))

    os.replace(tmp, JAR)
    print(f"\nDone. Rebuild: mvn install -q")


if __name__ == "__main__":
    patch()
