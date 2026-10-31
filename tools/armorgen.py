# -*- coding: utf-8 -*-
"""Генератор рескинов брони (модуль CustomArmor).

Берёт ванильные текстуры equipment-слоёв и прогоняет их через градиентную
карту (gradient map): яркость пикселя сохраняется, цвет заменяется на палитру
стиля. Силуэт и детали остаются ванильными — косметика не «режет глаза».

Индексы: src/main/resources/assets/visualmod/textures/entity/equipment/
         {humanoid,humanoid_leggings}/holy_<style>_<material>.png
Логика пути повторяет EquipmentModel$Layer.getFullTextureId(): в JSON/коде
передается короткий id, а игра сама дописывает "entity/equipment/<layerType>/".
"""
import os
import glob

from PIL import Image

SRC = 'tools/armor_src/assets/minecraft/textures/entity/equipment'
DST = 'src/main/resources/assets/visualmod/textures/entity/equipment'

MATERIALS = ['netherite', 'diamond', 'iron', 'gold', 'chainmail']
LAYERS = ['humanoid', 'humanoid_leggings']

# стили: (тени, средние тона, света, блики)
STYLES = {
    'holy':   [(20, 16, 32), (74, 47, 122), (201, 166, 255), (240, 228, 255)],
    'ember':  [(26, 15, 10), (122, 58, 26), (255, 180, 94), (255, 240, 201)],
    'frost':  [(13, 22, 32), (46, 95, 122), (168, 226, 255), (234, 250, 255)],
}


def ramp(stops, n=256):
    """256-ступенчатая палитра из 4 опорных цветов (равные интервалы)."""
    out = []
    seg = n - 1
    for i in range(n):
        t = i / seg * (len(stops) - 1)
        k = min(int(t), len(stops) - 2)
        f = t - k
        a, b = stops[k], stops[k + 1]
        out.append(tuple(int(round(a[c] + (b[c] - a[c]) * f)) for c in range(3)))
    return out


def recolor(src_path, stops, out_path, tint):
    im = Image.open(src_path).convert('RGBA')
    px = im.load()
    w, h = im.size
    lut = ramp(stops)
    # собственный канал материала подмешиваем чуть-чуть: бронза остаётся медной,
    # алмаз — голубоватым, иначе все комплекты будут одного цвета
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            # лёгкий сдвиг по яркости, чтобы стиль читался даже на тёмной незере
            lum = min(1.0, max(0.0, lum * 0.85 + 0.12))
            c = lut[int(lum * 255)]
            nr = int(round(c[0] * (1 - tint) + r * tint))
            ng = int(round(c[1] * (1 - tint) + g * tint))
            nb = int(round(c[2] * (1 - tint) + b * tint))
            px[x, y] = (min(255, nr), min(255, ng), min(255, nb), a)
    im.save(out_path)
    return w, h


def main():
    os.makedirs(DST, exist_ok=True)
    for layer in LAYERS:
        os.makedirs(os.path.join(DST, layer), exist_ok=True)
    count = 0
    for style, stops in STYLES.items():
        for mat in MATERIALS:
            for layer in LAYERS:
                src = os.path.join(SRC, layer, mat + '.png')
                if not os.path.exists(src):
                    print('MISS', src)
                    continue
                out = os.path.join(DST, layer, '%s_%s.png' % (style, mat))
                # железо/золото/кольчуга почти полностью перекрашиваем,
                # незерит и алмаз чуть сохраняем родной оттенок
                tint = 0.10 if mat in ('netherite', 'diamond') else 0.28
                w, h = recolor(src, stops, out, tint)
                count += 1
                print('ok %s %s %s %dx%d' % (style, mat, layer, w, h))
    print('generated', count)


if __name__ == '__main__':
    main()
