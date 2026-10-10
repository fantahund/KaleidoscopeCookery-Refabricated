"""
4x4-Farbraster einer Item-Textur fuer die Zutaten auf Spiessen (Kaleidoscope Grilling, SkewerColorProvider#sampleGrid):
mittlere Haelfte der Textur, dunkle Pixel ignoriert, auf 8 Farben (Lab) reduziert, einzelne Ausreisser geglaettet.
Das Plugin schattiert daraus je Seite und Garstufe die Tint-Farben.
"""
import math

GRID = 4
FALLBACK = 0xB86B45


def luminance(rgb: int) -> int:
    return round(((rgb >> 16) & 0xFF) * 0.2126 + ((rgb >> 8) & 0xFF) * 0.7152 + (rgb & 0xFF) * 0.0722)


def _linear(v: float) -> float:
    return v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4


def _pivot(v: float) -> float:
    return math.cbrt(v) if v > 0.008856 else 7.787 * v + 16.0 / 116.0


def to_lab(rgb: int):
    r, g, b = (_linear(((rgb >> s) & 0xFF) / 255.0) for s in (16, 8, 0))
    x = _pivot((r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047)
    y = _pivot(r * 0.2126729 + g * 0.7151522 + b * 0.0721750)
    z = _pivot((r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883)
    return 116 * y - 16, 500 * (x - y), 200 * (y - z)


def lab_distance(a: int, b: int) -> float:
    la, lb = to_lab(a), to_lab(b)
    return sum((p - q) ** 2 for p, q in zip(la, lb))


def average(colors: list) -> int:
    n = max(1, len(colors))
    return (sum((c >> 16) & 0xFF for c in colors) // n) << 16 | (sum((c >> 8) & 0xFF for c in colors) // n) << 8 \
        | (sum(c & 0xFF for c in colors) // n)


def collect(pixels, width, height, x0, x1, y0, y1, dark_cutoff):
    colors = []
    for y in range(y0, min(y1, height)):
        for x in range(x0, min(x1, width)):
            r, g, b, a = pixels[x, y]
            if a < 48:
                continue
            rgb = r << 16 | g << 8 | b
            if luminance(rgb) >= dark_cutoff:
                colors.append(rgb)
    return colors


def quantize(colors: list, max_colors: int) -> list:
    clusters = [[1, (c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF] for c in colors]

    def avg(cl):
        return (cl[1] // cl[0]) << 16 | (cl[2] // cl[0]) << 8 | (cl[3] // cl[0])

    while len(clusters) > max_colors:
        best = None
        for i in range(len(clusters)):
            for j in range(i + 1, len(clusters)):
                d = lab_distance(avg(clusters[i]), avg(clusters[j]))
                if best is None or d < best[0]:
                    best = (d, i, j)
        _, i, j = best
        other = clusters.pop(j)
        clusters[i] = [clusters[i][k] + other[k] for k in range(4)]
    averages = [avg(c) for c in clusters]
    result = []
    for c in colors:
        nearest = averages[0]
        nd = lab_distance(c, nearest)
        for cand in averages[1:]:
            d = lab_distance(c, cand)
            if d < nd:
                nd, nearest = d, cand
        result.append(nearest)
    return result


def clean_isolated(colors: list) -> list:
    original = list(colors)
    freq = {}
    for c in original:
        freq[c] = freq.get(c, 0) + 1
    for i, c in enumerate(original):
        if freq[c] > 1:
            continue
        x, z = i % GRID, i // GRID
        neighbors = {}
        for cond, j in ((x > 0, i - 1), (x + 1 < GRID, i + 1), (z > 0, i - GRID), (z + 1 < GRID, i + GRID)):
            if cond:
                neighbors[original[j]] = neighbors.get(original[j], 0) + 1
        majority, count = c, 0
        for k, v in neighbors.items():
            if v > count:
                majority, count = k, v
        if count >= 2 and lab_distance(c, majority) > 30 * 30:
            colors[i] = majority
    return colors


def palette(image) -> list:
    """16 RGB-Werte (Zeile gridZ = 0 ist unten) aus einem RGBA-PIL-Bild."""
    width, height = image.size
    pixels = image.load()
    x0, x1 = width // 4, max(width // 4 + 1, width * 3 // 4)
    y0, y1 = height // 4, max(height // 4 + 1, height * 3 // 4)
    center = collect(pixels, width, height, x0, x1, y0, y1, 0)
    if not center:
        x0, x1, y0, y1 = 0, width, 0, height
        center = collect(pixels, width, height, x0, x1, y0, y1, 0)
    fallback = average(center) if center else FALLBACK
    median = sorted(luminance(c) for c in center)[len(center) // 2] if center else luminance(FALLBACK)
    dark_cutoff = min(36, max(5, round(median * 0.24)))
    sampled = [None] * (GRID * GRID)
    for gz in range(GRID):
        sy = GRID - 1 - gz
        fy = y0 + (y1 - y0) * sy // GRID
        ty = max(fy + 1, y0 + (y1 - y0) * (sy + 1) // GRID)
        for gx in range(GRID):
            fx = x0 + (x1 - x0) * gx // GRID
            tx = max(fx + 1, x0 + (x1 - x0) * (gx + 1) // GRID)
            block = collect(pixels, width, height, fx, tx, fy, ty, dark_cutoff) or collect(pixels, width, height, fx, tx, fy, ty, 0)
            if block:
                sampled[gz * GRID + gx] = average(block)
    for i in range(len(sampled)):
        if sampled[i] is not None:
            continue
        x, z = i % GRID, i // GRID
        nearest, nd = None, None
        for j, c in enumerate(sampled):
            if c is None:
                continue
            d = abs(x - j % GRID) + abs(z - j // GRID)
            if nd is None or d < nd:
                nearest, nd = j, d
        sampled[i] = fallback if nearest is None else sampled[nearest]
    return clean_isolated(quantize(sampled, 8))


if __name__ == "__main__":
    # Selbsttest: einfarbiges Bild -> 16 gleiche Farben
    from PIL import Image
    img = Image.new("RGBA", (16, 16), (200, 100, 50, 255))
    assert palette(img) == [200 << 16 | 100 << 8 | 50] * 16
    print("ok")
