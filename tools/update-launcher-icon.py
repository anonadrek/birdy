"""Install Android launcher icons + Play Store hi-res icon from a pre-prepared asset set.

Expected source set (in ~/Downloads or specified --src dir):
  ic_launcher_play_512.png    — 512x512 RGB, composed (bird on cream paper bg)
  ic_launcher_foreground.png  — 432x432 RGBA, bird-only on transparent (Asset Studio adaptive fg)
  ic_launcher_background.png  — 432x432 RGBA, solid paper bg (Asset Studio adaptive bg)
  ic_launcher_monochrome.png  — 432x432 RGBA, white silhouette on transparent (Android 13+ themed icons)

Installs:
  - drawable-nodpi/ic_launcher_foreground.png   (as-is)
  - drawable-nodpi/ic_launcher_background.png   (as-is)
  - drawable-nodpi/ic_launcher_monochrome.png   (as-is)
  - mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png   (resized from 512 composed)
  - docs/play-store/ic_launcher_512.png         (composed 512x512)
  - docs/superpowers/icon-concepts/final/ic_launcher_512.png (canonical source-of-truth)
"""

from pathlib import Path
from PIL import Image

REPO = Path(r"C:\Users\abbea\dev\birdy-bird-scanner")
SRC = Path(r"C:\Users\abbea\Downloads")

LEGACY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def install_drawable(name: str, expected_size: tuple[int, int]) -> None:
    src = SRC / name
    img = Image.open(src)
    assert img.size == expected_size, f"{name}: expected {expected_size}, got {img.size}"
    out = REPO / "androidApp" / "src" / "main" / "res" / "drawable-nodpi" / name
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, format="PNG", optimize=True)
    print(f"wrote {out.relative_to(REPO)} ({img.size[0]}x{img.size[1]} {img.mode})")


def main() -> None:
    install_drawable("ic_launcher_foreground.png", (432, 432))
    install_drawable("ic_launcher_background.png", (432, 432))
    install_drawable("ic_launcher_monochrome.png", (432, 432))

    composed = Image.open(SRC / "ic_launcher_play_512.png")
    assert composed.size == (512, 512), f"composed: expected 512x512, got {composed.size}"

    # Legacy mipmap PNGs (square, composed)
    for density, size in LEGACY_SIZES.items():
        out = REPO / "androidApp" / "src" / "main" / "res" / f"mipmap-{density}" / "ic_launcher.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        composed.resize((size, size), Image.LANCZOS).save(out, format="PNG", optimize=True)
        print(f"wrote {out.relative_to(REPO)} ({size}x{size})")

    # Play Store hi-res
    play_out = REPO / "docs" / "play-store" / "ic_launcher_512.png"
    composed.save(play_out, format="PNG", optimize=True)
    print(f"wrote {play_out.relative_to(REPO)} (512x512)")

    # Canonical source-of-truth
    final_out = REPO / "docs" / "superpowers" / "icon-concepts" / "final" / "ic_launcher_512.png"
    composed.save(final_out, format="PNG", optimize=True)
    print(f"wrote {final_out.relative_to(REPO)} (512x512)")


if __name__ == "__main__":
    main()
