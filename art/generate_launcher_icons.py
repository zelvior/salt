#!/usr/bin/env python3
"""
Salt — launcher icon pipeline.

Derives EVERY launcher/app icon from the single master asset `art/salt-logo.png`
(1024x1024 full-bleed square, emblem inside the adaptive-icon safe zone).

Rule: the master is the only hand-made image. Never edit the derived files
directly — tweak this script and re-run:

    python3 art/generate_launcher_icons.py

Outputs:
  art/salt-emblem.png                              emblem on transparency (512)
  art/playstore-icon.png                           Play Store listing icon (512)
  res/mipmap-*/ic_launcher_foreground.webp         adaptive foreground layer (emblem, 66% safe zone)
  res/mipmap-*/ic_launcher_monochrome.webp         Android 13+ themed-icon layer (white alpha)
  res/mipmap-*/ic_launcher.webp                    legacy static (rounded-square masked)
  res/mipmap-*/ic_launcher_round.webp              legacy static (circle masked)
  res/drawable-nodpi/salt_logo.webp                in-app brand mark (transparent emblem)
"""

import os
import sys
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MASTER = os.path.join(ROOT, "art", "salt-logo.png")
RES = os.path.join(ROOT, "app", "src", "main", "res")

ADAPTIVE_BINS = {  # 108dp canvas rendered at each density (432 = xxxhdpi)
    "mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432,
}
LEGACY_BINS = {  # classic 48dp launcher icon
    "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192,
}
SAFE_ZONE = 0.66  # emblem occupies 66% of the adaptive canvas (72/108dp)


def luminance(img):
    """Grayscale luminance channel (Rec. 709)."""
    r, g, b = img.convert("RGB").split()
    out = Image.new("L", img.size)
    px = out.load()
    px_r, px_g, px_b = r.load(), g.load(), b.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            px[x, y] = (px_r[x, y] * 2126 + px_g[x, y] * 7152 + px_b[x, y] * 722) // 10000
    return out


def _band_profile(L, axis, threshold, window=None):
    """First/last index along `axis` with any luminance > threshold.

    window=(a0,a1) restricts the scan on the other axis first. Returns (lo, hi).
    """
    w, h = L.size
    px = L.load()
    if axis == "y":
        rng, other = h, w
    else:
        rng, other = w, h
    hit = [False] * rng
    for i in range(rng):
        for j in range(0, other, 2):
            v = px[j, i] if axis == "y" else px[i, j]
            if window is not None and not (window[0] <= j <= window[1]):
                continue
            if v > threshold:
                hit[i] = True
                break
    idx = [i for i, v in enumerate(hit) if v]
    return (idx[0], idx[-1]) if idx else (0, rng - 1)


