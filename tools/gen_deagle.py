"""Генератор модели и текстуры Deagle (visualmod).

Пространство модели 0..16: ствол вдоль +Y, «верх» оружия (планка и прицел) по +X,
правая сторона с окном экстрактора по +Z. Каждой ориентации граней назначается
своя ячейка палитры, поэтому металл светлеет сверху и темнеет снизу и оружие
читается объёмным, а не набором одинаковых серых коробок.
"""
import json
from PIL import Image, ImageDraw

TEX = 128          # пикселей в текстуре
CELL = 8           # пикселей в одной палитрной ячейке (= 1 юнит UV)
S = TEX / 16.0     # пикселей на UV-юнит

MATERIALS = {
    "steel_light":   (0x8C8C9A, 0.10),
    "steel_base":    (0x5E5E6B, 0.09),
    "steel_mid":     (0x474752, 0.08),
    "steel_dark":    (0x303038, 0.07),
    "steel_darkest": (0x1E1E24, 0.05),
    "chrome":        (0xC4C4D4, 0.14),
    "black":         (0x121216, 0.03),
    "grip":          (0x373742, 0.09),
    "grip_panel":    (0x1C1C23, 0.05),
    "accent":        (0xFF7A00, 0.16),
    "accent_dark":   (0xB05000, 0.10),
}
ORDER = list(MATERIALS)
CELL_POS = {name: (i % 8, i // 8) for i, name in enumerate(ORDER)}


def shade(rgb, k):
    r, g, b = (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255
    return tuple(int(max(0, min(255, c * (1 + k)))) for c in (r, g, b))


def uv(name):
    """UV внутри палитрной ячейки с отступом 1 пиксель — цвет ровный, без кроя."""
    cx, cy = CELL_POS[name]
    return [round((cx * CELL + 1) / S, 4), round((cy * CELL + 1) / S, 4),
            round((cx * CELL + CELL - 1) / S, 4), round((cy * CELL + CELL - 1) / S, 4)]


def box(comment, a, b, profile="steel_base", top="steel_light", bottom="steel_dark",
        tip="steel_mid", tail="steel_dark", rotation=None):
    """a,b — [x,y,z] начала и конца блока.
    profile -> север/юг (±Z, плоские боковины), top -> восток (+X, верх оружия),
    bottom -> запад (-X, «брюко»), tip -> вверх (+Y, дульный срез),
    tail -> вниз (-Y, тыльная часть).
    """
    faces = {
        "north": {"uv": uv(profile), "texture": "#layer0"},
        "south": {"uv": uv(profile), "texture": "#layer0"},
        "east":  {"uv": uv(top), "texture": "#layer0"},
        "west":  {"uv": uv(bottom), "texture": "#layer0"},
        "up":    {"uv": uv(tip), "texture": "#layer0"},
        "down":  {"uv": uv(tail), "texture": "#layer0"},
    }
    el = {"comment": comment, "from": list(a), "to": list(b), "faces": faces}
    if rotation:
        origin, axis, angle = rotation
        el["rotation"] = {"origin": list(origin), "axis": axis, "angle": angle, "resize": "none"}
    return el


E = []
RAKE = ((8.0, 6.2, 8.0), "z", -22.5)   # рукоять отклоняется назад, к «брюку»

# ── рукоять ─────────────────────────────────────────────────────────────────
E.append(box("grip", (6.7, 1.5, 6.95), (9.1, 6.3, 9.05), profile="grip",
             top="grip", bottom="grip_panel", tip="steel_darkest", tail="grip_panel", rotation=RAKE))
E.append(box("grip_backstrap", (8.85, 1.7, 7.1), (9.5, 6.2, 8.9), profile="grip_panel",
             top="grip", bottom="grip_panel", tail="grip_panel", rotation=RAKE))
E.append(box("grip_swell", (6.55, 2.0, 6.9), (7.1, 5.5, 9.1), profile="grip_panel",
             top="grip", bottom="grip_panel", tail="grip_panel", rotation=RAKE))
E.append(box("mag_body", (7.1, 1.4, 7.3), (8.7, 6.0, 8.7), profile="steel_darkest",
             top="steel_darkest", bottom="steel_darkest", rotation=RAKE))
E.append(box("mag_base", (6.6, 0.7, 6.85), (9.2, 1.6, 9.15), profile="accent",
             top="accent", bottom="accent_dark", tip="accent_dark", tail="accent_dark", rotation=RAKE))

# ── рамка, скоба, крючок ────────────────────────────────────────────────────
E.append(box("frame", (6.2, 6.0, 6.9), (9.6, 8.0, 9.1), profile="steel_mid",
             top="steel_base", bottom="steel_dark", tip="steel_dark", tail="steel_darkest"))
E.append(box("guard_front", (5.75, 4.0, 7.25), (6.25, 6.2, 8.75), profile="steel_dark",
             top="steel_mid", bottom="steel_darkest", tail="steel_dark"))
E.append(box("guard_bottom", (5.75, 3.7, 7.25), (7.1, 4.2, 8.75), profile="steel_dark",
             top="steel_mid", bottom="steel_darkest", tip="steel_dark"))
E.append(box("trigger", (6.3, 4.4, 7.65), (6.85, 6.0, 8.35), profile="chrome",
             top="chrome", bottom="steel_darkest", tail="chrome"))
E.append(box("trigger_lower", (6.05, 4.15, 7.7), (6.55, 4.7, 8.3), profile="chrome",
             top="chrome", bottom="steel_darkest"))

# ── затвор ──────────────────────────────────────────────────────────────────
E.append(box("slide", (6.4, 8.0, 6.95), (9.55, 13.8, 9.05), profile="steel_base",
             top="steel_light", bottom="steel_dark", tip="steel_mid", tail="steel_dark"))
E.append(box("slide_rear", (6.3, 7.9, 6.85), (9.65, 10.1, 9.15), profile="steel_base",
             top="steel_light", bottom="steel_dark", tail="steel_darkest"))
E.append(box("slide_rib", (9.15, 8.2, 7.15), (9.8, 13.7, 8.85), profile="steel_base",
             top="steel_light", bottom="steel_base", tip="chrome", tail="steel_mid"))
E.append(box("muzzle_crown", (6.85, 13.75, 7.15), (9.25, 14.6, 8.85), profile="steel_mid",
             top="steel_light", bottom="steel_dark", tip="steel_dark", tail="steel_mid"))
E.append(box("bore", (7.4, 14.55, 7.6), (8.7, 14.78, 8.4), profile="black", top="black",
             bottom="black", tip="black", tail="black"))

# ── насечки (тыл и перед, с обеих сторон) ───────────────────────────────────
for i in range(5):
    y = 8.15 + i * 0.36
    E.append(box(f"serr_rear_L{i}", (6.6, y, 6.72), (9.4, y + 0.2, 6.95), profile="steel_darkest",
                 top="steel_dark", bottom="steel_darkest", tip="steel_darkest", tail="steel_darkest"))
    E.append(box(f"serr_rear_R{i}", (6.6, y, 9.05), (9.4, y + 0.2, 9.28), profile="steel_darkest",
                 top="steel_dark", bottom="steel_darkest", tip="steel_darkest", tail="steel_darkest"))
for i in range(3):
    y = 12.35 + i * 0.36
    E.append(box(f"serr_front_L{i}", (6.6, y, 6.72), (9.4, y + 0.2, 6.95), profile="steel_darkest",
                 top="steel_dark", bottom="steel_darkest"))
    E.append(box(f"serr_front_R{i}", (6.6, y, 9.05), (9.4, y + 0.2, 9.28), profile="steel_darkest",
                 top="steel_dark", bottom="steel_darkest"))

# ── окно экстрактора (правая сторона, +Z) ───────────────────────────────────
E.append(box("bolt_face", (7.3, 10.35, 8.8), (9.15, 12.0, 9.05), profile="steel_darkest",
             top="steel_darkest", bottom="steel_darkest"))
E.append(box("ejection_port", (7.15, 10.2, 9.02), (9.3, 12.2, 9.26), profile="black",
             top="steel_darkest", bottom="black", tip="black", tail="black"))
E.append(box("ejection_lip_top", (7.1, 12.05, 9.0), (9.35, 12.32, 9.3), profile="chrome",
             top="chrome", bottom="steel_light"))
E.append(box("ejection_lip_bot", (7.1, 10.08, 9.0), (9.35, 10.35, 9.3), profile="chrome",
             top="chrome", bottom="steel_light"))

# ── прицел ──────────────────────────────────────────────────────────────────
E.append(box("front_sight", (9.65, 12.9, 7.45), (10.35, 13.75, 8.55), profile="steel_darkest",
             top="steel_dark", bottom="steel_darkest", tip="black"))
E.append(box("front_sight_dot", (9.95, 13.4, 7.75), (10.42, 13.72, 8.25), profile="accent",
             top="accent", bottom="accent_dark", tip="accent"))
E.append(box("rear_sight_L", (9.65, 8.05, 6.85), (10.4, 9.05, 7.5), profile="steel_darkest",
             top="steel_dark", bottom="steel_darkest"))
E.append(box("rear_sight_R", (9.65, 8.05, 8.5), (10.4, 9.05, 9.15), profile="steel_darkest",
             top="steel_dark", bottom="steel_darkest"))
E.append(box("rear_sight_bar", (9.65, 8.05, 6.85), (10.15, 8.45, 9.15), profile="steel_darkest",
             top="steel_dark", bottom="steel_darkest", tail="black"))

# ── курок, защёлка, штифты ──────────────────────────────────────────────────
E.append(box("hammer", (9.2, 6.6, 7.45), (10.3, 7.9, 8.55), profile="steel_dark",
             top="steel_base", bottom="steel_darkest", tail="steel_dark"))
E.append(box("hammer_spur", (9.7, 6.0, 7.6), (10.6, 6.75, 8.4), profile="chrome",
             top="chrome", bottom="steel_darkest", tail="chrome"))
E.append(box("takedown_lever", (8.95, 8.2, 8.98), (9.7, 8.95, 9.3), profile="accent",
             top="accent", bottom="accent_dark"))
for i, y in enumerate((6.55, 7.5)):
    E.append(box(f"frame_pin{i}", (6.15, y, 6.88), (6.62, y + 0.45, 9.12), profile="steel_darkest",
                 top="steel_darkest", bottom="black"))
E.append(box("slide_stop", (6.5, 8.15, 6.7), (7.3, 8.75, 6.95), profile="chrome",
             top="chrome", bottom="steel_darkest"))

# ── текстура: палитра, каждая ячейка — лёгкий градиент по вертикали ─────────
img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
for name in ORDER:
    base, spread = MATERIALS[name]
    cx, cy = CELL_POS[name]
    x0, y0 = cx * CELL, cy * CELL
    light, dark = shade(base, spread), shade(base, -spread)
    for row in range(CELL):
        t = row / (CELL - 1)
        c = tuple(int(light[k] * (1 - t) + dark[k] * t) for k in range(3))
        d.line([(x0, y0 + row), (x0 + CELL - 1, y0 + row)], fill=c + (255,))

model = {
    "parent": "minecraft:item/handheld",
    "gui_light": "front",
    "textures": {"layer0": "visualmod:item/deagle", "particle": "visualmod:item/deagle"},
    "elements": [{"comment": e["comment"], "from": e["from"], "to": e["to"], "faces": e["faces"],
                  **({"rotation": e["rotation"]} if "rotation" in e else {}) } for e in E],
}

with open("D:/visualmod/src/main/resources/assets/visualmod/models/item/deagle.json", "w", encoding="utf-8") as f:
    json.dump(model, f, indent=1)
img.save("D:/visualmod/src/main/resources/assets/visualmod/textures/item/deagle.png")
print("elements:", len(E))
