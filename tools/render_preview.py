"""Рендер модели элементов в PNG (вид спереди, как в GUI) — для визуальной проверки."""
import math, sys
from PIL import Image, ImageDraw
import swordgen as G

RGB = {
    'steel': (0xD9, 0xDD, 0xE3), 'white': (0xF6, 0xF9, 0xFC), 'gold': (0xE3, 0xB5, 0x3A),
    'brass': (0xA6, 0x7C, 0x1A), 'wood': (0x4A, 0x2F, 0x1B), 'dark': (0x1B, 0x1B, 0x22),
    'violet': (0x6A, 0x3F, 0xD0), 'lav': (0xC4, 0xA0, 0xFF), 'grey': (0x5B, 0x64, 0x70),
    'brown': (0x6B, 0x44, 0x23),
}
UV2NAME = {tuple(v): k for k, v in G.PAL.items()}


def corners(e):
    x0, y0, _ = e['from']; x1, y1, _ = e['to']
    rot = e.get('rotation')
    a = math.radians(rot['angle']) if rot else 0.0
    o = rot['origin'] if rot else [(x0 + x1) / 2, (y0 + y1) / 2, 0]
    ca, sa = math.cos(a), math.sin(a)
    pts = []
    for sx in (x0, x1):
        for sy in (y0, y1):
            qx, qy = sx - o[0], sy - o[1]
            pts.append((o[0] + qx * ca - qy * sa, o[1] + qx * sa + qy * ca))
    return [(pts[0][0], pts[0][1]), (pts[1][0], pts[1][1]), (pts[3][0], pts[3][1]), (pts[2][0], pts[2][1])]


def render(elements, path, scale=30, flip=True):
    S = 16 * scale
    img = Image.new('RGB', (S, S), (28, 28, 34))
    d = ImageDraw.Draw(img)
    for gx in range(17):
        col = (46, 46, 56) if gx % 4 == 0 else (36, 36, 44)
        d.line([(gx * scale, 0), (gx * scale, S)], fill=col)
        d.line([(0, gx * scale), (S, gx * scale)], fill=col)
    for e in elements:
        uv = tuple(e['faces']['north']['uv'])
        name = UV2NAME.get(uv, 'grey')
        c = RGB[name]
        pts = corners(e)
        px = [(p[0] * scale, (S - p[1] * scale) if flip else p[1] * scale) for p in pts]
        d.polygon(px, fill=c, outline=(0, 0, 0))
    img.save(path)
    return path


if __name__ == '__main__':
    render(G.karambit(), 'tools/preview_karambit.png')
    render(G.scythe(), 'tools/preview_scythe.png')
    print('ok')
