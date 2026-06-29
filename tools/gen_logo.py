#!/usr/bin/env python3
"""
Generate the NEROLOGISTICS mod logo (square), in the shared Neroland family style
(cf. neroland-core/tools/gen_logo.py, nerospace/ and nerotech/): a deep-space starfield,
a glowing faceted central prism, and a beveled glowing wordmark.

NeroLogistics is the transport/automation mod, so the family faceted hexagonal prism is set
inside a glowing **hub-and-spoke network** of routing nodes and links, lit by a green-cyan
**flow** accent — keeping the family palette (teal / steel-blue / cyan) but leading with the
flow colour so it reads as the "logistics" member of the set. Renders supersampled, then
downsamples.

Outputs:
  art/logo/nerologistics_logo.png       (1024x1024 master)
  art/logo/nerologistics_logo_400.png   (CurseForge/Modrinth-ready)
  common/src/main/resources/nerologistics_logo.png  (256x256 in-game mods-list icon)
"""
import math
import os
import random
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "art/logo")
ICON = os.path.join(ROOT, "common/src/main/resources")
os.makedirs(OUT, exist_ok=True)
os.makedirs(ICON, exist_ok=True)

FINAL = 1024
SS = 2
R = FINAL * SS
rng = random.Random(23)

# Neroland family palette + NeroLogistics' green-cyan flow accent
NERO_ALLOY = (38, 166, 154)    # teal
STARSTEEL = (140, 178, 208)    # steel-blue
PLASMA = (96, 212, 232)        # cyan
STEEL = (122, 132, 146)        # machine casing
STEEL_DK = (66, 74, 86)
FLOW = (94, 214, 160)          # logistics flow (green-cyan)
FLOW_BRIGHT = (176, 246, 210)
BRIGHT = (235, 250, 244)


def _font(size):
    for path in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
        "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf",
    ):
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    try:
        import matplotlib.font_manager as fm
        return ImageFont.truetype(fm.findfont("DejaVu Sans:bold"), size)
    except Exception:
        return ImageFont.load_default()


def background():
    top = np.array([6, 11, 17], float)
    bot = np.array([12, 19, 26], float)
    yy = np.linspace(0, 1, R)[:, None, None]
    img = top[None, None, :] * (1 - yy) + bot[None, None, :] * yy
    img = np.repeat(img, R, axis=1)
    Y, X = np.mgrid[0:R, 0:R].astype(float)

    def glow(cx, cy, rad, color, strength):
        d = np.sqrt((X - cx) ** 2 + (Y - cy) ** 2)
        f = np.clip(1 - d / rad, 0, 1) ** 2 * strength
        for c in range(3):
            img[:, :, c] += color[c] * f

    glow(R * 0.28, R * 0.30, R * 0.55, (20, 80, 84), 0.42)    # teal nebula (family)
    glow(R * 0.76, R * 0.72, R * 0.55, (24, 96, 70), 0.44)    # flow nebula (NeroLogistics)
    glow(R * 0.5, R * 0.5, R * 0.42, (24, 44, 60), 0.28)

    d = np.sqrt((X - R / 2) ** 2 + (Y - R / 2) ** 2) / (R * 0.72)
    vig = np.clip(1 - (d ** 2) * 0.85, 0.25, 1)
    img *= vig[:, :, None]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").convert("RGBA")


def add_stars(base):
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for _ in range(460):
        x, y = rng.randint(0, R), rng.randint(0, R)
        s = rng.choice([1, 1, 1, 2, 2, 3]) * SS
        b = rng.randint(120, 255)
        tint = rng.choice([(b, b, b), (b, 255, 255), (190, 255, 220), (200, 200, 255)])
        d.ellipse([x, y, x + s, y + s], fill=tint + (rng.randint(120, 255),))
    base.alpha_composite(layer.filter(ImageFilter.GaussianBlur(2 * SS)))
    base.alpha_composite(layer)
    return base


def soft_glow(draw_fn, blur):
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(layer))
    return layer.filter(ImageFilter.GaussianBlur(blur))


