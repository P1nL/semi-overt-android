"""Generate the existing procedural cover artwork with only a short bottom blur.
Run: python tools/generate_category_covers.py (requires Pillow). Home assets are untouched.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

OUT = Path(__file__).resolve().parents[1] / "app/src/main/res/drawable-nodpi"
S = 768
SX, SY = S / 372, S / 176


def polygon(draw, points, fill):
    draw.polygon([(round(x * SX), round(y * SY)) for x, y in points], fill=fill)


def line(draw, a, b, fill, width=1):
    draw.line([(round(a[0] * SX), round(a[1] * SY)),
               (round(b[0] * SX), round(b[1] * SY))],
              fill=fill, width=max(1, round(width * SX)))


def vertical(top, bottom):
    image = Image.new("RGB", (S, S))
    draw = ImageDraw.Draw(image)
    for y in range(S):
        t = y / (S - 1)
        draw.line((0, y, S, y), fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))
    return image


def forest():
    image = vertical((202, 214, 213), (97, 121, 117))
    draw = ImageDraw.Draw(image)
    polygon(draw, [(0, 110), (65, 28), (135, 102), (207, 49), (310, 120),
                   (372, 68), (372, 176), (0, 176)], "#809A96")
    for layer, tone in enumerate(["#5F7C76", "#36594E", "#1D4035"]):
        for i in range(18):
            x, ground = i * 25 - 12 + layer * 8, 138 + layer * 24
            h = 32 + ((i * 17 + layer * 23) % 49)
            polygon(draw, [(x, ground - h), (x - 13, ground - h * .39),
                           (x - 7, ground - h * .42), (x - 19, ground),
                           (x + 19, ground), (x + 7, ground - h * .42),
                           (x + 13, ground - h * .39)], tone)
    return image


def architecture():
    image = vertical((183, 178, 165), (118, 124, 115))
    draw = ImageDraw.Draw(image)
    polygon(draw, [(0, 0), (225, 0), (284, 176), (0, 176)], "#B9B8A9")
    for i in range(7):
        line(draw, (i * 37, 0), (i * 37 + 59, 176), "#969C8D", 1.3)
    for i in range(1, 5):
        line(draw, (0, i * 38), (284, i * 38 + 15), "#9EA397")
    polygon(draw, [(228, 0), (302, 0), (372, 176), (285, 176)], "#343F39")
    line(draw, (310, 0), (370, 159), "#D9D1B9", 14)
    return ImageEnhance.Brightness(image).enhance(.58)


def short_bottom_blur(image):
    radii = [0, 3, 7, 14]
    samples = [image if r == 0 else image.filter(ImageFilter.GaussianBlur(r)) for r in radii]
    result = image.copy()
    for y in range(round(S * .88), S):
        t = max(0, min(1, (y / (S - 1) - .88) / .12))
        radius = (t * t * (3 - 2 * t)) * radii[-1]
        hi = next(i for i, value in enumerate(radii) if value >= radius)
        lo = max(0, hi - 1)
        amount = 0 if hi == lo else (radius - radii[lo]) / (radii[hi] - radii[lo])
        row = Image.blend(samples[lo].crop((0, y, S, y + 1)),
                          samples[hi].crop((0, y, S, y + 1)), amount)
        result.paste(row, (0, y))
    return result


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for name, make in [("forest", forest), ("architecture", architecture)]:
        target = OUT / f"category_cover_{name}.webp"
        short_bottom_blur(make()).save(target, "WEBP", quality=94, method=6)
        print(target)
