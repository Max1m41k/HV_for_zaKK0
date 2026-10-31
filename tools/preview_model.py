"""Быстрая 3D-превьюшка block-модели предмета (без Minecraft).

Рендерит грани элементов с тем же цветом, что отдаёт текстура по UV, чтобы
пропорции и затенение было видно до запуска игры.

    python tools/preview_model.py deagle out.png [yaw]
"""
import json
import math
import sys

import numpy as np
from PIL import Image, ImageDraw

NAME = sys.argv[1] if len(sys.argv) > 1 else "deagle"
OUT = sys.argv[2] if len(sys.argv) > 2 else "D:/visualmod/_preview.png"
YAW = math.radians(float(sys.argv[3]) if len(sys.argv) > 3 else 35)

BASE = "D:/visualmod/src/main/resources/assets/visualmod"
model = json.load(open(f"{BASE}/models/item/{NAME}.json"))
tex = Image.open(f"{BASE}/textures/item/{NAME}.png").convert("RGB")
TS = tex.size[0] / 16.0          # пикселей текстуры на один юнит UV

c, s = math.cos(YAW), math.sin(YAW)
ROT = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
LIGHT = np.array([0.3, 0.55, 0.78]); LIGHT /= np.linalg.norm(LIGHT)


def color_of(uv):
    px = int((uv[0] + uv[2]) / 2 * TS); py = int((uv[1] + uv[3]) / 2 * TS)
    return tex.getpixel((min(tex.size[0] - 1, max(0, px)), min(tex.size[1] - 1, max(0, py))))


def rotate(p, rot):
    if not rot:
        return p
    o = np.array(rot["origin"], float); a = math.radians(rot["angle"]); q = p - o
    cs, sn = math.cos(a), math.sin(a)
    if rot["axis"] == "x":
        q = np.array([q[0], q[1] * cs - q[2] * sn, q[1] * sn + q[2] * cs])
    elif rot["axis"] == "y":
        q = np.array([q[0] * cs + q[2] * sn, q[1], -q[0] * sn + q[2] * cs])
    else:
        q = np.array([q[0] * cs - q[1] * sn, q[0] * sn + q[1] * cs, q[2]])
    return q + o


def rotate_dir(n, rot):
    """Поворот нормали (без учёта центра вращения)."""
    if not rot:
        return np.array(n, float)
    a = math.radians(rot["angle"]); cs, sn = math.cos(a), math.sin(a); q = np.array(n, float)
    if rot["axis"] == "x":
        return np.array([q[0], q[1] * cs - q[2] * sn, q[1] * sn + q[2] * cs])
    if rot["axis"] == "y":
        return np.array([q[0] * cs + q[2] * sn, q[1], -q[0] * sn + q[2] * cs])
    return np.array([q[0] * cs - q[1] * sn, q[0] * sn + q[1] * cs, q[2]])


FACES = [([4, 5, 7, 6], (0, 0, 1), "south"), ([0, 2, 3, 1], (0, 0, -1), "north"),
         ([1, 3, 7, 5], (1, 0, 0), "east"), ([0, 4, 6, 2], (-1, 0, 0), "west"),
         ([2, 6, 7, 3], (0, 1, 0), "up"), ([0, 1, 5, 4], (0, -1, 0), "down")]

quads, points = [], []
for el in model["elements"]:
    a, b = np.array(el["from"], float), np.array(el["to"], float)
    rot = el.get("rotation")
    pts = [rotate(np.array([x, y, z], float), rot)
           for x in (a[0], b[0]) for y in (a[1], b[1]) for z in (a[2], b[2])]
    points += pts
    for idx, n, face in FACES:
        nn = rotate_dir(n, rot)
        if float(ROT @ nn @ LIGHT) <= 0.02:
            continue
        lit = 0.5 + 0.9 * float(ROT @ nn @ LIGHT)
        base = color_of(el["faces"][face]["uv"])
        quads.append((sum(float((ROT @ pts[k])[2]) for k in idx) / 4,
                      [pts[k] for k in idx], tuple(min(255, int(ch * lit)) for ch in base)))

center = np.mean(points, axis=0)
span = max(np.linalg.norm((ROT @ (p - center))[:2]) for p in points)
scale = 420 / (span * 2.3)

img = Image.new("RGB", (420, 420), (26, 26, 34))
dr = ImageDraw.Draw(img)


def proj(p):
    r = ROT @ (p - center)
    return (210 + r[0] * scale * 1.7, 210 - r[1] * scale * 1.7)


for _, qp, col in sorted(quads, key=lambda q: -q[0]):
    dr.polygon([proj(p) for p in qp], fill=col)
img.save(OUT)
print("saved", OUT, "quads:", len(quads))
