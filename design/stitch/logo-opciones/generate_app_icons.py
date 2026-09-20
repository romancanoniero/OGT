#!/usr/bin/env python3
"""Exporta B01 Sostener (ogt-b01-sostener.png) a todos los tamaños de iOS y Android."""

from pathlib import Path

from PIL import Image, ImageOps

INK = (0x11, 0x12, 0x13, 255)
WHITE = (255, 255, 255, 255)
TRANSPARENT = (0, 0, 0, 0)

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
SOURCE = HERE / "ogt-b01-sostener.png"
IOS = ROOT / "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
ANDROID = ROOT / "composeApp/src/androidMain/res"
STORE = HERE / "store"


def save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGBA").save(path, "PNG")


def square_rgba(src: Image.Image, size: int, *, fill=WHITE) -> Image.Image:
    canvas = Image.new("RGBA", (size, size), fill)
    fitted = ImageOps.contain(src.convert("RGBA"), (size, size), Image.Resampling.LANCZOS)
    x = (size - fitted.width) // 2
    y = (size - fitted.height) // 2
    canvas.alpha_composite(fitted, (x, y))
    return canvas


def punch_white(src: Image.Image, *, threshold: int = 246) -> Image.Image:
    """Deja el trazo; el blanco del PNG original pasa a transparente."""
    img = src.convert("RGBA")
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 8 or (r >= threshold and g >= threshold and b >= threshold):
                px[x, y] = TRANSPARENT
    return img


def tint_opaque(src: Image.Image, rgba: tuple[int, int, int, int]) -> Image.Image:
    img = src.convert("RGBA")
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            _, _, _, a = px[x, y]
            px[x, y] = (*rgba[:3], a) if a > 8 else TRANSPARENT
    return img


def pad_mark(src: Image.Image, size: int, inset: float, *, fill) -> Image.Image:
    inner = max(1, int(size * (1 - 2 * inset)))
    mark = ImageOps.contain(src, (inner, inner), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (size, size), fill)
    x = (size - mark.width) // 2
    y = (size - mark.height) // 2
    canvas.alpha_composite(mark, (x, y))
    return canvas


def main() -> None:
    raw = Image.open(SOURCE).convert("RGBA")
    cut = punch_white(raw)

    master = square_rgba(raw, 1024, fill=WHITE)
    fg = pad_mark(cut, 1024, 0.18, fill=TRANSPARENT)
    mono = pad_mark(tint_opaque(cut, INK), 1024, 0.18, fill=TRANSPARENT)
    notify = pad_mark(tint_opaque(cut, WHITE), 96, 0.08, fill=TRANSPARENT)

    save(master, STORE / "icon-1024.png")
    save(fg, STORE / "icon-foreground-1024.png")
    save(mono, STORE / "icon-mono-1024.png")
    save(square_rgba(raw, 512, fill=WHITE), STORE / "play-store-512.png")

    ios_sizes = {
        "icon-20.png": 20,
        "icon-20@2x.png": 40,
        "icon-20@3x.png": 60,
        "icon-29.png": 29,
        "icon-29@2x.png": 58,
        "icon-29@3x.png": 87,
        "icon-40.png": 40,
        "icon-40@2x.png": 80,
        "icon-40@3x.png": 120,
        "icon-60@2x.png": 120,
        "icon-60@3x.png": 180,
        "icon-76.png": 76,
        "icon-76@2x.png": 152,
        "icon-83.5@2x.png": 167,
        "icon-1024.png": 1024,
    }
    for name, px in ios_sizes.items():
        save(square_rgba(raw, px, fill=WHITE), IOS / name)

    android_launcher = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    android_fg = {
        "mipmap-mdpi": 108,
        "mipmap-hdpi": 162,
        "mipmap-xhdpi": 216,
        "mipmap-xxhdpi": 324,
        "mipmap-xxxhdpi": 432,
    }
    for folder, px in android_launcher.items():
        icon = square_rgba(raw, px, fill=WHITE)
        save(icon, ANDROID / folder / "ic_launcher.png")
        save(icon, ANDROID / folder / "ic_launcher_round.png")
    for folder, px in android_fg.items():
        save(pad_mark(cut, px, 0.18, fill=TRANSPARENT), ANDROID / folder / "ic_launcher_foreground.png")
        save(pad_mark(tint_opaque(cut, INK), px, 0.18, fill=TRANSPARENT), ANDROID / folder / "ic_launcher_monochrome.png")

    save(notify, ANDROID / "drawable" / "ic_stat_ogt.png")
    print("iconos B01 listos")


if __name__ == "__main__":
    main()
