package com.farmmacro.visual;

import com.farmmacro.macro.MacroFrame;

import java.util.Arrays;
import java.util.List;

/**
 * Упрощённая геометрия маршрута макроса (без зависимостей от рендера — проверяется вне игры).
 *
 * Упрощение:
 *  1. прореживание по расстоянию: следующая точка не ближе {@code minDist} к предыдущей сохранённой;
 *  2. Рамер–Дуглас–Пекер с допуском {@code epsilon}: прямые отрезки схлопываются, повороты остаются;
 *  3. точки смены ЛКМ/ПКМ и начала прыжка/приседания — «якоря», их упрощение не выкидывает;
 *  4. отрезки длиннее {@code maxSeg} не создаются (нужно для отсечения по радиусу и раскраски «пройдено/впереди»).
 */
public final class RoutePath {

    public static final int F_ATTACK = 1, F_USE = 2, F_ISSUE = 4;
    /** Тип отметки: 0 — прыжок, 1 — приседание, 2 — спуск. */
    public static final byte M_JUMP = 0, M_SNEAK = 1, M_DROP = 2;

    /** Точки ломаной. frame[i] — индекс кадра, flags[i] — ЛКМ/ПКМ на отрезке (i-1 → i). */
    public final float[] x, y, z;
    public final int[] frame;
    public final byte[] flags;
    public final int size;
    /** Стрелки: позиция, направление (единичный вектор в XZ), индекс кадра. */
    public final float[] ax, ay, az, adx, adz;
    public final int[] aFrame;
    public final int arrows;
    /** Отметки прыжка (тип 0) и приседания (тип 1). */
    public final float[] mx, my, mz;
    public final byte[] mType;
    public final int[] mFrame;
    public final int marks;
    public final int sourceFrames;

    private RoutePath(float[] x, float[] y, float[] z, int[] frame, byte[] flags, int size,
                      float[] ax, float[] ay, float[] az, float[] adx, float[] adz, int[] aFrame, int arrows,
                      float[] mx, float[] my, float[] mz, byte[] mType, int[] mFrame, int marks, int sourceFrames) {
        this.x = x; this.y = y; this.z = z; this.frame = frame; this.flags = flags; this.size = size;
        this.ax = ax; this.ay = ay; this.az = az; this.adx = adx; this.adz = adz; this.aFrame = aFrame; this.arrows = arrows;
        this.mx = mx; this.my = my; this.mz = mz; this.mType = mType; this.mFrame = mFrame; this.marks = marks;
        this.sourceFrames = sourceFrames;
    }

    private static byte flagsOf(MacroFrame f) {
        return (byte) ((f.attackPressed ? F_ATTACK : 0) | (f.usePressed ? F_USE : 0));
    }

