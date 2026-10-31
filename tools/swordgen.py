"""Генератор моделей karambit/scythe + ASCII-превью силуэта (модельное пространство 16x16).

Элемент = бокс, ось которого направлена под углом phi (против часовой, от +x),
поворот задаётся вокруг собственного центра — так же устроены старые dagger/scythe.
"""
import json, math

PAL = {
    'steel':  (1, 1, 3, 3),
    'white':  (9, 1, 11, 3),
    'gold':   (13, 1, 15, 3),
    'brass':  (1, 5, 3, 7),
    'wood':   (5, 5, 7, 7),
    'dark':   (13, 5, 15, 7),
    'violet': (1, 9, 3, 11),
    'lav':    (5, 9, 7, 11),
    'grey':   (5, 13, 7, 15),
    'brown':  (13, 9, 15, 11),
}

FACES = ('north', 'south', 'east', 'west', 'up', 'down')


def num(v):
    r = round(v, 3)
    return int(r) if abs(r - round(r)) < 1e-9 else r


def snap225(phi):
    """Ванила 1.21.4 принимает только 0, ±22.5, ±45 (ModelElement$Deserializer
    .deserializeRotationAngle кидает JsonParseException на любой другой угол).
    Ось бокса при этом можно выбрать — x или y, поэтому доступно любое направление
    кратное 22.5°."""
    k = math.floor(phi / 22.5 + 0.5) * 22.5
    return k


def elem(cx, cy, phi, length, thick, z0, z1, tex, tex_sides=None):
    """Бокс длиной length вдоль направления phi (округляется к 22.5°), центр (cx,cy)."""
    p = snap225(phi) % 180
    if p > 135:            # направление эквивалентно отрицательному по x
        p -= 180
    if -45 <= p <= 45:
        axis, ang = 'x', p
    else:
        axis, ang = 'y', (p - 90 if p > 0 else p + 90)
    if axis == 'x':
        x0, y0 = cx - length / 2, cy - thick / 2
        x1, y1 = cx + length / 2, cy + thick / 2
    else:
        x0, y0 = cx - thick / 2, cy - length / 2
        x1, y1 = cx + thick / 2, cy + length / 2
    main = list(PAL[tex])
    sides = list(PAL[tex_sides]) if tex_sides else main
    e = {
        'from': [num(x0), num(y0), num(z0)],
        'to': [num(x1), num(y1), num(z1)],
        'faces': {},
    }
    if abs(ang) > 1e-6:
        e['rotation'] = {'origin': [num(cx), num(cy), 8.0], 'axis': 'z', 'angle': num(ang)}
    for f in FACES:
        uv = sides if f in ('east', 'west') else main
        e['faces'][f] = {'uv': uv, 'texture': '#0'}
    return e


def bar(x0, y0, x1, y1, z0, z1, tex, tex_sides=None):
    main = list(PAL[tex])
    sides = list(PAL[tex_sides]) if tex_sides else main
    e = {'from': [num(x0), num(y0), num(z0)], 'to': [num(x1), num(y1), num(z1)], 'faces': {}}
    for f in FACES:
        e['faces'][f] = {'uv': sides if f in ('east', 'west') else main, 'texture': '#0'}
    return e


def arc(cx, cy, R, a_from, a_to, n, t0, t1, z0, z1, tex, edge=None, edge_inset=True):
    """Дуга вокруг (cx,cy): n сегментов, толщина t0->t1, торцы перпендикулярны радиусу."""
    out = []
    step = (a_to - a_from) / n
    for i in range(n):
        mid = a_from + step * (i + 0.5)
        t = t0 + (t1 - t0) * (i + 0.5) / n
        # бокс лежит на радиусе; центр slightly сдвигаем внутрь, если нужен острый внутренний край
        rr = R - (t / 2 if edge_inset else 0)
        px = cx + rr * math.cos(math.radians(mid))
        py = cy + rr * math.sin(math.radians(mid))
        chord = 2 * rr * math.sin(math.radians(abs(step) / 2))
        phi = mid + (90 if step > 0 else -90)
        out.append(elem(px, py, phi, chord * 1.12, t, z0, z1, tex, edge))
    return out


