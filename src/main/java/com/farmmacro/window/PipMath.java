package com.farmmacro.window;

/**
 * v1.16.3 «Картинка в картинке», без Minecraft: где поставить маленькое окно в рабочей области монитора
 * (без панели задач). Углы: 0 — левый верх, 1 — правый верх, 2 — левый низ, 3 — правый низ.
 */
public final class PipMath {
    private PipMath() {}

    public static final int MIN_W = 160, MIN_H = 90;

    public record Rect(int x, int y, int w, int h) {}

    /** Место окна w×h в углу рабочей области (ax, ay, aw, ah) с отступом margin; размер не больше области. */
    public static Rect place(int ax, int ay, int aw, int ah, int w, int h, int corner, int margin) {
        aw = Math.max(MIN_W, aw);
        ah = Math.max(MIN_H, ah);
        margin = Math.max(0, Math.min(margin, Math.min(aw, ah) / 4));
        w = Math.max(MIN_W, Math.min(w, aw - 2 * margin));
        h = Math.max(MIN_H, Math.min(h, ah - 2 * margin));
        int c = Math.floorMod(corner, 4);
        int x = (c & 1) == 0 ? ax + margin : ax + aw - margin - w;
        int y = c < 2 ? ay + margin : ay + ah - margin - h;
        return new Rect(x, y, w, h);
    }

    /** Индекс монитора, в рабочей области которого центр окна; areas[i] = {x, y, w, h}. Нет такого — 0 (или -1 без мониторов). */
    public static int monitorFor(int[][] areas, int cx, int cy) {
        if (areas == null || areas.length == 0) return -1;
        for (int i = 0; i < areas.length; i++) {
            int[] a = areas[i];
            if (cx >= a[0] && cx < a[0] + a[2] && cy >= a[1] && cy < a[1] + a[3]) return i;
        }
        return 0;
    }
}