    /**
     * @param rdp false — только прореживание (быстро, для записи в реальном времени)
     */
    public static RoutePath build(List<MacroFrame> frames, double minDist, double epsilon, double maxSeg,
                                  double arrowSpacing, boolean rdp) {
        int n = frames.size();
        if (n == 0) return empty();

        // 1. прореживание + якоря
        int[] keep = new int[Math.min(n, 1 << 16)];
        boolean[] anchor = new boolean[keep.length];
        byte[] segFlags = new byte[keep.length];
        int k = 0;
        keep[k] = 0; anchor[k] = true; segFlags[k] = 0; k++;
        MacroFrame last = frames.get(0);
        byte runFlags = flagsOf(last);       // флаги, накопленные с прошлой сохранённой точки
        byte prevFlags = runFlags;
        double md2 = minDist * minDist;
        for (int i = 1; i < n; i++) {
            MacroFrame f = frames.get(i);
            byte fl = flagsOf(f);
            boolean change = fl != prevFlags;
            prevFlags = fl;
            runFlags |= fl;
            double dx = f.x - last.x, dy = f.y - last.y, dz = f.z - last.z;
            boolean far = dx * dx + dy * dy + dz * dz >= md2;
            if (far || change || i == n - 1) {
                if (k == keep.length) {
                    keep = Arrays.copyOf(keep, k * 2);
                    anchor = Arrays.copyOf(anchor, k * 2);
                    segFlags = Arrays.copyOf(segFlags, k * 2);
                }
                keep[k] = i;
                anchor[k] = change || i == n - 1;
                segFlags[k] = runFlags;
                k++;
                last = f;
                runFlags = fl;
            }
        }

        // 2. RDP между якорями (итеративно, без рекурсии)
        boolean[] alive = new boolean[k];
        if (rdp && k > 2) {
            alive[0] = true; alive[k - 1] = true;
            for (int i = 0; i < k; i++) if (anchor[i]) alive[i] = true;
            int[] stack = new int[64];
            int sp = 0;
            int a = 0;
            for (int b = 1; b < k; b++) {
                if (!alive[b]) continue;
                // отрезок [a, b] между соседними якорями
                stack[sp++] = a; stack[sp++] = b;
                while (sp > 0) {
                    int hi = stack[--sp], lo = stack[--sp];
                    if (hi - lo < 2) continue;
                    MacroFrame p = frames.get(keep[lo]), q = frames.get(keep[hi]);
                    double best = -1; int bi = -1;
                    for (int i = lo + 1; i < hi; i++) {
                        double d = distToSegment(frames.get(keep[i]), p, q);
                        if (d > best) { best = d; bi = i; }
                    }
                    if (best > epsilon) {
                        alive[bi] = true;
                        if (sp + 4 > stack.length) stack = Arrays.copyOf(stack, stack.length * 2);
                        stack[sp++] = lo; stack[sp++] = bi;
                        stack[sp++] = bi; stack[sp++] = hi;
                    }
                }
                a = b;
            }
        } else {
            Arrays.fill(alive, true);
        }

        // 3. собрать точки + разбить длинные отрезки; флаги отрезка — OR по выброшенным точкам
        int cap = k + 16;
        float[] px = new float[cap], py = new float[cap], pz = new float[cap];
        int[] pf = new int[cap]; byte[] pfl = new byte[cap];
        int m = 0;
        byte acc = 0;
        for (int i = 0; i < k; i++) {
            acc |= segFlags[i];
            if (!alive[i]) continue;
            MacroFrame f = frames.get(keep[i]);
            if (m > 0) {
                double dx = f.x - px[m - 1], dy = f.y - py[m - 1], dz = f.z - pz[m - 1];
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                int parts = (int) Math.ceil(len / maxSeg);
                int base = m - 1, f0 = pf[base], f1 = keep[i];
                for (int sIdx = 1; sIdx < parts; sIdx++) {
                    double t = (double) sIdx / parts;
                    if (m + 2 >= cap) { cap *= 2; px = Arrays.copyOf(px, cap); py = Arrays.copyOf(py, cap);
                        pz = Arrays.copyOf(pz, cap); pf = Arrays.copyOf(pf, cap); pfl = Arrays.copyOf(pfl, cap); }
                    px[m] = (float) (px[base] + dx * t);
                    py[m] = (float) (py[base] + dy * t);
                    pz[m] = (float) (pz[base] + dz * t);
                    pf[m] = (int) Math.round(f0 + (f1 - f0) * t);
                    pfl[m] = acc;
                    m++;
                }
            }
            if (m + 1 >= cap) { cap *= 2; px = Arrays.copyOf(px, cap); py = Arrays.copyOf(py, cap);
                pz = Arrays.copyOf(pz, cap); pf = Arrays.copyOf(pf, cap); pfl = Arrays.copyOf(pfl, cap); }
            px[m] = (float) f.x; py[m] = (float) f.y; pz[m] = (float) f.z;
            pf[m] = keep[i]; pfl[m] = m == 0 ? 0 : acc;
            m++;
            acc = 0;
        }

        // 4. стрелки по длине ломаной
        Arrows ar = arrows(px, py, pz, pf, m, arrowSpacing);
        float[] ax = ar.x, ay = ar.y, az = ar.z, adx = ar.dx, adz = ar.dz;
        int[] aF = ar.frame;
        int an = ar.n;

        // 5. отметки прыжка / приседания (передний фронт)
        int mcap = 32, mn = 0;
        float[] mx = new float[mcap], my = new float[mcap], mz = new float[mcap];
        byte[] mt = new byte[mcap]; int[] mF = new int[mcap];
        boolean pj = false, ps = false;
        for (int i = 0; i < n; i++) {
            MacroFrame f = frames.get(i);
            int type = -1;
            if (f.jump && !pj) type = 0;
            else if (f.sneak && !ps) type = 1;
            pj = f.jump; ps = f.sneak;
            if (type < 0) continue;
            if (mn == mcap) { mcap *= 2; mx = Arrays.copyOf(mx, mcap); my = Arrays.copyOf(my, mcap);
                mz = Arrays.copyOf(mz, mcap); mt = Arrays.copyOf(mt, mcap); mF = Arrays.copyOf(mF, mcap); }
            mx[mn] = (float) f.x; my[mn] = (float) f.y; mz[mn] = (float) f.z; mt[mn] = (byte) type; mF[mn] = i;
            mn++;
        }

        return new RoutePath(px, py, pz, pf, pfl, m, ax, ay, az, adx, adz, aF, an, mx, my, mz, mt, mF, mn, n);
    }