def ring(cx, cy, R, n, thick, z0, z1, tex):
    out = []
    for i in range(n):
        mid = 360.0 * (i + 0.5) / n
        px = cx + R * math.cos(math.radians(mid))
        py = cy + R * math.sin(math.radians(mid))
        chord = 2 * R * math.sin(math.radians(180.0 / n))
        out.append(elem(px, py, mid + 90, chord * 1.15, thick, z0, z1, tex))
    return out


def rot_pt(x, y, deg, about):
    a = math.radians(deg)
    qx, qy = x - about[0], y - about[1]
    return (about[0] + qx * math.cos(a) - qy * math.sin(a),
            about[1] + qx * math.sin(a) + qy * math.cos(a))


def rotate_all(elements, deg, about=(8, 8)):
    """Поворачивает всю сборку: центр каждого бокса по дуге, угол — плюс deg."""
    out = []
    for e in elements:
        x0, y0, z0 = e['from']; x1, y1, z1 = e['to']
        cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
        old = e.get('rotation', {}).get('angle', 0.0)
        ncx, ncy = rot_pt(cx, cy, deg, about)
        ne = dict(e)
        ne['from'] = [num(ncx - (cx - x0)), num(ncy - (cy - y0)), num(z0)]
        ne['to'] = [num(ncx + (x1 - cx)), num(ncy + (y1 - cy)), num(z1)]
        ne['rotation'] = {'origin': [num(ncx), num(ncy), 8.0], 'axis': 'z', 'angle': num(old + deg)}
        out.append(ne)
    return out


def shift_all(elements, dx, dy):
    out = []
    for e in elements:
        x0, y0, z0 = e['from']; x1, y1, z1 = e['to']
        ne = dict(e)
        ne['from'] = [num(x0 + dx), num(y0 + dy), num(z0)]
        ne['to'] = [num(x1 + dx), num(y1 + dy), num(z1)]
        if 'rotation' in ne:
            o = ne['rotation']['origin']
            ne['rotation'] = dict(ne['rotation'], origin=[num(o[0] + dx), num(o[1] + dy), 8.0])
        out.append(ne)
    return out


def bounds(elements):
    xs, ys = [], []
    for e in elements:
        x0, y0, _ = e['from']; x1, y1, _ = e['to']
        rot = e.get('rotation')
        a = math.radians(rot['angle']) if rot else 0.0
        o = rot['origin'] if rot else [(x0 + x1) / 2, (y0 + y1) / 2, 0]
        for sx in (x0, x1):
            for sy in (y0, y1):
                px, py = rot_pt(sx, sy, math.degrees(a), (o[0], o[1])) if rot else (sx, sy)
                xs.append(px); ys.append(py)
    return min(xs), min(ys), max(xs), max(ys)


def preview(elements, w=56, h=28, pts=None):
    """Заполняем пиксели, попавшие внутрь повёрнутого бокса."""
    grid = [[' ' for _ in range(w)] for _ in range(h)]
    shades = ['#', '%', '@', '&', '$', '8', 'o', 'x', '*', '+']
    for idx, e in enumerate(elements):
        x0, y0, _ = e['from']; x1, y1, _ = e['to']
        rot = e.get('rotation')
        a = math.radians(rot['angle']) if rot else 0.0
        origin = rot['origin'] if rot else [(x0 + x1) / 2, (y0 + y1) / 2, 0]
        ox, oy = origin[0], origin[1]
        ca, sa = math.cos(-a), math.sin(-a)
        ch = shades[idx % len(shades)]
        for gy in range(h):
            for gx in range(w):
                px = gx / w * 16 + 8.0 / w
                py = (1 - gy / h) * 16 - 8.0 / h
                qx, qy = px - ox, py - oy
                rx, ry = qx * ca - qy * sa, qx * sa + qy * ca
                if x0 <= rx <= x1 and y0 <= ry <= y1:
                    grid[gy][gx] = ch
    return '\n'.join(''.join(r) for r in grid)


