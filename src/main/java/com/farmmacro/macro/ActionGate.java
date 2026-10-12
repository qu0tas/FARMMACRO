package com.farmmacro.macro;

/**
 * v1.15: задержка смены состояния «кнопки» (ЛКМ/ПКМ) или слота на N тиков от {@link DelayModel}. Без игры.
 * Хотят другое — через N тиков отдаём новое; передумали до срока — отмена. Счёт — по номеру тика:
 * несколько вызовов в одном тике не ускоряют ожидание.
 */
public final class ActionGate {
    /** Кнопка: зажата/отпущена. */
    public static final class Button {
        private boolean out, want;
        private int left;
        private long tick = Long.MIN_VALUE;

        /** @param delay сколько тиков ждать при смене (вызывается только при смене) */
        public boolean update(boolean wantNow, long tickNo, java.util.function.IntSupplier delayFor) {
            if (wantNow != want) {
                want = wantNow;
                left = want == out ? 0 : Math.max(0, delayFor.getAsInt());
                tick = tickNo;
            } else if (tickNo != tick) { tick = tickNo; if (left > 0) left--; }
            if (left == 0) out = want;
            return out;
        }
        public boolean out() { return out; }
        public int left() { return left; }
        /** Сразу как есть (стоп, паника, выкл). */
        public void reset(boolean state) { out = state; want = state; left = 0; tick = Long.MIN_VALUE; }
    }

    /** Слот: −1 — ещё не знаем. */
    public static final class Slot {
        private int want = -1, left;
        private long tick = Long.MIN_VALUE;

        /** @return слот, который поставить сейчас (может быть текущим — ждём) */
        public int update(int current, int wantNow, long tickNo, java.util.function.IntSupplier delayFor) {
            if (wantNow == current) { want = wantNow; left = 0; return current; }
            if (wantNow != want) { want = wantNow; left = Math.max(0, delayFor.getAsInt()); tick = tickNo; return left == 0 ? want : current; }
            if (tickNo != tick) { tick = tickNo; if (left > 0) left--; }
            return left == 0 ? want : current;
        }
        public int left() { return left; }
        public void reset() { want = -1; left = 0; tick = Long.MIN_VALUE; }
    }
}