    private record Arrows(float[] x, float[] y, float[] z, float[] dx, float[] dz, int[] frame, int n) {}

    /** Стрелки каждые {@code arrowSpacing} по длине ломаной (первая — на половине шага). */
    private static Arrows arrows(float[] px, float[] py, float[] pz, int[] pf, int m, double arrowSpacing) {
        int acap = 64, an = 0;
        float[] ax = new float[acap], ay = new float[acap], az = new float[acap], adx = new float[acap], adz = new float[acap];
        int[] aF = new int[acap];
        if (arrowSpacing > 0) {
            double next = arrowSpacing * 0.5, run = 0;
            for (int i = 1; i < m; i++) {
                double dx = px[i] - px[i - 1], dy = py[i] - py[i - 1], dz = pz[i] - pz[i - 1];
                double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double hl = Math.sqrt(dx * dx + dz * dz);
                while (len > 1e-6 && run + len >= next) {
                    double t = (next - run) / len;
                    if (hl > 1e-4) {
                        if (an == acap) { acap *= 2; ax = Arrays.copyOf(ax, acap); ay = Arrays.copyOf(ay, acap);
                            az = Arrays.copyOf(az, acap); adx = Arrays.copyOf(adx, acap); adz = Arrays.copyOf(adz, acap);
                            aF = Arrays.copyOf(aF, acap); }
                        ax[an] = (float) (px[i - 1] + dx * t); ay[an] = (float) (py[i - 1] + dy * t);
                        az[an] = (float) (pz[i - 1] + dz * t);
                        adx[an] = (float) (dx / hl); adz[an] = (float) (dz / hl);
                        aF[an] = (int) Math.round(pf[i - 1] + (pf[i] - pf[i - 1]) * t);
                        an++;
                    }
                    next += arrowSpacing;
                }
                run += len;
            }
        }

        return new Arrows(ax, ay, az, adx, adz, aF, an);
    }

    /** Масштаб «кадров» у маршрута по точкам: точка i — кадр i·FRAME_SCALE (для дробного прогресса на отрезке). */
    public static final int FRAME_SCALE = 1000;

    /**
     * Геометрия маршрута по точкам: без упрощения, отрезки ≤ maxSeg, флаги отрезка i-1 → i — действие точки i-1,
     * отметки прыжка/приседания — в точках, где они включены. frame = индекс точки × {@link #FRAME_SCALE}.
     */
    public static RoutePath fromPoints(List<com.farmmacro.route.RoutePoint> pts, double maxSeg, double arrowSpacing) {
        return fromPoints(pts, maxSeg, arrowSpacing, null);
    }