def emblem(base, cx, cy, rad):
    # flow + teal aura
    base.alpha_composite(soft_glow(
        lambda dr: dr.ellipse([cx - rad * 1.9, cy - rad * 1.9, cx + rad * 1.9, cy + rad * 1.9],
                              fill=(30, 130, 95, 150)), 34 * SS))
    base.alpha_composite(soft_glow(
        lambda dr: dr.ellipse([cx - rad * 1.3, cy - rad * 1.3, cx + rad * 1.3, cy + rad * 1.3],
                              fill=(40, 150, 150, 120)), 18 * SS))

    # hub-and-spoke network: a ring + six routing nodes linked to the central prism
    node_r = rad * 1.62
    nodes = [(cx + math.cos(math.radians(60 * i - 90)) * node_r,
              cy + math.sin(math.radians(60 * i - 90)) * node_r) for i in range(6)]
    gl = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    gd = ImageDraw.Draw(gl)
    # orbit ring
    gd.ellipse([cx - node_r, cy - node_r, cx + node_r, cy + node_r],
               outline=(70, 150, 130, 200), width=SS * 2)
    # spokes + ring links
    for i in range(6):
        gd.line([(cx, cy), nodes[i]], fill=FLOW + (170,), width=SS * 2)
        gd.line([nodes[i], nodes[(i + 1) % 6]], fill=(80, 170, 150, 150), width=SS)
    base.alpha_composite(gl.filter(ImageFilter.GaussianBlur(SS)))
    base.alpha_composite(gl)
    # node pucks
    nl = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    nd = ImageDraw.Draw(nl)
    nr = rad * 0.30
    for (nx, ny) in nodes:
        nd.ellipse([nx - nr, ny - nr, nx + nr, ny + nr], fill=STEEL_DK + (255,))
        nd.ellipse([nx - nr, ny - nr, nx + nr, ny + nr], outline=FLOW + (235,), width=SS * 2)
        nd.ellipse([nx - nr * 0.4, ny - nr * 0.4, nx + nr * 0.4, ny + nr * 0.4], fill=FLOW_BRIGHT + (255,))
    base.alpha_composite(nl)

    # faceted hexagonal prism (family motif), lit with the flow accent
    hexpts = [(cx + math.cos(math.radians(60 * i - 90)) * rad,
               cy + math.sin(math.radians(60 * i - 90)) * rad) for i in range(6)]
    layer = Image.new("RGBA", (R, R), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    facet_cols = [STARSTEEL, NERO_ALLOY, FLOW, STARSTEEL, NERO_ALLOY, FLOW]
    for i in range(6):
        shade = 0.58 + 0.42 * (i / 5.0)
        col = tuple(int(c * shade) for c in facet_cols[i])
        d.polygon([(cx, cy), hexpts[i], hexpts[(i + 1) % 6]], fill=col + (255,))
    # bright flow energy core
    ir = rad * 0.36
    d.ellipse([cx - ir, cy - ir, cx + ir, cy + ir], fill=FLOW_BRIGHT + (255,))
    d.ellipse([cx - ir * 0.5, cy - ir * 0.5, cx + ir * 0.5, cy + ir * 0.5], fill=BRIGHT + (255,))
    for i in range(6):
        d.line([hexpts[i], hexpts[(i + 1) % 6]], fill=(230, 245, 240, 235), width=max(1, SS * 2))
        d.line([(cx, cy), hexpts[i]], fill=(220, 235, 230, 150), width=max(1, SS))
    base.alpha_composite(layer)

    # specular sparkle
    sx, sy = cx - rad * 0.16, cy - rad * 0.46
    base.alpha_composite(soft_glow(
        lambda dr: dr.ellipse([sx - 9 * SS, sy - 9 * SS, sx + 9 * SS, sy + 9 * SS],
                              fill=(255, 255, 255, 255)), 5 * SS))
    dd = ImageDraw.Draw(base)
    L = 18 * SS
    dd.line([sx - L, sy, sx + L, sy], fill=(255, 255, 255, 230), width=SS * 2)
    dd.line([sx, sy - L, sx, sy + L], fill=(255, 255, 255, 230), width=SS * 2)
    return base


def wordmark(base):
    big = _font(int(R * 0.098))
    tagf = _font(int(R * 0.028))

    def centered(text, font, y, fill, glow=None):
        w = ImageDraw.Draw(base).textlength(text, font=font)
        x = (R - w) / 2
        if glow:
            gl = Image.new("RGBA", (R, R), (0, 0, 0, 0))
            ImageDraw.Draw(gl).text((x, y), text, font=font, fill=glow)
            base.alpha_composite(gl.filter(ImageFilter.GaussianBlur(9 * SS)))
            base.alpha_composite(gl.filter(ImageFilter.GaussianBlur(3 * SS)))
        out = Image.new("RGBA", (R, R), (0, 0, 0, 0))
        ImageDraw.Draw(out).text((x, y), text, font=font, fill=(10, 12, 16, 255))
        base.alpha_composite(out.filter(ImageFilter.MaxFilter(2 * SS + 1)))
        ImageDraw.Draw(base).text((x, y), text, font=font, fill=fill)

    centered("NEROLOGISTICS", big, int(R * 0.71), (244, 252, 248, 255), glow=(94, 214, 160, 255))

    tag = "T R A N S P O R T   ·   N E T W O R K S   ·   L O G I S T I C S"
    tw = ImageDraw.Draw(base).textlength(tag, font=tagf)
    ImageDraw.Draw(base).text(((R - tw) / 2, int(R * 0.858)), tag, font=tagf, fill=(176, 224, 206, 255))
    return base


def main():
    img = background()
    img = add_stars(img)
    cx, cy, rad = int(R * 0.5), int(R * 0.355), int(R * 0.120)
    img = emblem(img, cx, cy, rad)
    img = wordmark(img)

    final = img.convert("RGB").resize((FINAL, FINAL), Image.LANCZOS)
    p1 = os.path.join(OUT, "nerologistics_logo.png")
    p2 = os.path.join(OUT, "nerologistics_logo_400.png")
    p3 = os.path.join(ICON, "nerologistics_logo.png")
    final.save(p1)
    final.resize((400, 400), Image.LANCZOS).save(p2)
    final.resize((256, 256), Image.LANCZOS).save(p3)
    for p in (p1, p2, p3):
        print("wrote", os.path.relpath(p, ROOT))


if __name__ == "__main__":
    main()