def extract_emblem(master):
    """Cut the glowing cube off the dark background, excluding the floor reflection.

    The cube's rim highlights exceed luminance 200 while the reflection tops out
    around 180 — but the reflection's bright top edge merges with the cube's
    vertex bloom, so the L>200 band alone overshoots. The gap between cube and
    reflection is genuinely dark though, so after building the alpha ramp we
    scan upward for the first run of empty rows below the cube and cut there.
    """
    img = master.convert("RGB")
    L = luminance(img)
    alpha_full = L.point(
        lambda v: 0 if v <= 30 else 255 if v >= 78 else int((v - 30) * 255 / (78 - 30))
    )
    ap = alpha_full.load()
    w, h = img.size

    y0, y1 = _band_profile(L, "y", 200)
    x0, x1 = _band_profile(L, "x", 200, window=(y0, y1))

    # The cube tapers to its bottom vertex, then the mirrored reflection
    # widens again. In alpha-width terms that's a local minimum followed by
    # a rise. Search the lower half of the band for that minimum and cut at
    # the end of the dip — everything below is reflection.
    mid = y0 + (y1 - y0) // 2
    counts = {
        yy: sum(1 for x in range(0, w, 2) if ap[x, yy] > 80)
        for yy in range(mid, y1 + 1)
    }
    min_row = min(counts, key=counts.get)
    min_val = counts[min_row]
    cut = min_row
    while cut + 1 <= y1 and counts.get(cut + 1, 1 << 30) <= min_val + 8:
        cut += 1
    print(f"cube band: x[{x0},{x1}] y[{y0},{y1}] -> reflection cut at y={cut}")

    # Side/top padding keeps the soft glow; the bottom is the detected gap.
    pad = int(max(x1 - x0, cut - y0) * 0.06)
    x0p, y0p = max(0, x0 - pad), max(0, y0 - pad)
    x1p = min(w - 1, x1 + pad)

    emblem = img.crop((x0p, y0p, x1p + 1, cut + 1)).convert("RGBA")
    alpha = alpha_full.crop((x0p, y0p, x1p + 1, cut + 1))
    emblem.putalpha(alpha)

    # Feather the bottom edge so the vertex bloom fades instead of hard-cutting.
    fw, fh = emblem.size
    feather = max(2, int(fh * 0.04))
    px = emblem.load()
    for dy in range(feather):
        factor = 1.0 - (dy / feather)
        yy = fh - 1 - dy
        for x in range(fw):
            r, g, b, a = px[x, yy]
            px[x, yy] = (r, g, b, int(a * factor))

    print(f"emblem: {fw}x{fh}")
    return emblem


def paste_centered(canvas, emblem, scale):
    """Scale emblem so its largest side == scale * canvas side, centered."""
    cw, ch = canvas.size
    target = int(min(cw, ch) * scale)
    ew, eh = emblem.size
    if ew >= eh:
        nw, nh = target, max(1, round(target * eh / ew))
    else:
        nh, nw = target, max(1, round(target * ew / eh))
    e = emblem.resize((nw, nh), Image.LANCZOS)
    canvas.alpha_composite(e, ((cw - nw) // 2, (ch - nh) // 2))
    return canvas


def rounded_mask(size, radius_frac, supersample=4):
    s = size * supersample
    m = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(m)
    r = int(s * radius_frac)
    d.rounded_rectangle([0, 0, s - 1, s - 1], radius=r, fill=255)
    return m.resize((size, size), Image.LANCZOS)


def circle_mask(size, supersample=4):
    s = size * supersample
    m = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(m)
    d.ellipse([0, 0, s - 1, s - 1], fill=255)
    return m.resize((size, size), Image.LANCZOS)


def save_webp(img, path, lossless=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "WEBP", quality=90, method=6, lossless=lossless)
    print(f"  {os.path.relpath(path, ROOT)}  {img.size[0]}x{img.size[1]}")


def shape_mask(size, shape, supersample=4):
    """Alpha mask for a launcher shape: circle, squircle, or rounded square."""
    s = size * supersample
    m = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(m)
    if shape == "circle":
        d.ellipse([0, 0, s - 1, s - 1], fill=255)
    elif shape == "squircle":
        d.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.42), fill=255)
    else:
        d.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=255)
    return m.resize((size, size), Image.LANCZOS)


