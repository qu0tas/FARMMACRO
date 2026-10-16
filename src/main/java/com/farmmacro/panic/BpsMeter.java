package com.farmmacro.panic;

/**
 * v1.13 «Урожай не ломается»: блоков в секунду по «рабочим» тикам (ЛКМ зажата и идём).
 * Кольцо на {@code window} тиков: в каждом — рабочий ли тик и сколько блоков сломано.
 * Без Minecraft — проверяется в routeStress (BpsCheck).
 */
public final class BpsMeter {
    private int[] broken = new int[100];
    private boolean[] working = new boolean[100];
    private int pos, filled, workTotal, pendingBroken;

    /** Сломан блок (засчитается в текущий тик). */
    public void onBroken() { if(pendingBroken<4096)pendingBroken++; }

    /** Конец тика. windowTicks — длина окна (меняется из настроек — кольцо пересоздаётся). */
    public void tick(boolean isWorking, int windowTicks) {
        windowTicks = Math.max(20, Math.min(1200, windowTicks));
        if (broken.length != windowTicks) { broken = new int[windowTicks]; working = new boolean[windowTicks]; pos = 0; filled = 0; workTotal = 0; }
        broken[pos] = pendingBroken;
        working[pos] = isWorking;
        pendingBroken = 0;
        pos = (pos + 1) % broken.length;
        if (filled < broken.length) filled++;
        if (isWorking&&workTotal<2400) workTotal++;
    }

    private int workTicks() {
        int n = 0;
        for (int i = 0; i < filled; i++) if (working[i]) n++;
        return n;
    }

    /** BPS по рабочим тикам окна; NaN — рабочих тиков меньше 1 с. */
    public double bps() {
        int w = workTicks(), b = 0;
        if (w < 20) return Double.NaN;
        for (int i = 0; i < filled; i++) if (working[i]) b += broken[i];
        return b * 20.0 / w;
    }

    /** Можно судить: окно заполнено, рабочих тиков в нём ≥ 80 %, и с запуска наработано окно + 2 с прогрева. */
    public boolean ready() {
        return filled == broken.length && workTicks() >= broken.length * 4 / 5 && workTotal >= broken.length + 40;
    }

    public void reset() {
        java.util.Arrays.fill(broken, 0); java.util.Arrays.fill(working, false);
        pos = 0; filled = 0; workTotal = 0; pendingBroken = 0;
    }
}