def validate(elements, name):
    """Проверяем то, на что ванила ругается: угол поворота и границы бокса."""
    bad = []
    for i, e in enumerate(elements):
        a = e.get('rotation', {}).get('angle', 0.0)
        if abs(a) not in (0.0, 22.5, 45.0):
            bad.append(f'{name}[{i}] angle={a}')
        for v in (e['from'] + e['to']):
            if not (-16.0 <= v <= 16.0):
                bad.append(f'{name}[{i}] out of [-16,16]: {e["from"]}..{e["to"]}')
                break
    return bad


# ══════════════════════════════ karambit ══════════════════════════════

def karambit():
    el = []
    # --- кольцо на рукояти (пальцевое) ---
    el += ring(3.25, 2.55, 1.4, 8, 0.72, 7.2, 8.8, 'dark')
    el += ring(3.25, 2.55, 1.4, 8, 0.24, 7.05, 8.95, 'grey')
    # --- рукоять вдоль диагонали 45°, от кольца к гарде ---
    for i, s in enumerate([0.0, 1.35, 2.7, 4.05]):
        cx = 4.55 + s * 0.7071
        cy = 3.85 + s * 0.7071
        el.append(elem(cx, cy, 45, 1.6, 1.45 - i * 0.03, 7.25, 8.75, 'violet', 'dark'))
        el.append(elem(cx, cy, 45, 0.5, 1.55 - i * 0.03, 7.15, 8.85, 'lav', 'dark'))
    # --- больстер (гарда) ---
    cx, cy = 7.9, 7.2
    el.append(elem(cx, cy, 45, 1.0, 2.5, 7.1, 8.9, 'gold', 'brass'))
    el.append(elem(cx + 0.3, cy + 0.3, 45, 0.4, 1.9, 6.95, 9.05, 'white'))
    # --- клинок: дуга, закрученная вниз (ястребиный коготь) ---
    BC = (10.25, 4.4)      # центр дуги клинка
    R = 3.85
    el += arc(BC[0], BC[1], R, 135, 22.5, 6, 1.9, 0.18, 7.25, 8.75, 'violet', 'lav')
    # лезвие (внутренняя режущая кромка) — светлая полоса по той же дуге
    el += arc(BC[0], BC[1], R - 1.15, 130, 35, 5, 0.36, 0.16, 7.15, 8.85, 'white', 'white',
              edge_inset=False)
    # хребет клинка — тёмная линия, чтобы читался изгиб
    el += arc(BC[0], BC[1], R + 0.12, 128, 38, 5, 0.34, 0.14, 7.35, 8.65, 'dark', 'dark',
              edge_inset=False)
    # рукоять остаётся на диагонали 45° (как у старой модели) — тогда поза в руке
    # от display-трансформов не меняется, а клинок просто закручен в коготь
    return shift_all(el, -0.5, 3.2)


# ══════════════════════════════ death scythe ══════════════════════════════