def make_preview(emblem, res_dir, path):
    """Contact sheet: masked adaptive renders + legacy + themed + emblem-on-light."""
    px = 432
    bg = Image.new("RGBA", (px, px), (11, 14, 20, 255))
    fg = Image.open(os.path.join(res_dir, "mipmap-xxxhdpi", "ic_launcher_foreground.webp"))
    mono = Image.open(os.path.join(res_dir, "mipmap-xxxhdpi", "ic_launcher_monochrome.webp"))

    comp = bg.copy()
    comp.alpha_composite(fg.convert("RGBA"))

    def masked(img, shape):
        out = img.convert("RGBA")
        out.putalpha(shape_mask(img.size[0], shape))
        return out

    tiles = [
        (masked(comp, "squircle").resize((200, 200), Image.LANCZOS), "squircle"),
        (masked(comp, "circle").resize((200, 200), Image.LANCZOS), "circle"),
        (masked(comp, "rounded").resize((200, 200), Image.LANCZOS), "rounded"),
        (Image.open(os.path.join(res_dir, "mipmap-xxxhdpi", "ic_launcher.webp"))
            .convert("RGBA").resize((200, 200), Image.LANCZOS), "legacy"),
    ]
    tinted = Image.new("RGBA", (200, 200), (17, 24, 38, 255))
    tinted.alpha_composite(mono.convert("RGBA").resize((132, 132), Image.LANCZOS), (34, 34))
    tiles.append((tinted, "themed"))
    light = Image.new("RGBA", (200, 200), (235, 238, 243, 255))
    light.alpha_composite(emblem.resize((200, 200), Image.LANCZOS))
    tiles.append((light, "emblem@light"))

    sheet = Image.new("RGBA", (6 * 200 + 7 * 24, 200 + 2 * 24 + 30), (24, 26, 31, 255))
    x = 24
    for img, label in tiles:
        sheet.alpha_composite(img, (x, 24))
        ImageDraw.Draw(sheet).text((x + 62, 200 + 28), label, fill=(180, 186, 196, 255))
        x += 224
    sheet.convert("RGB").save(path)
    print(f"  art/{os.path.basename(path)}  {sheet.size[0]}x{sheet.size[1]}")


def main():
    master = Image.open(MASTER)
    print(f"master: {master.size[0]}x{master.size[1]} {master.mode}")
    if master.size[0] != master.size[1]:
        sys.exit("Master must be square")
    master = master.resize((1024, 1024), Image.LANCZOS) if master.size != (1024, 1024) else master

    emblem = extract_emblem(master)

    # --- standalone art -----------------------------------------------------
    art512 = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    paste_centered(art512, emblem, 0.92)
    art512.save(os.path.join(ROOT, "art", "salt-emblem.png"))
    print("  art/salt-emblem.png 512x512")

    play = master.convert("RGB").resize((512, 512), Image.LANCZOS)
    play.save(os.path.join(ROOT, "art", "playstore-icon.png"))
    print("  art/playstore-icon.png 512x512")

    # --- adaptive icon layers ----------------------------------------------
    print("adaptive foreground + monochrome:")
    for bin_, px in ADAPTIVE_BINS.items():
        fg = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        paste_centered(fg, emblem, SAFE_ZONE)
        save_webp(fg, os.path.join(RES, f"mipmap-{bin_}", "ic_launcher_foreground.webp"))

        mono = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        white = Image.new("RGBA", emblem.size, (255, 255, 255, 255))
        white.putalpha(emblem.getchannel("A"))
        paste_centered(mono, white, SAFE_ZONE)
        save_webp(mono, os.path.join(RES, f"mipmap-{bin_}", "ic_launcher_monochrome.webp"))

    # --- legacy static icons (non-adaptive fallback paths) ------------------
    # Composed like the adaptive rendering (brand background + emblem) so the
    # icon reads identically on old launchers, then masked per shape.
    print("legacy statics:")
    for bin_, px in LEGACY_BINS.items():
        full = Image.new("RGBA", (px, px), (11, 14, 20, 255))
        paste_centered(full, emblem, 0.62)

        sq = full.copy()
        sq.putalpha(rounded_mask(px, 0.14))
        save_webp(sq, os.path.join(RES, f"mipmap-{bin_}", "ic_launcher.webp"), lossless=(px <= 96))

        rd = full.copy()
        rd.putalpha(circle_mask(px))
        save_webp(rd, os.path.join(RES, f"mipmap-{bin_}", "ic_launcher_round.webp"), lossless=(px <= 96))

    # --- in-app brand mark ---------------------------------------------------
    brand = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    paste_centered(brand, emblem, 0.94)
    save_webp(brand, os.path.join(RES, "drawable-nodpi", "salt_logo.webp"))

    # --- launcher-shape preview sheet ----------------------------------------
    make_preview(emblem, RES, os.path.join(ROOT, "art", "preview-sheet.png"))

    print("done.")


if __name__ == "__main__":
    main()