    /**
     * @param issues issues[i] != 0 — с отрезком i → i+1 что-то не так (препятствие, обрыв, нужен спуск): флаг F_ISSUE.
     * После точки-спуска лента идёт по верхнему этажу за край (0.6 бл по направлению спуска), вниз на высоту падения
     * и дальше к следующей точке — не сквозь блоки.
     */
    public static RoutePath fromPoints(List<com.farmmacro.route.RoutePoint> pts, double maxSeg, double arrowSpacing, byte[] issues) {
        int n = pts.size();
        if (n == 0) return empty();
        int cap = n * 4 + 16, m = 0;
        float[] px = new float[cap], py = new float[cap], pz = new float[cap];
        int[] pf = new int[cap]; byte[] pfl = new byte[cap];
        for (int i = 0; i < n; i++) {
            var p = pts.get(i);
            byte fl = 0;
            if (i > 0) {
                var prev = pts.get(i - 1);
                fl = (byte) ((prev.attack() ? F_ATTACK : 0) | (prev.use() ? F_USE : 0)
                        | (issues != null && i - 1 < issues.length && issues[i - 1] != 0 ? F_ISSUE : 0));
                if (prev.drop) {
                    double[] d = com.farmmacro.route.Terrain.dropDir(pts, i - 1);
                    double depth = prev.dropDepth > 0 ? prev.dropDepth : Math.max(0, prev.y - p.y);
                    float ex = (float) (prev.x + d[0] * 0.6), ez = (float) (prev.z + d[1] * 0.6);
                    if (m + 3 >= cap) { cap *= 2; px = Arrays.copyOf(px, cap); py = Arrays.copyOf(py, cap);
                        pz = Arrays.copyOf(pz, cap); pf = Arrays.copyOf(pf, cap); pfl = Arrays.copyOf(pfl, cap); }
                    px[m] = ex; py[m] = (float) prev.y; pz[m] = ez; pf[m] = (int) Math.round((i - 1 + 0.02) * FRAME_SCALE); pfl[m] = fl; m++;
                    if (depth > 0.05) {
                        px[m] = ex; py[m] = (float) (prev.y - depth); pz[m] = ez; pf[m] = (int) Math.round((i - 1 + 0.05) * FRAME_SCALE); pfl[m] = fl; m++;
                    }
                }
                double dx = p.x - px[m - 1], dy = p.y - py[m - 1], dz = p.z - pz[m - 1];
                int parts = (int) Math.ceil(Math.sqrt(dx * dx + dy * dy + dz * dz) / maxSeg);
                int base = m - 1;
                for (int s = 1; s < parts; s++) {
                    double t = (double) s / parts;
                    if (m + 2 >= cap) { cap *= 2; px = Arrays.copyOf(px, cap); py = Arrays.copyOf(py, cap);
                        pz = Arrays.copyOf(pz, cap); pf = Arrays.copyOf(pf, cap); pfl = Arrays.copyOf(pfl, cap); }
                    px[m] = (float) (px[base] + dx * t); py[m] = (float) (py[base] + dy * t); pz[m] = (float) (pz[base] + dz * t);
                    pf[m] = (int) Math.round((i - 1 + t) * FRAME_SCALE); pfl[m] = fl;
                    m++;
                }
            }
            if (m + 1 >= cap) { cap *= 2; px = Arrays.copyOf(px, cap); py = Arrays.copyOf(py, cap);
                pz = Arrays.copyOf(pz, cap); pf = Arrays.copyOf(pf, cap); pfl = Arrays.copyOf(pfl, cap); }
            px[m] = (float) p.x; py[m] = (float) p.y; pz[m] = (float) p.z; pf[m] = i * FRAME_SCALE; pfl[m] = fl;
            m++;
        }
        Arrows ar = arrows(px, py, pz, pf, m, arrowSpacing);
        int mn = 0;
        float[] mx = new float[n * 3 + 1], my = new float[n * 3 + 1], mz = new float[n * 3 + 1];
        byte[] mt = new byte[n * 3 + 1]; int[] mF = new int[n * 3 + 1];
        for (int i = 0; i < n; i++) {
            var p = pts.get(i);
            if (p.jump)  { mx[mn] = (float) p.x; my[mn] = (float) p.y; mz[mn] = (float) p.z; mt[mn] = 0; mF[mn] = i * FRAME_SCALE; mn++; }
            if (p.sneak) { mx[mn] = (float) p.x; my[mn] = (float) p.y + 0.12f; mz[mn] = (float) p.z; mt[mn] = 1; mF[mn] = i * FRAME_SCALE; mn++; }
            if (p.drop)  { mx[mn] = (float) p.x; my[mn] = (float) p.y + 0.24f; mz[mn] = (float) p.z; mt[mn] = M_DROP; mF[mn] = i * FRAME_SCALE; mn++; }
        }
        return new RoutePath(px, py, pz, pf, pfl, m, ar.x, ar.y, ar.z, ar.dx, ar.dz, ar.frame, ar.n, mx, my, mz, mt, mF, mn, n);
    }

    private static RoutePath empty() {
        return new RoutePath(new float[0], new float[0], new float[0], new int[0], new byte[0], 0,
                new float[0], new float[0], new float[0], new float[0], new float[0], new int[0], 0,
                new float[0], new float[0], new float[0], new byte[0], new int[0], 0, 0);
    }

    private static double distToSegment(MacroFrame p, MacroFrame a, MacroFrame b) {
        double vx = b.x - a.x, vy = b.y - a.y, vz = b.z - a.z;
        double wx = p.x - a.x, wy = p.y - a.y, wz = p.z - a.z;
        double vv = vx * vx + vy * vy + vz * vz;
        double t = vv < 1e-12 ? 0 : Math.max(0, Math.min(1, (wx * vx + wy * vy + wz * vz) / vv));
        double dx = wx - vx * t, dy = wy - vy * t, dz = wz - vz * t;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