def scythe():
    el = []
    # --- древко вдоль диагонали: снизу-слева вверх-вправо ---
    el.append(bar(3.1, 2.4, 4.1, 3.4, 7.3, 8.7, 'dark'))       # навершие (штык внизу)
    for i in range(6):
        s = 1.0 + i * 1.62
        cx = 3.6 + s * 0.7071
        cy = 2.9 + s * 0.7071
        el.append(elem(cx, cy, 45, 1.75, 0.95, 7.45, 8.55, 'wood', 'dark'))
    # --- витки обмотки (фиолетовые) ---
    for i in range(4):
        s = 2.2 + i * 3.2
        cx = 3.6 + s * 0.7071
        cy = 2.9 + s * 0.7071
        el.append(elem(cx, cy, 45, 0.62, 1.16, 7.25, 8.75, 'violet', 'lav'))
    # --- втуловина / голову косы ---
    hx, hy = 12.55, 11.85
    el.append(elem(hx, hy, 45, 1.9, 1.7, 7.1, 8.9, 'grey', 'dark'))
    el.append(elem(hx + 0.55, hy + 0.55, 45, 0.65, 1.3, 6.95, 9.05, 'gold'))
    el.append(elem(hx - 0.55, hy - 0.55, 45, 0.55, 1.05, 6.9, 9.1, 'violet'))
    # --- крюк/шип сзади головы ---
    el.append(elem(hx - 0.5, hy + 1.5, 118, 1.4, 0.45, 7.5, 8.5, 'dark'))
    # --- главное лезвие: большая дуга, выходящая из головы и закрученная вниз-влево ---
    BC = (11.3, 8.9)
    el += arc(BC[0], BC[1], 4.4, 62, 202, 7, 1.5, 0.16, 7.3, 8.7, 'steel', 'white')
    # светлая режущая кромка по внутренней стороне дуги
    el += arc(BC[0], BC[1], 3.15, 68, 190, 6, 0.36, 0.16, 7.2, 8.8, 'white', 'white',
              edge_inset=False)
    # тёмный хребет
    el += arc(BC[0], BC[1], 4.52, 66, 186, 6, 0.3, 0.12, 7.4, 8.6, 'dark', 'dark',
              edge_inset=False)
    # --- фиолетовое свечение у основания лезвия (поверх стали) ---
    el += arc(BC[0], BC[1], 4.4, 62, 84, 2, 1.45, 1.2, 7.25, 8.75, 'violet', 'lav')
    return el


REF = json.loads(open('tools/display_ref.json', encoding='utf-8').read())
OUT = 'src/main/resources/assets/visualmod/models/item/'


def emit(elements, display):
    L = ['{',
         '  "textures": {',
         '    "0": "visualmod:item/sword_palette",',
         '    "particle": "visualmod:item/sword_palette"',
         '  },',
         '  "gui_light": "front",',
         '  "elements": [']
    for i, e in enumerate(elements):
        L.append('    {')
        L.append(f'      "from": [{e["from"][0]}, {e["from"][1]}, {e["from"][2]}],')
        L.append(f'      "to": [{e["to"][0]}, {e["to"][1]}, {e["to"][2]}],')
        if 'rotation' in e:
            r = e['rotation']
            L.append(f'      "rotation": {{ "origin": [{r["origin"][0]}, {r["origin"][1]}, {r["origin"][2]}], '
                     f'"axis": "{r["axis"]}", "angle": {r["angle"]} }},')
        L.append('      "faces": {')
        for j, f in enumerate(FACES):
            uv = e['faces'][f]['uv']
            comma = '' if j == len(FACES) - 1 else ','
            L.append(f'        "{f}": {{ "uv": [{uv[0]}, {uv[1]}, {uv[2]}, {uv[3]}], "texture": "#0" }}{comma}')
        L.append('      }')
        L.append('    }' + (',' if i < len(elements) - 1 else ''))
    L.append('  ],')
    L.append('  "display": ' + json.dumps(display, separators=(',', ':')).replace('},{', '}, {'))
    L.append('}')
    return '\n'.join(L) + '\n'


if __name__ == '__main__':
    import sys
    write = '--write' in sys.argv
    for name, fn, key in (('karambit', karambit, 'dagger'), ('scythe', scythe, 'scythe')):
        el = fn()
        bad = validate(el, name)
        b = bounds(el)
        print(f'=== {name}: {len(el)} эл., x[{b[0]:.1f},{b[2]:.1f}] y[{b[1]:.1f},{b[3]:.1f}] '
              f'проблем: {len(bad)} ===')
        for x in bad[:8]:
            print('   !', x)
        print(preview(el))
        print()
        if write:
            txt = emit(el, REF[key])
            json.loads(txt)  # проверка валидности
            with open(OUT + key + '.json', 'w', encoding='utf-8') as f:
                f.write(txt)
            print(f'-> {OUT}{key}.json ({len(txt)} байт)')

