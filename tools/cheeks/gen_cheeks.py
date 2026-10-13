"""Генератор текстур «Анимешных щёчек» (v1.16.1). Запуск из корня репо: python3 tools/cheeks/gen_cheeks.py
Нужны numpy и Pillow. Текстура — ЛЕВАЯ щека (для смотрящего на лицо), внешний край слева;
правая рисуется зеркально (u отражается в CheeksLayer).
  blush_soft.png    — мягкое пятно, белое (цвет задаётся в игре)
  blush_stripes.png — пятно диагональными штрихами, белое (цвет задаётся в игре)
  deco_hearts.png   — белые «♥..» (не окрашиваются)
  deco_lines.png    — белые «♥///»
"""
import numpy as np
from PIL import Image
import os

W, H = 64, 32
OUT = "src/main/resources/assets/farmmacro/textures/entity/cheeks"
SS = 4  # суперсэмплинг для гладких краёв

def grid(ss=SS):
    ys, xs = np.mgrid[0:H * ss, 0:W * ss]
    return (xs + 0.5) / ss, (ys + 0.5) / ss

def down(a, ss=SS):
    return a.reshape(H, ss, W, ss).mean(axis=(1, 3))

def soft():
    x, y = grid()
    d = ((x - W / 2) / (W * 0.46)) ** 2 + ((y - H / 2) / (H * 0.44)) ** 2
    a = np.exp(-d * 2.6) - np.exp(-2.6)          # 0 на краю эллипса
    a = np.clip(a / (1 - np.exp(-2.6)), 0, 1)
    return a * 0.92

def stripes():
    x, y = grid()
    base = soft() / 0.92
    s = 0.5 + 0.5 * np.cos((x + y * 0.9) * 2 * np.pi / 7.0)   # штрихи «///»
    s = np.clip((s - 0.35) / 0.4, 0, 1)
    return np.clip(base * 1.25, 0, 1) * (0.18 + 0.82 * s) * 0.95

def heart(x, y, cx, cy, r):
    # классическое сердце: (X²+Y²−1)³ − X²Y³ ≤ 0
    X = (x - cx) / r
    Y = -(y - cy) / r + 0.15
    return ((X * X + Y * Y - 1) ** 3 - X * X * Y ** 3) <= 0

def disc(x, y, cx, cy, r):
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r

def seg(x, y, x0, y0, x1, y1, w):
    px, py = x - x0, y - y0
    dx, dy = x1 - x0, y1 - y0
    t = np.clip((px * dx + py * dy) / (dx * dx + dy * dy), 0, 1)
    return (px - t * dx) ** 2 + (py - t * dy) ** 2 <= w * w

def deco_hearts():
    x, y = grid()
    m = heart(x, y, 17, 16, 6.2) | disc(x, y, 31, 18.5, 2.0) | disc(x, y, 39, 18.5, 2.0)
    return m.astype(float)

def deco_lines():
    x, y = grid()
    m = heart(x, y, 16, 16, 6.0)
    for i in range(3):
        bx = 27 + i * 7
        m |= seg(x, y, bx, 22, bx + 5, 10, 1.25)
    return m.astype(float)

def save(name, alpha):
    a = np.clip(down(alpha) * 255, 0, 255).astype(np.uint8)
    rgba = np.zeros((H, W, 4), np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = a
    Image.fromarray(rgba, "RGBA").save(os.path.join(OUT, name), optimize=True)

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    save("blush_soft.png", soft())
    save("blush_stripes.png", stripes())
    save("deco_hearts.png", deco_hearts())
    save("deco_lines.png", deco_lines())
    print("ok")
