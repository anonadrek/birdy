#!/usr/bin/env python3
"""Fail if any PT_LOAD segment of any .so inside the given APK/AAB is below 16 KB alignment.

Usage: python tools/check_16kb_alignment.py <path.apk|path.aab> [more ...]
Exit 0 = every .so has all PT_LOAD p_align >= 0x4000. Exit 1 = at least one below, or no .so found.

Background: Google Play requires 16 KB page-size support (targetSdk 35+ with native code).
vC125 shipped on a per-version skip. Diagnosis + fix: docs/superpowers/specs/
2026-07-16-i2a-android-litert-16kb-design.md
"""
import struct
import sys
import zipfile

REQUIRED = 0x4000


def pt_load_aligns(blob):
    """Return list of p_align for all PT_LOAD program headers, or None if not ELF."""
    if blob[:4] != b"\x7fELF":
        return None
    is64 = blob[4] == 2
    if is64:
        (e_phoff,) = struct.unpack_from("<Q", blob, 0x20)
        (e_phentsize,) = struct.unpack_from("<H", blob, 0x36)
        (e_phnum,) = struct.unpack_from("<H", blob, 0x38)
    else:
        (e_phoff,) = struct.unpack_from("<I", blob, 0x1C)
        (e_phentsize,) = struct.unpack_from("<H", blob, 0x2A)
        (e_phnum,) = struct.unpack_from("<H", blob, 0x2C)
    aligns = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        (p_type,) = struct.unpack_from("<I", blob, off)
        if p_type == 1:  # PT_LOAD
            fmt, rel = ("<Q", 0x30) if is64 else ("<I", 0x1C)
            (p_align,) = struct.unpack_from(fmt, blob, off + rel)
            aligns.append(p_align)
    return aligns


def main(paths):
    failures, checked = [], 0
    for archive in paths:
        with zipfile.ZipFile(archive) as z:
            for name in z.namelist():
                if not name.endswith(".so"):
                    continue
                aligns = pt_load_aligns(z.read(name))
                if aligns is None:
                    continue
                checked += 1
                worst = min(aligns) if aligns else 0
                ok = worst >= REQUIRED
                print(f"{'OK  ' if ok else 'FAIL'} {archive}!{name}  p_align={hex(worst)}")
                if not ok:
                    failures.append(name)
    print(f"\n{checked} .so checked, {len(failures)} below 16 KB")
    return 1 if failures or checked == 0 else 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1:]))
